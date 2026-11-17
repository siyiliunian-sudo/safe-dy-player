package com.xiazongji.littlestar;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/** Switches the launcher entry between the four adaptive icons. */
public final class IconManager {
    public static final int STYLE_NOTE = 0;
    public static final int STYLE_BEAR = 1;
    public static final int STYLE_STAR = 2;
    public static final int STYLE_ROCKET = 3;

    private static final String PREFS = "little_star_settings";
    private static final String KEY_ICON = "icon_style";
    private static final String[] ALIASES = {
            ".LauncherDefault",
            ".LauncherBear",
            ".LauncherStar",
            ".LauncherRocket"
    };

    private IconManager() {
    }

    public static int getStyle(Context context) {
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_ICON, STYLE_NOTE);
        return Math.max(STYLE_NOTE, Math.min(STYLE_ROCKET, value));
    }

    public static void apply(Context context, int style) {
        if (style < STYLE_NOTE || style > STYLE_ROCKET || style == getStyle(context)) {
            return;
        }
        PackageManager manager = context.getPackageManager();
        String packageName = context.getPackageName();

        // Enable the new entry first so the launcher never loses the app entirely.
        manager.setComponentEnabledSetting(
                new ComponentName(packageName, packageName + ALIASES[style]),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

        for (int i = 0; i < ALIASES.length; i++) {
            if (i != style) {
                manager.setComponentEnabledSetting(
                        new ComponentName(packageName, packageName + ALIASES[i]),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }

        prefs(context).edit().putInt(KEY_ICON, style).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
