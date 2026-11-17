package com.xiazongji.littlestar;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vertical feed with a single shared player. Only the active page owns a
 * decoder, which avoids exhausting MediaCodec instances during long loops.
 */
@OptIn(markerClass = UnstableApi.class)
public final class VideoPagerAdapter extends RecyclerView.Adapter<VideoPagerAdapter.VideoHolder> {
    public static final int PAGE_COUNT = 10000;

    private final Context context;
    private final List<VideoItem> videos;
    private final PlayerView playerView;
    private final android.widget.ImageView transitionView;
    private final Runnable onVideoEnded;
    private final Set<VideoHolder> liveHolders = new HashSet<>();
    private boolean cropToFill = true;
    private boolean resumed = true;
    private boolean blocked;
    private boolean userPaused;
    private int activePosition = 0;
    private ExoPlayer player;
    private VideoHolder activeHolder;
    private String activeMediaId;
    private int retryCount;
    private boolean endNotified;

    public VideoPagerAdapter(Context context, List<VideoItem> videos, boolean cropToFill,
                             PlayerView playerView, android.widget.ImageView transitionView,
                             Runnable onVideoEnded) {
        this.context = context;
        this.videos = new ArrayList<>(videos);
        this.cropToFill = cropToFill;
        this.playerView = playerView;
        this.transitionView = transitionView;
        this.onVideoEnded = onVideoEnded;
        applyResizeMode();
    }

    @Override
    public int getItemCount() {
        return videos.isEmpty() ? 0 : PAGE_COUNT;
    }

