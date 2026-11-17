package com.xiazongji.littlestar;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    public static final String EXTRA_START_VIDEO_ID = "start_video_id";
    public static final String EXTRA_FROM_SEARCH = "from_search";
    private static final int REQUEST_MANAGE = 201;
    private static final String PREFS = "little_star_settings";
    private static final String KEY_CROP = "crop_fill";
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
    private TextView captionTitle;
    private TextView authorName;
    private TextView musicText;
    private View restOverlay;
    private View progressPanel;
    private SeekBar progressSeek;
    private ImageView likeButton;
    private ImageView starButton;
    private View feedTopBar;
    private View searchTopBar;
    private View bottomTabBar;
    private View searchBottomBars;
    private View bottomStack;
    private View fullscreenPill;
    private View discButton;
    private View discLabel;
    private View listenPill;
    private ImageView disc;
    private boolean fromSearch;
    private String requestedVideoId;
    private boolean seeking;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressHide = this::hideProgressPanel;
    private final Runnable progressTick = this::updateProgress;
    private String lastVideoId;
    private boolean restMode;
    private long watchedMs;
    private final Handler sessionHandler = new Handler(Looper.getMainLooper());
    private final Runnable watchTick = this::onWatchTick;
    private long watchLimitMs;
    private ObjectAnimator discAnimator;
    private int bottomInset;
    private int topInset;

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
        requestedVideoId = getIntent().getStringExtra(EXTRA_START_VIDEO_ID);
        fromSearch = getIntent().getBooleanExtra(EXTRA_FROM_SEARCH, false);

        pager = findViewById(R.id.videoPager);
        emptyState = findViewById(R.id.emptyState);
        sideRail = findViewById(R.id.sideRail);
        bottomInfo = findViewById(R.id.bottomInfo);
        bottomScrim = findViewById(R.id.bottomScrim);
        topScrim = findViewById(R.id.topScrim);
        captionTitle = findViewById(R.id.captionTitle);
        authorName = findViewById(R.id.authorName);
        musicText = findViewById(R.id.musicText);
        restOverlay = findViewById(R.id.restOverlay);
        watchLimitMs = loadWatchLimitMs();
        progressPanel = findViewById(R.id.progressPanel);
        progressSeek = findViewById(R.id.progressSeek);
        likeButton = findViewById(R.id.likeButton);
        starButton = findViewById(R.id.starButton);
        feedTopBar = findViewById(R.id.feedTopBar);
        searchTopBar = findViewById(R.id.searchTopBar);
        bottomTabBar = findViewById(R.id.bottomTabBar);
        searchBottomBars = findViewById(R.id.searchBottomBars);
        bottomStack = findViewById(R.id.bottomStack);
        fullscreenPill = findViewById(R.id.fullscreenPill);
        discButton = findViewById(R.id.discButton);
        discLabel = findViewById(R.id.discLabel);
        listenPill = findViewById(R.id.listenPill);
        disc = findViewById(R.id.disc);

        authorName.setText(getString(R.string.feed_handle)
                + getString(R.string.feed_author_suffix));
        setupChrome();
        setupReactions();
        setupTapGestures();
        setupSeekBar();
        setupNavigation();

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
            window.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                // 深色视频背景：状态栏使用白色图标
                controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            //noinspection deprecation
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void setupChrome() {
        feedTopBar.setVisibility(fromSearch ? View.GONE : View.VISIBLE);
        searchTopBar.setVisibility(fromSearch ? View.VISIBLE : View.GONE);
        bottomTabBar.setVisibility(fromSearch ? View.GONE : View.VISIBLE);
        searchBottomBars.setVisibility(fromSearch ? View.VISIBLE : View.GONE);
        fullscreenPill.setVisibility(fromSearch ? View.GONE : View.VISIBLE);
        discButton.setVisibility(fromSearch ? View.GONE : View.VISIBLE);
        discLabel.setVisibility(fromSearch ? View.GONE : View.VISIBLE);
        listenPill.setVisibility(fromSearch ? View.VISIBLE : View.GONE);
        if (discAnimator != null) {
            discAnimator.cancel();
            discAnimator = null;
        }
        if (!fromSearch) {
            disc.setPivotX(dp(23));
            disc.setPivotY(dp(23));
            discAnimator = ObjectAnimator.ofFloat(disc, "rotation", 0f, 360f);
            discAnimator.setDuration(9000L);
            discAnimator.setRepeatCount(ValueAnimator.INFINITE);
            discAnimator.setInterpolator(new LinearInterpolator());
            discAnimator.start();
        }
        if (rootView() != null) {
            applyInsets();
        }
    }

    private View rootView() {
        return findViewById(R.id.root);
    }

    private void setupNavigation() {
        findViewById(R.id.searchButton).setOnClickListener(v -> openSearch());
        findViewById(R.id.searchTopBox).setOnClickListener(v -> openSearch());
        findViewById(R.id.searchTopAction).setOnClickListener(v -> openSearch());
        findViewById(R.id.searchTopBack).setOnClickListener(v -> openSearch());

        View menu = findViewById(R.id.menuButton);
        menu.setOnClickListener(v -> Toast.makeText(this, R.string.local_only_tip,
                Toast.LENGTH_SHORT).show());
        menu.setOnLongClickListener(v -> {
            openManage();
            return true;
        });

        View navMe = findViewById(R.id.navMe);
        navMe.setOnClickListener(v -> Toast.makeText(this, R.string.manage_hold_tip,
                Toast.LENGTH_SHORT).show());
        navMe.setOnLongClickListener(v -> {
            openManage();
            return true;
        });
        View.OnClickListener localOnly = v -> Toast.makeText(this, R.string.local_only_tip,
                Toast.LENGTH_SHORT).show();
        findViewById(R.id.navHome).setOnClickListener(localOnly);
        findViewById(R.id.navFriends).setOnClickListener(localOnly);
        findViewById(R.id.navMessages).setOnClickListener(localOnly);
        findViewById(R.id.navCreate).setOnClickListener(localOnly);
        findViewById(R.id.discButton).setOnClickListener(localOnly);
        findViewById(R.id.listenPill).setOnClickListener(localOnly);
        findViewById(R.id.commentBar).setOnClickListener(localOnly);
        findViewById(R.id.collectionBar).setOnClickListener(localOnly);
        findViewById(R.id.collectionNext).setOnClickListener(v -> {
            if (adapter != null && pager != null) {
                pager.setCurrentItem(pager.getCurrentItem() + 1, true);
            }
        });

        fullscreenPill.setOnClickListener(v -> {
            boolean crop = !cropToFill();
            prefs().edit().putBoolean(KEY_CROP, crop).apply();
            if (adapter != null) {
                adapter.setCropToFill(crop);
            }
            Toast.makeText(this, crop ? R.string.toast_fullscreen_on : R.string.toast_fullscreen_off,
                    Toast.LENGTH_SHORT).show();
        });

        setupTopTabs();
    }

    private void setupTopTabs() {
        int[] tabs = {R.id.tabHot, R.id.tabLive, R.id.tabCity, R.id.tabGroup,
                R.id.tabFollow, R.id.tabShop, R.id.tabRecommend};
        int[] lines = {R.id.tabHotLine, R.id.tabLiveLine, R.id.tabCityLine,
                R.id.tabGroupLine, R.id.tabFollowLine, R.id.tabShopLine,
                R.id.tabRecommendLine};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            findViewById(tabs[i]).setOnClickListener(v -> selectTopTab(tabs, lines, index));
        }
    }

    private void selectTopTab(int[] tabs, int[] lines, int selected) {
        for (int i = 0; i < tabs.length; i++) {
            LinearLayout tab = findViewById(tabs[i]);
            TextView label = (TextView) tab.getChildAt(0);
            boolean active = i == selected;
            label.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            label.setTextColor(active ? 0xFFFFFFFF : 0xD9FFFFFF);
            findViewById(lines[i]).setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        }
        if (selected != 6) {
            Toast.makeText(this, R.string.local_only_tip, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSearch() {
        startActivity(new Intent(this, SearchActivity.class));
    }

    private void applyInsets() {
        View root = rootView();
        if (root == null) {
            return;
        }
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
            topInset = top;
            bottomInset = bottom;
            feedTopBar.setPadding(dp(12), dp(9) + top, dp(14), dp(4));
            searchTopBar.setPadding(dp(6), dp(7) + top, dp(14), dp(7));

            ViewGroup.LayoutParams tabParams = bottomTabBar.getLayoutParams();
            tabParams.height = dp(54) + bottom;
            bottomTabBar.setLayoutParams(tabParams);
            bottomTabBar.setPadding(0, dp(5), 0, bottom);
            searchBottomBars.setPadding(0, 0, 0, bottom);

            int barsHeight = fromSearch ? dp(84) : dp(54);
            bottomStack.setPadding(0, 0, 0, bottom + barsHeight + dp(4));

            FrameLayout.LayoutParams railParams =
                    (FrameLayout.LayoutParams) sideRail.getLayoutParams();
            railParams.bottomMargin = bottom + (fromSearch ? dp(158) : dp(172));
            sideRail.setLayoutParams(railParams);

            ViewGroup.LayoutParams topParams = topScrim.getLayoutParams();
            topParams.height = dp(180) + top;
            topScrim.setLayoutParams(topParams);
            ViewGroup.LayoutParams bottomParams = bottomScrim.getLayoutParams();
            bottomParams.height = dp(260) + bottom;
            bottomScrim.setLayoutParams(bottomParams);

            FrameLayout.LayoutParams progressParams =
                    (FrameLayout.LayoutParams) progressPanel.getLayoutParams();
            progressParams.bottomMargin = bottom + barsHeight + dp(2);
            progressPanel.setLayoutParams(progressParams);
            return insets;
        });
        root.requestApplyInsets();
    }

    private boolean cropToFill() {
        return prefs().getBoolean(KEY_CROP, true);
    }

    private void rebuildFeed() {
        String keepId = requestedVideoId != null ? requestedVideoId : lastVideoId;
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
            bottomStack.setVisibility(View.GONE);
            bottomScrim.setVisibility(View.GONE);
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
        sideRail.setVisibility(View.VISIBLE);
        bottomStack.setVisibility(View.VISIBLE);
        bottomScrim.setVisibility(View.VISIBLE);
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
        requestedVideoId = null;
        updateChrome(target);
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
            VideoItem item = videos.get(videoIndex);
            captionTitle.setText(item.title());
            musicText.setText(getString(R.string.feed_music));
            updateReactionUi(item.id);
        }
    }

    private void setupReactions() {
        likeButton.setOnClickListener(v -> {
            String id = currentVideoId();
            if (id == null) return;
            ReactionStore.toggleLike(this, id);
            updateReactionUi(id);
            likeButton.animate().scaleX(1.18f).scaleY(1.18f).setDuration(90L)
                    .withEndAction(() -> likeButton.animate().scaleX(1f).scaleY(1f)
                            .setDuration(90L).start()).start();
        });
        starButton.setOnClickListener(v -> {
            String id = currentVideoId();
            if (id == null) return;
            ReactionStore.toggleFavorite(this, id);
            updateReactionUi(id);
            starButton.animate().scaleX(1.18f).scaleY(1.18f).setDuration(90L)
                    .withEndAction(() -> starButton.animate().scaleX(1f).scaleY(1f)
                            .setDuration(90L).start()).start();
        });
    }

    private String currentVideoId() {
        if (videos.isEmpty()) {
            return null;
        }
        int index = pager.getCurrentItem() % videos.size();
        return videos.get(index).id;
    }

    private void updateReactionUi(String videoId) {
        boolean liked = ReactionStore.isLiked(this, videoId);
        boolean favorite = ReactionStore.isFavorite(this, videoId);
        likeButton.setColorFilter(liked ? 0xFFFE2C55 : 0xFFFFFFFF);
        starButton.setColorFilter(favorite ? 0xFFFFC43D : 0xFFFFFFFF);
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
            findViewById(R.id.menuButton).performHapticFeedback(
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        boolean nowFromSearch = intent.getBooleanExtra(EXTRA_FROM_SEARCH, false);
        if (nowFromSearch != fromSearch) {
            fromSearch = nowFromSearch;
            setupChrome();
        }
        String id = intent.getStringExtra(EXTRA_START_VIDEO_ID);
        if (id != null) {
            requestedVideoId = id;
            rebuildFeed();
        }
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
        if (discAnimator != null && discAnimator.isPaused()) {
            discAnimator.resume();
        }
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
        if (discAnimator != null) {
            discAnimator.pause();
        }
        if (adapter != null) {
            adapter.pause();
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (fromSearch) {
            startActivity(new Intent(this, SearchActivity.class));
            return;
        }
        super.onBackPressed();
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
        if (discAnimator != null) {
            discAnimator.cancel();
            discAnimator = null;
        }
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
