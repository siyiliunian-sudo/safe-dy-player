package com.xiazongji.littlestar;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/** One curated video, always stored inside the app's private files directory. */
public final class VideoItem {
    public final String id;
    public final String name;
    public final String path;
    public final String thumbPath;
    public final long sizeBytes;
    public final long durationMs;

    public VideoItem(String id, String name, String path, String thumbPath,
                     long sizeBytes, long durationMs) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.thumbPath = thumbPath;
        this.sizeBytes = sizeBytes;
        this.durationMs = durationMs;
    }

    public String title() {
        String value = name == null ? "" : name;
        int dot = value.lastIndexOf('.');
        if (dot > 0) {
            value = value.substring(0, dot);
        }
        value = value.replace('_', ' ').trim();
        if (value.isEmpty()) {
            value = "小视频";
        }
        if (value.length() > 44) {
            value = value.substring(0, 44) + "…";
        }
        return value;
    }

    public String meta(Context context) {
        String size;
        if (sizeBytes >= 1024L * 1024L) {
            size = String.format(Locale.CHINA, "%.1f MB", sizeBytes / (1024f * 1024f));
        } else if (sizeBytes > 0) {
            size = String.format(Locale.CHINA, "%d KB", Math.max(1, sizeBytes / 1024));
        } else {
            size = "本地视频";
        }
        if (durationMs > 0) {
            return size + " · " + formatDuration(durationMs);
        }
        return size;
    }

    public static String formatDuration(long ms) {
        long total = Math.max(0, ms / 1000L);
        long seconds = total % 60L;
        long minutes = (total / 60L) % 60L;
        long hours = total / 3600L;
        if (hours > 0) {
            return String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds);
    }

    public boolean exists() {
        return path != null && new File(path).isFile();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("path", path);
        object.put("thumb", thumbPath == null ? "" : thumbPath);
        object.put("size", sizeBytes);
        object.put("duration", durationMs);
        return object;
    }

    public static VideoItem fromJson(JSONObject object) {
        String id = object.optString("id", "");
        String path = object.optString("path", "");
        if (id.isEmpty() || path.isEmpty()) {
            return null;
        }
        return new VideoItem(
                id,
                object.optString("name", "小视频"),
                path,
                object.optString("thumb", ""),
                object.optLong("size", 0L),
                object.optLong("duration", 0L));
    }
}
