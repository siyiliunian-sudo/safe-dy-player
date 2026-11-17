package com.xiazongji.littlestar;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Search results: two-column local video grid with filter chips. */
public final class VideoGridActivity extends Activity {
    public static final String EXTRA_QUERY = "query";

    private static final int INK = 0xFF161823;
    private static final int MUTED = 0xFF8A8A93;

    private final List<VideoItem> allVideos = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();
    private final List<View> tabLines = new ArrayList<>();
    private final List<TextView> tabLabels = new ArrayList<>();
    private VideoGridAdapter adapter;
    private RecyclerView recycler;
    private View empty;
    private LinearLayout tabs;
    private LinearLayout chips;
    private int filterMode;
    private int tabIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_grid);
        configureWindow();

        TextView query = findViewById(R.id.gridQuery);
        String value = getIntent().getStringExtra(EXTRA_QUERY);
        boolean hasQuery = value != null && !value.trim().isEmpty();
        if (!hasQuery) {
            query.setText(R.string.search_hint);
            query.setTextColor(getColorCompat(R.color.search_muted));
        } else {
            query.setText(value);
            query.setTextColor(INK);
        }
        findViewById(R.id.gridClear).setVisibility(hasQuery ? View.VISIBLE : View.GONE);

        findViewById(R.id.gridBack).setOnClickListener(v -> finish());
        findViewById(R.id.gridSearchBox).setOnClickListener(v -> openSearch());
        findViewById(R.id.gridSearchAction).setOnClickListener(v -> openSearch());
        findViewById(R.id.gridClear).setOnClickListener(v -> openSearch());
        findViewById(R.id.gridFilter).setOnClickListener(v ->
                Toast.makeText(this, R.string.grid_filter_tip, Toast.LENGTH_SHORT).show());

        tabs = findViewById(R.id.gridTabs);
        chips = findViewById(R.id.gridChips);
        recycler = findViewById(R.id.videoGrid);
        empty = findViewById(R.id.gridEmpty);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new VideoGridAdapter(this, this::openVideo);
        recycler.setAdapter(adapter);

        buildTabs();
        buildChips();
        applyInsets();
        reload();
    }

    private void reload() {
        allVideos.clear();
        allVideos.addAll(VideoStore.load(this));
        applyFilter();
    }

    private void buildTabs() {
        String[] labels = getResources().getStringArray(R.array.grid_tabs);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            if (i > 0) {
                params.setMarginStart(dp(22));
            }
            tab.setLayoutParams(params);
            tab.setClickable(true);

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(15f);
            label.setSingleLine(true);
            tab.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            View line = new View(this);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(16), dp(2));
            lineParams.topMargin = dp(5);
            line.setLayoutParams(lineParams);
            line.setBackgroundColor(INK);
            tab.addView(line);

            tab.setOnClickListener(v -> selectTab(index));
            tabs.addView(tab);
            tabLabels.add(label);
            tabLines.add(line);
        }
        styleTabs();
    }

    private void selectTab(int index) {
        tabIndex = index;
        styleTabs();
        if (index != 0) {
            Toast.makeText(this, R.string.grid_local_only, Toast.LENGTH_SHORT).show();
        }
    }

    private void styleTabs() {
        for (int i = 0; i < tabLabels.size(); i++) {
            boolean active = i == tabIndex;
            TextView label = tabLabels.get(i);
            label.setTextColor(active ? INK : MUTED);
            label.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabLines.get(i).setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void buildChips() {
        String[] labels = getResources().getStringArray(R.array.grid_chips);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(12f);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(13), 0, dp(13), 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
            if (i > 0) {
                params.setMarginStart(dp(8));
            }
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                if (index <= 2) {
                    selectChip(index);
                } else {
                    Toast.makeText(this, R.string.grid_local_only, Toast.LENGTH_SHORT).show();
                }
            });
            chips.addView(chip);
            chipViews.add(chip);
        }
        styleChips();
    }

    private void selectChip(int index) {
        filterMode = index;
        styleChips();
        applyFilter();
    }

    private void styleChips() {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView chip = chipViews.get(i);
            boolean active = i == filterMode;
            chip.setBackgroundResource(active
                    ? R.drawable.bg_chip_selected : R.drawable.bg_search_chip);
            chip.setTextColor(active ? INK : Color.parseColor("#FF3B3B42"));
            chip.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void applyFilter() {
        ArrayList<VideoItem> items = new ArrayList<>();
        if (filterMode == 2) {
            for (VideoItem item : allVideos) {
                if (ReactionStore.isFavorite(this, item.id)) {
                    items.add(item);
                }
            }
        } else {
            items.addAll(allVideos);
            if (filterMode == 1) {
                Collections.reverse(items);
            }
        }
        adapter.submit(items);
        boolean isEmpty = items.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void openVideo(VideoItem item) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_START_VIDEO_ID, item.id);
        intent.putExtra(MainActivity.EXTRA_FROM_SEARCH, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openSearch() {
        startActivity(new Intent(this, SearchActivity.class));
        finish();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(getColorCompat(R.color.search_bg));
        window.setNavigationBarColor(getColorCompat(R.color.search_bg));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            //noinspection deprecation
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                window.setNavigationBarColor(Color.BLACK);
            }
            //noinspection deprecation
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void applyInsets() {
        View root = findViewById(R.id.gridRoot);
        View header = findViewById(R.id.gridHeader);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                //noinspection deprecation
                top = insets.getSystemWindowInsetTop();
                //noinspection deprecation
                bottom = insets.getSystemWindowInsetBottom();
            }
            header.setPadding(dp(6), dp(7) + top, dp(14), dp(7));
            recycler.setPadding(dp(3), dp(4), dp(3), dp(24) + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private int getColorCompat(int color) {
        return getResources().getColor(color, getTheme());
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
