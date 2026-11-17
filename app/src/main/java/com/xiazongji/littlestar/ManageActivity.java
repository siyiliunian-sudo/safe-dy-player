package com.xiazongji.littlestar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ManageActivity extends Activity {
    private static final int REQUEST_GALLERY = 301;
    private static final int REQUEST_FILES = 302;
    private static final String PREFS = "little_star_settings";
    private static final String KEY_CROP = "crop_fill";
    private static final String KEY_WATCH_LIMIT = "watch_limit_minutes";
    private static final int DEFAULT_WATCH_LIMIT = 20;
    private static final int[] ICON_RESOURCES = {
            R.mipmap.ic_launcher,
            R.mipmap.ic_launcher_bear,
            R.mipmap.ic_launcher_star,
            R.mipmap.ic_launcher_rocket
    };

    private LinearLayout listContainer;
    private TextView listCount;
    private TextView listEmpty;
    private View importOverlay;
    private TextView importText;
    private View galleryButton;
    private View filesButton;
    private Switch cropSwitch;
    private SeekBar watchLimitSeek;
    private TextView watchLimitValue;

    private final List<VideoItem> videos = new ArrayList<>();
    private final LinearLayout[] iconChoices = new LinearLayout[4];
    private final FrameLayout[] iconFrames = new FrameLayout[4];
    private final ImageView[] iconChecks = new ImageView[4];
    private final TextView[] iconLabels = new TextView[4];
    private boolean importing;
    private boolean hasChanges;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage);
        configureWindow();

        listContainer = findViewById(R.id.listContainer);
        listCount = findViewById(R.id.listCount);
        listEmpty = findViewById(R.id.listEmpty);
        importOverlay = findViewById(R.id.importOverlay);
        importText = findViewById(R.id.importText);
        galleryButton = findViewById(R.id.galleryButton);
        filesButton = findViewById(R.id.filesButton);
        cropSwitch = findViewById(R.id.cropSwitch);
        watchLimitSeek = findViewById(R.id.watchLimitSeek);
        watchLimitValue = findViewById(R.id.watchLimitValue);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.doneButton).setOnClickListener(v -> finish());
        galleryButton.setOnClickListener(v -> pickFromGallery());
        filesButton.setOnClickListener(v -> pickFromFiles());

        cropSwitch.setChecked(prefs().getBoolean(KEY_CROP, true));
        cropSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs().edit().putBoolean(KEY_CROP, isChecked).apply();
            markChanged();
        });

        int watchLimit = Math.max(0, Math.min(60,
                prefs().getInt(KEY_WATCH_LIMIT, DEFAULT_WATCH_LIMIT)));
        watchLimitSeek.setMax(60);
        watchLimitSeek.setProgress(watchLimit);
        updateWatchLimitLabel(watchLimit);
        watchLimitSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    prefs().edit().putInt(KEY_WATCH_LIMIT, progress).apply();
                    updateWatchLimitLabel(progress);
                    markChanged();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        initIconChoices();
        applyInsets();
        renderList();
        renderIconChoices();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(getColorCompat(R.color.bg));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            //noinspection deprecation
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    private void applyInsets() {
        View root = findViewById(R.id.root);
        View header = findViewById(R.id.manageHeader);
        ScrollView scroll = findViewById(R.id.contentScroll);
        final int headerStart = dp(12);
        final int headerTop = dp(10);
        final int headerEnd = dp(16);
        final int headerBottom = dp(12);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                //noinspection deprecation
                top = insets.getSystemWindowInsetTop();
                //noinspection deprecation
                bottom = insets.getSystemWindowInsetBottom();
            }
            header.setPadding(headerStart, headerTop + top, headerEnd, headerBottom);
            scroll.setPadding(dp(16), 0, dp(16), dp(28) + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void initIconChoices() {
        int[] choiceIds = {R.id.iconChoice0, R.id.iconChoice1, R.id.iconChoice2, R.id.iconChoice3};
        int[] frameIds = {R.id.iconFrame0, R.id.iconFrame1, R.id.iconFrame2, R.id.iconFrame3};
        int[] imageIds = {R.id.iconImage0, R.id.iconImage1, R.id.iconImage2, R.id.iconImage3};
        int[] checkIds = {R.id.iconCheck0, R.id.iconCheck1, R.id.iconCheck2, R.id.iconCheck3};
        int[] labelIds = {R.id.iconLabel0, R.id.iconLabel1, R.id.iconLabel2, R.id.iconLabel3};
        for (int i = 0; i < 4; i++) {
            final int style = i;
            iconChoices[i] = findViewById(choiceIds[i]);
            iconFrames[i] = findViewById(frameIds[i]);
            iconChecks[i] = findViewById(checkIds[i]);
            iconLabels[i] = findViewById(labelIds[i]);
            ImageView image = findViewById(imageIds[i]);
            image.setImageResource(ICON_RESOURCES[i]);
            iconChoices[i].setOnClickListener(v -> selectIcon(style));
        }
    }

    private void selectIcon(int style) {
        if (IconManager.getStyle(this) == style) {
            return;
        }
        try {
            IconManager.apply(this, style);
            renderIconChoices();
            markChanged();
            Toast.makeText(this,
                    style == IconManager.STYLE_NOTE
                            ? R.string.icon_default : R.string.icon_changed,
                    Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            Toast.makeText(this, "图标切换失败，请重启手机后再试", Toast.LENGTH_LONG).show();
        }
    }

    private void updateWatchLimitLabel(int minutes) {
        watchLimitValue.setText(minutes <= 0
                ? getString(R.string.watch_limit_off)
                : getString(R.string.watch_limit_minutes, minutes));
    }

    private void renderIconChoices() {
        int selected = IconManager.getStyle(this);
        for (int i = 0; i < 4; i++) {
            boolean active = i == selected;
            iconFrames[i].setBackgroundResource(active
                    ? R.drawable.bg_select_ring : R.drawable.bg_icon_unselected);
            iconChecks[i].setVisibility(active ? View.VISIBLE : View.GONE);
            iconLabels[i].setTextColor(getColorCompat(active
                    ? R.color.neon_cyan : R.color.white_70));
        }
    }

    private void renderList() {
        videos.clear();
        videos.addAll(VideoStore.load(this));
        listCount.setText(getString(R.string.list_count, videos.size()));
        listEmpty.setVisibility(videos.isEmpty() ? View.VISIBLE : View.GONE);
        listContainer.removeAllViews();
        for (int i = 0; i < videos.size(); i++) {
            listContainer.addView(createVideoRow(videos.get(i), i));
        }
    }

    private View createVideoRow(VideoItem item, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card_inner);
        row.setPadding(dp(10), dp(10), dp(5), dp(10));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);

        FrameLayout thumbBox = new FrameLayout(this);
        thumbBox.setBackgroundResource(R.drawable.bg_thumb);
        thumbBox.setClipToOutline(true);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(66), dp(66));
        row.addView(thumbBox, thumbParams);

        Bitmap thumb = VideoStore.decodeThumb(item.thumbPath, dp(70));
        if (thumb != null) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(thumb);
            thumbBox.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            ImageView placeholder = new ImageView(this);
            placeholder.setImageResource(R.drawable.ic_photo);
            placeholder.setColorFilter(getColorCompat(R.color.muted_dark));
            int padding = dp(19);
            placeholder.setPadding(padding, padding, padding, padding);
            thumbBox.addView(placeholder, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        TextView duration = new TextView(this);
        duration.setText(item.durationMs > 0
                ? VideoItem.formatDuration(item.durationMs) : "本地");
        duration.setTextColor(getColorCompat(R.color.white));
        duration.setTextSize(9f);
        duration.setGravity(android.view.Gravity.CENTER);
        duration.setBackgroundResource(R.drawable.bg_duration);
        FrameLayout.LayoutParams durationParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durationParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        durationParams.setMargins(0, 0, dp(5), dp(5));
        duration.setPadding(dp(5), dp(1), dp(5), dp(1));
        thumbBox.addView(duration, durationParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(5), 0);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(info, infoParams);

        TextView title = new TextView(this);
        title.setText(item.title());
        title.setTextColor(getColorCompat(R.color.white));
        title.setTextSize(13.5f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);

        TextView meta = new TextView(this);
        meta.setText(item.meta(this) + " · 第 " + (index + 1) + " 个");
        meta.setTextColor(getColorCompat(R.color.muted));
        meta.setTextSize(10f);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(5);
        info.addView(meta, metaParams);

        FrameLayout remove = new FrameLayout(this);
        remove.setBackgroundResource(R.drawable.bg_remove_button);
        remove.setClickable(true);
        remove.setFocusable(true);
        remove.setContentDescription(getString(R.string.remove) + item.title());
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        row.addView(remove, removeParams);

        ImageView trash = new ImageView(this);
        trash.setImageResource(R.drawable.ic_trash);
        FrameLayout.LayoutParams trashParams = new FrameLayout.LayoutParams(dp(20), dp(20));
        trashParams.gravity = android.view.Gravity.CENTER;
        remove.addView(trash, trashParams);
        remove.setOnClickListener(v -> confirmRemove(item));

        return row;
    }

    private void confirmRemove(VideoItem item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_title)
                .setMessage(getString(R.string.remove_message, item.title()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.remove_confirm, (dialog, which) -> {
                    VideoStore.remove(this, item);
                    markChanged();
                    renderList();
                    Toast.makeText(this, R.string.video_removed, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void pickFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (ActivityNotFoundException error) {
            pickFromFiles();
        }
    }

    private void pickFromFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.file_pick_title)),
                    REQUEST_FILES);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.no_app_for_pick, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_GALLERY && requestCode != REQUEST_FILES) {
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null && seen.add(uri.toString())) {
                    uris.add(uri);
                }
            }
        }
        Uri single = data.getData();
        if (single != null && seen.add(single.toString())) {
            uris.add(single);
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.import_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        startImport(uris);
    }

    private void startImport(List<Uri> uris) {
        if (importing) {
            return;
        }
        importing = true;
        importOverlay.setVisibility(View.VISIBLE);
        importText.setText(R.string.importing_one);
        galleryButton.setEnabled(false);
        filesButton.setEnabled(false);

        final Context appContext = getApplicationContext();
        final int total = uris.size();
        new Thread(() -> {
            int success = 0;
            int failed = 0;
            for (int i = 0; i < total; i++) {
                final int current = i + 1;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        importText.setText(getString(R.string.importing, current, total));
                    }
                });
                try {
                    VideoStore.importVideo(appContext, uris.get(i));
                    success++;
                } catch (Exception error) {
                    failed++;
                }
            }
            final int successCount = success;
            final int failedCount = failed;
            runOnUiThread(() -> {
                importing = false;
                importOverlay.setVisibility(View.GONE);
                galleryButton.setEnabled(true);
                filesButton.setEnabled(true);
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (successCount > 0) {
                    markChanged();
                    renderList();
                }
                if (successCount == 0) {
                    Toast.makeText(this, R.string.import_fail, Toast.LENGTH_LONG).show();
                } else if (failedCount > 0) {
                    Toast.makeText(this,
                            getString(R.string.import_partial, successCount, failedCount),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            getString(R.string.import_success, successCount),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }, "video-import").start();
    }

    @Override
    public void onBackPressed() {
        if (importing) {
            Toast.makeText(this, "正在导入视频，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    private void markChanged() {
        if (!hasChanges) {
            hasChanges = true;
            setResult(RESULT_OK);
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private int getColorCompat(int resource) {
        return getResources().getColor(resource, getTheme());
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