    @NonNull
    @Override
    public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video_page, parent, false);
        VideoHolder holder = new VideoHolder(view);
        liveHolders.add(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
        if (videos.isEmpty()) {
            return;
        }
        int videoIndex = ((position % videos.size()) + videos.size()) % videos.size();
        holder.bind(position, videos.get(videoIndex));
        if (position == activePosition) {
            attach(holder);
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull VideoHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder.position == activePosition) {
            attach(holder);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull VideoHolder holder) {
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull VideoHolder holder) {
        if (holder == activeHolder) {
            detachUi();
            playerView.postDelayed(this::attachActiveFromHolders, 80L);
        }
        holder.releaseView();
        liveHolders.remove(holder);
        super.onViewRecycled(holder);
    }

    public void setActivePosition(int position) {
        int previousPosition = activePosition;
        activePosition = position;
        if (videos.isEmpty()) {
            return;
        }
        int videoIndex = ((position % videos.size()) + videos.size()) % videos.size();
        VideoItem item = videos.get(videoIndex);
        if (!item.id.equals(activeMediaId)) {
            retryCount = 0;
            VideoItem previous = findVideo(activeMediaId);
            if (!blocked && previous != null) {
                animateSwitch(previous, item, position >= previousPosition);
            } else {
                playVideo(item);
            }
        } else {
            playVideo(item);
        }
        VideoHolder target = null;
        for (VideoHolder holder : new ArrayList<>(liveHolders)) {
            if (holder.position == position) {
                target = holder;
                break;
            }
        }
        if (target != null && target.video != null) {
            attach(target);
        } else {
            detachUi();
            // The PlayerView is global, so start the selected clip even if the
            // adapter view is between bind/layout passes. The UI will attach later.
            playVideo(item);
            playerView.postDelayed(this::attachActiveFromHolders, 80L);
            playerView.postDelayed(this::attachActiveFromHolders, 450L);
        }
    }

    public void refreshActivePage() {
        attachActiveFromHolders();
    }

    private void attachActiveFromHolders() {
        for (VideoHolder holder : new ArrayList<>(liveHolders)) {
            if (holder.position == activePosition && holder.video != null) {
                attach(holder);
                return;
            }
        }
    }

    public void setCropToFill(boolean cropToFill) {
        if (this.cropToFill == cropToFill) {
            return;
        }
        this.cropToFill = cropToFill;
        applyResizeMode();
    }

    private void applyResizeMode() {
        playerView.setResizeMode(cropToFill
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    public void setPlaybackBlocked(boolean blocked) {
        this.blocked = blocked;
        if (blocked) {
            if (player != null) {
                player.pause();
            }
            finishTransition();
        } else if (resumed && !userPaused && activeHolder != null && player != null) {
            player.play();
        }
    }

    public void pause() {
        resumed = false;
        if (player != null) {
            player.pause();
        }
        finishTransition();
    }

    public void resume() {
        resumed = true;
        if (!blocked && !userPaused && activeHolder != null && player != null) {
            player.play();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public boolean isUserPaused() {
        return userPaused;
    }

    public boolean toggleUserPause() {
        userPaused = !userPaused;
        if (userPaused) {
            if (player != null) {
                player.pause();
            }
        } else if (resumed && !blocked && player != null) {
            if (player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare();
            } else if (player.getPlaybackState() == Player.STATE_ENDED) {
                player.seekTo(0L);
                endNotified = false;
            }
            player.play();
        }
        return userPaused;
    }

    public void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(Math.max(0L, positionMs));
            endNotified = false;
        }
    }

    public long getCurrentPosition() {
        return player == null ? 0L : Math.max(0L, player.getCurrentPosition());
    }

    public long getDuration() {
        if (player == null) {
            return 0L;
        }
        long duration = player.getDuration();
        return duration == C.TIME_UNSET ? 0L : Math.max(0L, duration);
    }

    public void releaseAll() {
        finishTransition();
        detachUi();
        if (player != null) {
            player.release();
            player = null;
            activeMediaId = null;
        }
        if (playerView != null) {
            playerView.setPlayer(null);
        }
    }

    private void attach(VideoHolder holder) {
        if (holder == null || holder.video == null) {
            return;
        }
        if (blocked) {
            activeHolder = holder;
            holder.loading.setVisibility(View.GONE);
            return;
        }

        if (activeHolder != holder) {
            activeHolder = holder;
        }
        holder.errorPanel.setVisibility(View.GONE);
        playVideo(holder.video);
        if (player != null) {
            if (player.getPlaybackState() == Player.STATE_BUFFERING
                    || player.getPlaybackState() == Player.STATE_IDLE) {
                holder.loading.setVisibility(View.VISIBLE);
            } else if (player.getPlaybackState() == Player.STATE_READY) {
                holder.loading.setVisibility(View.GONE);
            }
        }
    }

    private VideoItem findVideo(String id) {
        if (id == null) {
            return null;
        }
        for (VideoItem video : videos) {
            if (id.equals(video.id)) {
                return video;
            }
        }
        return null;
    }

    private void finishTransition() {
        if (transitionView != null) {
            transitionView.animate().cancel();
            transitionView.setVisibility(View.GONE);
            transitionView.setImageDrawable(null);
            transitionView.setTranslationY(0f);
        }
        if (playerView != null) {
            playerView.animate().cancel();
            playerView.setTranslationY(0f);
        }
    }

    private void animateSwitch(VideoItem previous, VideoItem next, boolean movingUp) {
        int height = playerView.getHeight();
        if (transitionView == null || height <= 0) {
            playVideo(next);
            return;
        }
        transitionView.animate().cancel();
        playerView.animate().cancel();
        android.graphics.Bitmap oldThumb = VideoStore.decodeThumb(previous.thumbPath, 720);
        if (oldThumb != null) {
            transitionView.setImageBitmap(oldThumb);
            transitionView.setVisibility(View.VISIBLE);
            transitionView.setTranslationY(0f);
        } else {
            transitionView.setVisibility(View.GONE);
        }
        playVideo(next);
        float oldEndY = movingUp ? -height : height;
        float newStartY = movingUp ? height : -height;
        playerView.setTranslationY(newStartY);
        playerView.setAlpha(1f);
        transitionView.animate()
                .translationY(oldEndY)
                .setDuration(220L)
                .start();
        playerView.animate()
                .translationY(0f)
                .setDuration(240L)
                .withEndAction(() -> {
                    transitionView.setVisibility(View.GONE);
                    transitionView.setImageDrawable(null);
                    transitionView.setTranslationY(0f);
                    playerView.setTranslationY(0f);
                })
                .start();
    }

    private void playVideo(VideoItem item) {
        if (item == null || blocked) {
            return;
        }
        ExoPlayer exo = ensurePlayer();
        if (!item.id.equals(activeMediaId)) {
            exo.stop();
            exo.clearMediaItems();
            exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(new File(item.path))));
            activeMediaId = item.id;
            endNotified = false;
            exo.prepare();
        } else if (exo.getPlaybackState() == Player.STATE_IDLE) {
            exo.prepare();
        } else if (exo.getPlaybackState() == Player.STATE_ENDED) {
            endNotified = false;
            exo.seekTo(0L);
        }
        if (resumed && !userPaused) {
            exo.play();
        } else {
            exo.pause();
        }
    }

    private ExoPlayer ensurePlayer() {
        if (player != null) {
            return player;
        }
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
                .forceDisableMediaCodecAsynchronousQueueing();
        player = new ExoPlayer.Builder(context, renderers).build();
        playerView.setPlayer(player);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                VideoHolder holder = activeHolder;
                if (holder != null) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        holder.loading.setVisibility(View.VISIBLE);
                    } else if (playbackState == Player.STATE_READY) {
                        holder.loading.setVisibility(View.GONE);
                        holder.errorPanel.setVisibility(View.GONE);
                    } else if (playbackState == Player.STATE_ENDED) {
                        holder.loading.setVisibility(View.GONE);
                    }
                }
                if (playbackState == Player.STATE_ENDED && !blocked && !endNotified) {
                    endNotified = true;
                    if (onVideoEnded != null) {
                        onVideoEnded.run();
                    }
                }
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0;
                    if (resumed && !blocked && !userPaused) {
                        player.play();
                    }
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (!blocked && retryCount < 1) {
                    retryCount++;
                    player.stop();
                    player.clearMediaItems();
                    activeMediaId = null;
                    if (activeHolder != null) {
                        activeHolder.loading.setVisibility(View.VISIBLE);
                        activeHolder.errorPanel.setVisibility(View.GONE);
                    }
                    playerView.postDelayed(() -> {
                        if (!blocked && !videos.isEmpty()) {
                            int index = ((activePosition % videos.size()) + videos.size())
                                    % videos.size();
                            playVideo(videos.get(index));
                        }
                    }, 350L);
                } else if (activeHolder != null) {
                    activeHolder.showError();
                }
            }
        });
        return player;
    }

    private void detachUi() {
        if (activeHolder != null) {
            activeHolder.loading.setVisibility(View.GONE);
            activeHolder = null;
        }
    }

    public final class VideoHolder extends RecyclerView.ViewHolder {
        private final ProgressBar loading;
        private final View errorPanel;
        private VideoItem video;
        private int position = -1;

        VideoHolder(View itemView) {
            super(itemView);
            loading = itemView.findViewById(R.id.loading);
            errorPanel = itemView.findViewById(R.id.errorPanel);
        }

        void bind(int position, VideoItem item) {
            this.position = position;
            this.video = item;
            errorPanel.setVisibility(View.GONE);
            loading.setVisibility(View.GONE);
        }

        void showError() {
            loading.setVisibility(View.GONE);
            errorPanel.setVisibility(View.VISIBLE);
            if (player != null) {
                player.pause();
            }
        }

        void releaseView() {
            loading.setVisibility(View.GONE);
            errorPanel.setVisibility(View.GONE);
            video = null;
            position = -1;
        }
    }
}
