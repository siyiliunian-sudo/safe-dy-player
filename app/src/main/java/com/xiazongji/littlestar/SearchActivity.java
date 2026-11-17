package com.xiazongji.littlestar;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Local search page with history, suggestions and a local hot board. */
public final class SearchActivity extends Activity {
    private static final int COLLAPSED_HISTORY = 4;
    private static final int RED = 0xFFF02B55;
    private static final int INK = 0xFF161823;
    private static final int MUTED = 0xFF8A8A93;

    private EditText input;
    private ImageView clear;
    private LinearLayout historyGrid;
    private LinearLayout historyTools;
    private LinearLayout suggestions;
    private LinearLayout hotList;
    private LinearLayout hotTabs;
    private TextView historyHint;
    private TextView historyExpand;
    private View historyChevron;
    private View historyClear;
    private View voice;
    private View body;
    private boolean expanded;
    private int hotTabIndex = 1;
    private List<VideoItem> videos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        configureWindow();

        input = findViewById(R.id.searchInput);
        clear = findViewById(R.id.searchClear);
        historyGrid = findViewById(R.id.historyGrid);
        historyTools = findViewById(R.id.historyTools);
        suggestions = findViewById(R.id.searchSuggestions);
        hotList = findViewById(R.id.hotList);
        hotTabs = findViewById(R.id.hotTabs);
        historyHint = findViewById(R.id.historyHint);
        historyExpand = findViewById(R.id.historyExpand);
        historyChevron = findViewById(R.id.historyChevron);
        historyClear = findViewById(R.id.historyClear);
        voice = findViewById(R.id.searchVoice);
        body = findViewById(R.id.searchBody);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            input.setTextCursorDrawable(R.drawable.cursor_red);
        }

        findViewById(R.id.searchBack).setOnClickListener(v -> finish());
        findViewById(R.id.searchAction).setOnClickListener(v -> performSearch());
        findViewById(R.id.searchCamera).setOnClickListener(v ->
                Toast.makeText(this, R.string.local_only_tip, Toast.LENGTH_SHORT).show());
        clear.setOnClickListener(v -> {
            input.setText("");
            rebuildAll();
        });
        voice.setOnClickListener(v -> {
            input.requestFocus();
            showKeyboard();
        });
        historyExpand.setOnClickListener(v -> {
            expanded = !expanded;
            rebuildHistory();
            rebuildSuggestions();
        });
        historyChevron.setOnClickListener(v -> {
            expanded = !expanded;
            rebuildHistory();
            rebuildSuggestions();
        });
        historyClear.setOnClickListener(v -> {
            SearchHistoryStore.clear(this);
            expanded = false;
            rebuildHistory();
            Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                rebuildHistory();
                rebuildSuggestions();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        applyInsets();
        refreshData();
        input.requestFocus();
        input.postDelayed(this::showKeyboard, 240L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        videos = VideoStore.load(this);
        rebuildHistory();
        rebuildSuggestions();
        rebuildHotTabs();
        rebuildHotList();
    }

    private void rebuildAll() {
        clear.setVisibility(input.length() == 0 ? View.GONE : View.VISIBLE);
        rebuildHistory();
        rebuildSuggestions();
    }

    private String query() {
        return input.getText().toString().trim();
    }

    private boolean matches(String text) {
        String q = query();
        return !q.isEmpty() && text.toLowerCase(Locale.CHINA).contains(q.toLowerCase(Locale.CHINA));
    }

    // ---------------------------------------------------------------- history

    private void rebuildHistory() {
        historyGrid.removeAllViews();
        List<String> history = SearchHistoryStore.load(this);
        boolean hasHistory = !history.isEmpty();
        historyHint.setVisibility(hasHistory ? View.GONE : View.VISIBLE);
        historyExpand.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        historyChevron.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        historyClear.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        historyTools.setVisibility(View.VISIBLE);
        historyExpand.setText(expanded ? R.string.history_less : R.string.history_more);
        if (!hasHistory) {
            return;
        }
        int count = expanded ? history.size() : Math.min(COLLAPSED_HISTORY, history.size());
        addTwoColumnRows(historyGrid, history.subList(0, count), true);
    }

    // ------------------------------------------------------------ suggestions

    private void rebuildSuggestions() {
        suggestions.removeAllViews();
        if (videos.isEmpty()) {
            suggestions.setVisibility(View.GONE);
            return;
        }
        String q = query().toLowerCase(Locale.CHINA);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ArrayList<String> matched = new ArrayList<>();
        ArrayList<String> rest = new ArrayList<>();
        for (VideoItem item : videos) {
            String title = item.title();
            if (q.isEmpty() || title.toLowerCase(Locale.CHINA).contains(q)) {
                matched.add(title);
            } else {
                rest.add(title);
            }
        }
        Collections.sort(matched);
        Collections.sort(rest);
        ordered.addAll(matched);
        ordered.addAll(rest);
        ArrayList<String> items = new ArrayList<>(ordered);
        if (items.isEmpty()) {
            suggestions.setVisibility(View.GONE);
            return;
        }
        suggestions.setVisibility(View.VISIBLE);
        int count = Math.min(8, items.size());
        addTwoColumnRows(suggestions, items.subList(0, count), false);
    }

    private void addTwoColumnRows(LinearLayout parent, List<String> items, boolean history) {
        for (int i = 0; i < items.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
            TextView left = buildSuggestionText(items.get(i));
            row.addView(left, new LinearLayout.LayoutParams(0, dp(46), 1f));
            if (i + 1 < items.size()) {
                TextView right = buildSuggestionText(items.get(i + 1));
                LinearLayout.LayoutParams rightParams =
                        new LinearLayout.LayoutParams(0, dp(46), 1f);
                rightParams.setMarginStart(dp(18));
                row.addView(right, rightParams);
            } else {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(46), 1f));
            }
            parent.addView(row);
        }
    }

    private TextView buildSuggestionText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15f);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTextColor(matches(text) ? RED : INK);
        view.setBackgroundResource(R.drawable.bg_search_row);
        view.setOnClickListener(v -> {
            input.setText(text);
            input.setSelection(input.length());
            performSearch();
        });
        return view;
    }

    // ---------------------------------------------------------------- hot list

    private void rebuildHotTabs() {
        hotTabs.removeAllViews();
        String[] labels = getResources().getStringArray(R.array.search_hot_tabs);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tab = new TextView(this);
            tab.setText(labels[i]);
            tab.setTextSize(14f);
            boolean active = i == hotTabIndex;
            tab.setTextColor(active ? INK : MUTED);
            tab.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            if (i > 0) {
                params.setMarginStart(dp(22));
            }
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setLayoutParams(params);
            tab.setOnClickListener(v -> {
                hotTabIndex = index;
                rebuildHotTabs();
            });
            hotTabs.addView(tab);
        }
    }

    private void rebuildHotList() {
        hotList.removeAllViews();
        if (videos.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.hot_empty);
            empty.setTextSize(13f);
            empty.setTextColor(MUTED);
            empty.setPadding(0, dp(18), 0, dp(6));
            hotList.addView(empty);
            TextView add = new TextView(this);
            add.setText(R.string.hot_add);
            add.setTextSize(13f);
            add.setTextColor(INK);
            add.setTypeface(Typeface.DEFAULT_BOLD);
            add.setPadding(0, dp(10), 0, dp(10));
            add.setOnClickListener(v -> startActivity(new Intent(this, ManageActivity.class)));
            hotList.addView(add);
            return;
        }
        ArrayList<VideoItem> sorted = new ArrayList<>(videos);
        Collections.sort(sorted, (a, b) -> Long.compare(heatValue(b), heatValue(a)));
        VideoItem pinned = sorted.get(0);
        hotList.addView(buildPinnedRow(pinned));
        int rank = 1;
        for (int i = 1; i < sorted.size() && rank <= 9; i++, rank++) {
            hotList.addView(buildHotRow(rank, sorted.get(i)));
        }
    }

    private View buildPinnedRow(VideoItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.bg_hot_pinned);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(12), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        params.bottomMargin = dp(6);
        row.setLayoutParams(params);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_arrow_up);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(17), dp(17)));

        TextView title = new TextView(this);
        title.setText(item.title());
        title.setTextSize(15f);
        title.setTextColor(INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(8), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> searchFor(item.title()));
        return row;
    }

    private View buildHotRow(int rank, VideoItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        row.setPadding(dp(2), 0, dp(2), 0);

        TextView rankView = new TextView(this);
        rankView.setText(String.valueOf(rank));
        rankView.setGravity(Gravity.CENTER);
        rankView.setTextSize(12f);
        rankView.setTypeface(Typeface.DEFAULT_BOLD);
        if (rank <= 3) {
            rankView.setTextColor(Color.WHITE);
            rankView.setBackgroundResource(rank == 1 ? R.drawable.bg_rank_1
                    : rank == 2 ? R.drawable.bg_rank_2 : R.drawable.bg_rank_3);
            LinearLayout.LayoutParams rankParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            rankParams.setMarginStart(dp(2));
            row.addView(rankView, rankParams);
            View gap = new View(this);
            row.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        } else {
            rankView.setTextColor(MUTED);
            row.addView(rankView, new LinearLayout.LayoutParams(dp(22), dp(20)));
            View gap = new View(this);
            row.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        }

        TextView title = new TextView(this);
        title.setText(item.title());
        title.setTextSize(15f);
        title.setTextColor(INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (rank <= 3) {
            TextView tag = new TextView(this);
            boolean isNew = rank == 1;
            tag.setText(isNew ? R.string.hot_new : R.string.hot_flag);
            tag.setTextColor(Color.WHITE);
            tag.setTextSize(9f);
            tag.setGravity(Gravity.CENTER);
            tag.setBackgroundResource(R.drawable.bg_tag_new);
            tag.setPadding(dp(4), 0, dp(4), 0);
            LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(16));
            tagParams.setMarginStart(dp(6));
            row.addView(tag, tagParams);
        }

        TextView heat = new TextView(this);
        heat.setText(formatHeat(heatValue(item)));
        heat.setTextSize(12f);
        heat.setTextColor(0xFF9A9AA0);
        LinearLayout.LayoutParams heatParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heatParams.setMarginStart(dp(12));
        row.addView(heat, heatParams);

        row.setOnClickListener(v -> searchFor(item.title()));
        return row;
    }

    private void searchFor(String text) {
        input.setText(text);
        input.setSelection(input.length());
        performSearch();
    }

    private long heatValue(VideoItem item) {
        long hash = Math.abs((long) item.title().hashCode());
        return 6000L + hash % 9000L;
    }

    private String formatHeat(long value) {
        return String.format(Locale.CHINA, "%.1f万", value / 10f);
    }

    // ---------------------------------------------------------------- actions

    private void performSearch() {
        String value = query();
        if (!value.isEmpty()) {
            SearchHistoryStore.add(this, value);
        }
        Intent intent = new Intent(this, VideoGridActivity.class);
        intent.putExtra(VideoGridActivity.EXTRA_QUERY, value);
        startActivity(intent);
    }

    private void showKeyboard() {
        InputMethodManager keyboard = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(getResources().getColor(R.color.search_bg, getTheme()));
        window.setNavigationBarColor(getResources().getColor(R.color.search_bg, getTheme()));
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
        View root = findViewById(R.id.searchRoot);
        View header = findViewById(R.id.searchHeader);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime());
                top = insets.getInsets(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.displayCutout()).top;
                bottom = bars.bottom;
            } else {
                //noinspection deprecation
                top = insets.getSystemWindowInsetTop();
                //noinspection deprecation
                bottom = insets.getSystemWindowInsetBottom();
            }
            header.setPadding(dp(6), dp(7) + top, dp(14), dp(7));
            FrameLayout.LayoutParams voiceParams =
                    (FrameLayout.LayoutParams) voice.getLayoutParams();
            voiceParams.bottomMargin = dp(26) + bottom;
            voice.setLayoutParams(voiceParams);
            body.setPadding(dp(16), dp(6), dp(16), dp(96) + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
