package com.xiazongji.littlestar;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class MainActivity extends Activity {
    private static final int REQUEST_MANAGE = 201;
    private static final String PREFS = "little_star_settings";
    private static final String KEY_CROP = "crop_fill";
    private static final String KEY_HINT = "feed_hint_seen";
    private static final String KEY_WATCH_LIMIT = "watch_limit_minutes";
    private static final int DEFAULT_WATCH_LIMIT_MINUTES = 20;
    private static final String KEY_LAST_START = "last_start_video";

    private ViewPager2 pager;
    private VideoPagerAdapter adapter;
    private List<VideoItem> videos = new ArrayList<>();
    private View emptyState;
    private View sideRail;
    private View bottomInfo;
    private View bottomScrim;
    private View topScrim;
    private View swipeHint;
    private TextView captionTitle;
    private View restOverlay;
    private View progressPanel;
    private SeekBar progressSeek;
    private boolean seeking;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressHide = this::hideProgressPanel;
    private final Runnable progressTick = this::updateProgress;
    private String lastVideoId;
    private boolean hintShown;
    private boolean restMode;
    private long watchedMs;
    private final Handler sessionHandler = new Handler(Looper.getMainLooper());
    private final Runnable watchTick = this::onWatchTick;
    private long watchLimitMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (CrashReporter.hasReport(this)) {
            startActivity(new Intent(this, CrashReportActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        configureWindow();

        pager = findViewById(R.id.videoPager);
        emptyState = findViewById(R.id.emptyState);
        sideRail = findViewById(R.id.sideRail);
        bottomInfo = findViewById(R.id.bottomInfo);
        bottomScrim = findViewById(R.id.bottomScrim);
        topScrim = findViewById(R.id.topScrim);
        swipeHint = findViewById(R.id.swipeHint);
        captionTitle = findViewById(R.id.captionTitle);
        restOverlay = findViewById(R.id.restOverlay);
        watchLimitMs = loadWatchLimitMs();
        progressPanel = findViewById(R.id.progressPanel);
        progressSeek = findViewById(R.id.progressSeek);
        setupTapGestures();
        setupSeekBar();

        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        pager.setOffscreenPageLimit(1);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                onFeedPageSelected(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_IDLE && adapter != null) {
                    adapter.refreshActivePage();
                }
            }
        });

        View manageButton = findViewById(R.id.manageButton);
        manageButton.setOnLongClickListener(v -> {
            openManage();
            return true;
        });
        manageButton.setOnClickListener(v -> Toast.makeText(this,
                R.string.manage_tip, Toast.LENGTH_SHORT).show());

        findViewById(R.id.emptyAddButton).setOnClickListener(v -> openManage());

        applyInsets();
        rebuildFeed();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            //noinspection deprecation
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        hideSystemBars();
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void applyInsets() {
        View root = findViewById(R.id.root);
        View topBar = findViewById(R.id.topBar);
        View bottomInfoView = bottomInfo;
        View sideRailView = sideRail;
        View hintView = swipeHint;
        final int baseTop = dp(10);
        final int baseBottom = dp(26);
        final int baseSideBottom = dp(92);
        final int baseHintBottom = dp(170);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout()
                                | WindowInsets.Type.mandatorySystemGestures());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                //noinspection deprecation
                top = insets.getSystemWindowInsetTop();
                //noinspection deprecation
                bottom = insets.getSystemWindowInsetBottom();
            }
            topBar.setPadding(dp(16), baseTop + top, dp(16), 0);
            bottomInfoView.setPadding(dp(16), dp(8), dp(6), baseBottom + bottom);
            FrameLayout.LayoutParams railParams =
                    (FrameLayout.LayoutParams) sideRailView.getLayoutParams();
            railParams.bottomMargin = baseSideBottom + bottom;
            sideRailView.setLayoutParams(railParams);
            FrameLayout.LayoutParams hintParams =
                    (FrameLayout.LayoutParams) hintView.getLayoutParams();
            hintParams.bottomMargin = baseHintBottom + bottom;
            hintView.setLayoutParams(hintParams);
            ViewGroup.LayoutParams topParams = topScrim.getLayoutParams();
            topParams.height = dp(150) + top;
            topScrim.setLayoutParams(topParams);
            ViewGroup.LayoutParams bottomParams = bottomScrim.getLayoutParams();
            bottomParams.height = dp(160) + bottom;
            bottomScrim.setLayoutParams(bottomParams);
            return insets;
        });
        root.requestApplyInsets();
    }

    private boolean cropToFill() {
        return prefs().getBoolean(KEY_CROP, true);
    }

    private void rebuildFeed() {
        String keepId = lastVideoId;
        if (adapter != null) {
            adapter.releaseAll();
        }
        pager.setAdapter(null);
        videos = VideoStore.load(this);

        if (videos.isEmpty()) {
            adapter = null;
            emptyState.setVisibility(View.VISIBLE);
            pager.setVisibility(View.GONE);
            sideRail.setVisibility(View.GONE);
            bottomInfo.setVisibility(View.GONE);
            bottomScrim.setVisibility(View.GONE);
            swipeHint.setVisibility(View.GONE);
            return;
        }

        int target = -1;
        if (keepId != null) {
            for (int i = 0; i < videos.size(); i++) {
                if (videos.get(i).id.equals(keepId)) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            target = chooseRandomStart();
        }
        emptyState.setVisibility(View.GONE);
        pager.setVisibility(View.VISIBLE);
        adapter = new VideoPagerAdapter(this, videos, cropToFill(), findViewById(R.id.playerView), findViewById(R.id.transitionView), this::onVideoEnded);
        if (restMode) {
            adapter.setPlaybackBlocked(true);
        }
        pager.setAdapter(adapter);
        int startPosition = startPageFor(target);
        try {
            pager.setCurrentItem(startPosition, false);
        } catch (RuntimeException error) {
            startPosition = target;
            pager.setCurrentItem(startPosition, false);
        }
        adapter.setActivePosition(startPosition);
        lastVideoId = videos.get(target).id;
        updateChrome(target);
        if (!prefs().getBoolean(KEY_HINT, false)) {
            showSwipeHint();
        }
    }

    private int chooseRandomStart() {
        int count = videos.size();
        if (count <= 1) {
            return 0;
        }
        String last = prefs().getString(KEY_LAST_START, null);
        ArrayList<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (last == null || !videos.get(i).id.equals(last)) {
                candidates.add(i);
            }
        }
        int target = candidates.isEmpty()
                ? new Random().nextInt(count)
                : candidates.get(new Random().nextInt(candidates.size()));
        prefs().edit().putString(KEY_LAST_START, videos.get(target).id).apply();
        return target;
    }

    private void onVideoEnded() {
        if (restMode || videos.isEmpty() || adapter == null) {
            return;
        }
        final int next = pager.getCurrentItem() + 1;
        pager.post(() -> {
            if (!restMode && next < VideoPagerAdapter.PAGE_COUNT) {
                pager.setCurrentItem(next, false);
            }
        });
    }

    private void onFeedPageSelected(int position) {
        if (videos.isEmpty() || position < 0) {
            return;
        }
        int videoIndex = position % videos.size();
        lastVideoId = videos.get(videoIndex).id;
        if (adapter != null) {
            adapter.setActivePosition(position);
        }
        updateChrome(videoIndex);
        if (adapter != null && adapter.isUserPaused()) {
            showProgressPanel();
        } else {
            hideProgressPanel();
        }
        if (position < 30 || position > VideoPagerAdapter.PAGE_COUNT - 30) {
            int recenter = startPageFor(videoIndex);
            pager.post(() -> {
                if (!isFinishing() && !isDestroyed()
                        && Math.abs(pager.getCurrentItem() - recenter) > 10) {
                    pager.setCurrentItem(recenter, false);
                }
            });
        }
    }

    private void updateChrome(int videoIndex) {
        sideRail.setVisibility(View.VISIBLE);
        bottomInfo.setVisibility(View.VISIBLE);
        bottomScrim.setVisibility(View.VISIBLE);
        if (videoIndex >= 0 && videoIndex < videos.size()) {
            captionTitle.setText(videos.get(videoIndex).title());
        }
    }

    private void showSwipeHint() {
        if (hintShown) {
            return;
        }
        hintShown = true;
        prefs().edit().putBoolean(KEY_HINT, true).apply();
        swipeHint.setVisibility(View.VISIBLE);
        swipeHint.setAlpha(1f);
        swipeHint.animate()
                .alpha(0f)
                .setStartDelay(3000L)
                .setDuration(700L)
                .withEndAction(() -> swipeHint.setVisibility(View.GONE))
                .start();
    }

    private void setupTapGestures() {
        GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (progressPanel.getVisibility() == View.VISIBLE) {
                            togglePause();
                        } else {
                            showProgressPanel();
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        togglePause();
                        return true;
                    }
                });
        detector.setIsLongpressEnabled(false);
        RecyclerView recyclerView = (RecyclerView) pager.getChildAt(0);
        if (recyclerView != null) {
            recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                    detector.onTouchEvent(e);
                    return false;
                }
            });
        }
    }

    private void setupSeekBar() {
        progressSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && adapter != null) {
                    adapter.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seeking = true;
                progressHandler.removeCallbacks(progressHide);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seeking = false;
                if (adapter != null) {
                    adapter.seekTo(seekBar.getProgress());
                }
                scheduleProgressHide();
            }
        });
    }

    private void showProgressPanel() {
        if (adapter == null || videos.isEmpty()) {
            return;
        }
        progressPanel.setVisibility(View.VISIBLE);
        progressHandler.removeCallbacks(progressTick);
        progressHandler.removeCallbacks(progressHide);
        updateProgress();
        scheduleProgressHide();
    }

    private void hideProgressPanel() {
        if (adapter != null && adapter.isUserPaused()) {
            return;
        }
        progressPanel.setVisibility(View.GONE);
        progressHandler.removeCallbacks(progressTick);
        progressHandler.removeCallbacks(progressHide);
    }

    private void scheduleProgressHide() {
        progressHandler.removeCallbacks(progressHide);
        if (adapter != null && !adapter.isUserPaused()) {
            progressHandler.postDelayed(progressHide, 3500L);
        }
    }

    private void togglePause() {
        if (adapter == null) {
            return;
        }
        boolean paused = adapter.toggleUserPause();
        progressPanel.setVisibility(View.VISIBLE);
        progressHandler.removeCallbacks(progressHide);
        updateProgress();
        if (!paused) {
            scheduleProgressHide();
        }
    }

    private void updateProgress() {
        if (progressPanel.getVisibility() != View.VISIBLE || adapter == null) {
            return;
        }
        long duration = adapter.getDuration();
        long position = adapter.getCurrentPosition();
        if (!seeking) {
            if (duration > 0L) {
                int max = (int) Math.min(Integer.MAX_VALUE, duration);
                progressSeek.setMax(max);
                progressSeek.setProgress((int) Math.min(duration, position));
            } else {
                progressSeek.setMax(1);
                progressSeek.setProgress(0);
            }
        }
        progressHandler.removeCallbacks(progressTick);
        progressHandler.postDelayed(progressTick, 250L);
    }

    private void openManage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            findViewById(R.id.manageButton).performHapticFeedback(
                    android.view.HapticFeedbackConstants.LONG_PRESS);
        }
        Intent intent = new Intent(this, ManageActivity.class);
        startActivityForResult(intent, REQUEST_MANAGE);
    }

    private int startPageFor(int videoIndex) {
        int count = Math.max(1, videos.size());
        int base = (VideoPagerAdapter.PAGE_COUNT / 2 / count) * count;
        return base + (videoIndex % count);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MANAGE) {
            return;
        }
        if (adapter != null) {
            adapter.setCropToFill(cropToFill());
        }
        if (resultCode == RESULT_OK) {
            rebuildFeed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        applyWatchLimitChange();
        if (adapter != null) {
            adapter.setCropToFill(cropToFill());
            if (restMode) {
                adapter.setPlaybackBlocked(true);
            } else {
                adapter.setPlaybackBlocked(false);
                adapter.resume();
            }
        }
        startWatchTicker();
        if (adapter != null && adapter.isUserPaused()) {
            showProgressPanel();
        } else {
            hideProgressPanel();
        }
    }

    @Override
    protected void onPause() {
        sessionHandler.removeCallbacks(watchTick);
        progressHandler.removeCallbacks(progressTick);
        progressHandler.removeCallbacks(progressHide);
        progressPanel.setVisibility(View.GONE);
        if (adapter != null) {
            adapter.pause();
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    private long loadWatchLimitMs() {
        int minutes = prefs().getInt(KEY_WATCH_LIMIT, DEFAULT_WATCH_LIMIT_MINUTES);
        minutes = Math.max(0, Math.min(60, minutes));
        return minutes == 0 ? 0L : minutes * 60_000L;
    }

    private void applyWatchLimitChange() {
        watchLimitMs = loadWatchLimitMs();
        if (restMode) {
            if (watchLimitMs <= 0L || watchedMs < watchLimitMs) {
                restMode = false;
                if (restOverlay != null) {
                    restOverlay.setVisibility(View.GONE);
                }
                if (adapter != null) {
                    adapter.setPlaybackBlocked(false);
                }
            }
        } else if (watchLimitMs > 0L && watchedMs >= watchLimitMs) {
            enterRestMode();
        }
    }

    private void startWatchTicker() {
        sessionHandler.removeCallbacks(watchTick);
        if (!restMode && watchLimitMs > 0L) {
            sessionHandler.postDelayed(watchTick, 1000L);
        }
    }

    private void onWatchTick() {
        if (restMode || watchLimitMs <= 0L) {
            return;
        }
        if (adapter != null && adapter.isPlaying()) {
            watchedMs += 1000L;
        }
        if (watchedMs >= watchLimitMs) {
            enterRestMode();
            return;
        }
        sessionHandler.postDelayed(watchTick, 1000L);
    }

    private void enterRestMode() {
        if (restMode || watchLimitMs <= 0L) {
            return;
        }
        restMode = true;
        sessionHandler.removeCallbacks(watchTick);
        if (adapter != null) {
            adapter.setPlaybackBlocked(true);
        }
        if (restOverlay != null) {
            restOverlay.setVisibility(View.VISIBLE);
        }
        if (swipeHint != null) {
            swipeHint.setVisibility(View.GONE);
        }
        TextView message = findViewById(R.id.restMessage);
        if (message != null) {
            int minutes = (int) Math.max(1L, watchLimitMs / 60_000L);
            message.setText(getString(R.string.rest_message, minutes));
        }
    }

    @Override
    protected void onDestroy() {
        sessionHandler.removeCallbacks(watchTick);
        progressHandler.removeCallbacks(progressTick);
        progressHandler.removeCallbacks(progressHide);
        if (adapter != null) {
            adapter.releaseAll();
        }
        super.onDestroy();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
