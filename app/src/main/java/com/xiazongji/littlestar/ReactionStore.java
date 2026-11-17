package com.xiazongji.littlestar;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Local-only visual reactions: no network, no social graph. */
public final class ReactionStore {
    private static final String PREFS = "little_star_reactions";
    private static final String KEY_LIKED = "liked";
    private static final String KEY_FAVORITES = "favorites";

    private ReactionStore() {
    }

    public static boolean isLiked(Context context, String videoId) {
        return contains(context, KEY_LIKED, videoId);
    }

    public static boolean toggleLike(Context context, String videoId) {
        return toggle(context, KEY_LIKED, videoId);
    }

    public static boolean isFavorite(Context context, String videoId) {
        return contains(context, KEY_FAVORITES, videoId);
    }

    public static boolean toggleFavorite(Context context, String videoId) {
        return toggle(context, KEY_FAVORITES, videoId);
    }

    public static Set<String> getFavorites(Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    private static boolean contains(Context context, String key, String videoId) {
        return videoId != null && prefs(context).getStringSet(key, new HashSet<>()).contains(videoId);
    }

    private static boolean toggle(Context context, String key, String videoId) {
        if (videoId == null) {
            return false;
        }
        SharedPreferences preferences = prefs(context);
        Set<String> values = new HashSet<>(preferences.getStringSet(key, new HashSet<>()));
        boolean selected;
        if (values.contains(videoId)) {
            values.remove(videoId);
            selected = false;
        } else {
            values.add(videoId);
            selected = true;
        }
        preferences.edit().putStringSet(key, values).apply();
        return selected;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
