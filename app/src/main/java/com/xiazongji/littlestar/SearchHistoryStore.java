package com.xiazongji.littlestar;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Persists the local search history; nothing ever leaves the device. */
public final class SearchHistoryStore {
    private static final String PREFS = "little_star_search";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_ITEMS = 12;

    private SearchHistoryStore() {
    }

    public static List<String> load(Context context) {
        String raw = prefs(context).getString(KEY_HISTORY, "");
        ArrayList<String> items = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return items;
        }
        for (String line : raw.split("\n")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                items.add(value);
            }
        }
        return items;
    }

    public static void add(Context context, String query) {
        String value = query == null ? "" : query.trim();
        if (value.isEmpty()) {
            return;
        }
        List<String> items = load(context);
        items.remove(value);
        items.add(0, value);
        while (items.size() > MAX_ITEMS) {
            items.remove(items.size() - 1);
        }
        prefs(context).edit().putString(KEY_HISTORY, TextUtils.join("\n", items)).apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_HISTORY).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
