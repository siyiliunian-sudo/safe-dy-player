package com.xiazongji.littlestar;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Owns the imported library. Video bytes are copied into filesDir so a later
 * deletion from the phone gallery can never break the child's play list.
 */
public final class VideoStore {
    private static final String PREFS = "little_star_library";
    private static final String KEY_VIDEOS = "videos";
    private static final String VIDEO_DIR = "videos";
    private static final String THUMB_DIR = "thumbs";
    private static final Object LOCK = new Object();

    private VideoStore() {
    }

    public static List<VideoItem> load(Context context) {
        synchronized (LOCK) {
            ArrayList<VideoItem> result = new ArrayList<>();
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = preferences.getString(KEY_VIDEOS, null);
            if (raw == null || raw.trim().isEmpty()) {
                return result;
            }
            boolean changed = false;
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        changed = true;
                        continue;
                    }
                    VideoItem item = VideoItem.fromJson(object);
                    if (item == null || !item.exists()) {
                        changed = true;
                        continue;
                    }
                    result.add(item);
                }
            } catch (JSONException ignored) {
                changed = true;
            }
            if (changed) {
                persistLocked(context, result);
            }
            return result;
        }
    }

    /**
     * Copies one picked document into private storage, builds a thumbnail and
     * appends it to the library. Throws IOException for unreadable documents.
     */
    public static VideoItem importVideo(Context context, Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("empty uri");
        }
        ContentResolver resolver = context.getContentResolver();
        String displayName = queryDisplayName(resolver, uri);
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = "小视频_" + System.currentTimeMillis();
        }
        displayName = displayName.replace('/', '_').replace('\\', '_').trim();
        if (displayName.length() > 120) {
            displayName = displayName.substring(0, 120);
        }
        String extension = extensionFor(resolver.getType(uri), displayName);

        File videoDir = new File(context.getFilesDir(), VIDEO_DIR);
        if (!videoDir.exists() && !videoDir.mkdirs()) {
            throw new IOException("cannot create video directory");
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        File target = new File(videoDir, id + extension);
        File partial = new File(videoDir, id + extension + ".part");
        long copied = 0L;
        try {
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) {
                    throw new IOException("cannot open input stream");
                }
                try (OutputStream out = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[256 * 1024];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        copied += count;
                    }
                    out.flush();
                }
            }
            if (copied <= 0L) {
                throw new IOException("empty video");
            }
            if (!partial.renameTo(target)) {
                throw new IOException("cannot finish import");
            }
        } catch (IOException | SecurityException error) {
            if (partial.exists()) {
                //noinspection ResultOfMethodCallIgnored
                partial.delete();
            }
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            throw new IOException("import failed", error);
        }

        long duration = readDuration(target);
        String thumbPath = createThumbnail(context, id, target);

        VideoItem item = new VideoItem(
                id,
                displayName,
                target.getAbsolutePath(),
                thumbPath,
                target.length(),
                duration);

        synchronized (LOCK) {
            ArrayList<VideoItem> current = new ArrayList<>(load(context));
            current.add(item);
            persistLocked(context, current);
        }
        return item;
    }

    public static void remove(Context context, VideoItem item) {
        synchronized (LOCK) {
            ArrayList<VideoItem> current = new ArrayList<>(load(context));
            for (int i = current.size() - 1; i >= 0; i--) {
                if (current.get(i).id.equals(item.id)) {
                    current.remove(i);
                }
            }
            persistLocked(context, current);
        }
        deleteQuietly(item.path);
        if (item.thumbPath != null && !item.thumbPath.isEmpty()) {
            deleteQuietly(item.thumbPath);
        }
    }

    private static void persistLocked(Context context, List<VideoItem> items) {
        JSONArray array = new JSONArray();
        for (VideoItem item : items) {
            try {
                array.put(item.toJson());
            } catch (JSONException ignored) {
                // A malformed entry should not take down the whole library.
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_VIDEOS, array.toString())
                .apply();
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to the last path segment.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String segment = uri.getLastPathSegment();
        if (segment != null) {
            int slash = segment.lastIndexOf('/');
            return slash >= 0 ? segment.substring(slash + 1) : segment;
        }
        return null;
    }

    private static String extensionFor(String mime, String displayName) {
        if ("video/quicktime".equals(mime)) {
            return ".mov";
        }
        if ("video/x-matroska".equals(mime)) {
            return ".mkv";
        }
        if ("video/webm".equals(mime)) {
            return ".webm";
        }
        if ("video/3gpp".equals(mime)) {
            return ".3gp";
        }
        int dot = displayName.lastIndexOf('.');
        if (dot > 0 && dot < displayName.length() - 1) {
            String ext = displayName.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.matches("[a-z0-9]{2,5}")) {
                return "." + ext;
            }
        }
        return ".mp4";
    }

    private static long readDuration(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (value != null) {
                return Math.max(0L, Long.parseLong(value));
            }
        } catch (RuntimeException ignored) {
            // Some containers do not expose a duration; the player can still try.
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
                // ignore
            }
        }
        return 0L;
    }

    private static String createThumbnail(Context context, String id, File video) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap frame = null;
        Bitmap square = null;
        try {
            retriever.setDataSource(video.getAbsolutePath());
            frame = retriever.getFrameAtTime(0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                return "";
            }
            int rotation = 0;
            String rotationValue = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rotationValue != null) {
                rotation = Integer.parseInt(rotationValue);
            }
            if (rotation == 90 || rotation == 270) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(frame, 0, 0,
                        frame.getWidth(), frame.getHeight(), matrix, true);
                if (rotated != frame) {
                    frame.recycle();
                    frame = rotated;
                }
            }

            int width = frame.getWidth();
            int height = frame.getHeight();
            int side = Math.min(width, height);
            int left = Math.max(0, (width - side) / 2);
            int top = Math.max(0, (height - side) / 2);
            square = Bitmap.createBitmap(frame, left, top, side, side);
            int output = Math.min(420, side);
            Bitmap scaled = Bitmap.createScaledBitmap(square, output, output, true);
            if (scaled != square) {
                square.recycle();
                square = scaled;
            }

            File thumbDir = new File(context.getFilesDir(), THUMB_DIR);
            if (!thumbDir.exists() && !thumbDir.mkdirs()) {
                return "";
            }
            File thumb = new File(thumbDir, id + ".jpg");
            try (FileOutputStream out = new FileOutputStream(thumb)) {
                if (!square.compress(Bitmap.CompressFormat.JPEG, 84, out)) {
                    return "";
                }
            }
            return thumb.getAbsolutePath();
        } catch (RuntimeException | IOException ignored) {
            return "";
        } finally {
            if (frame != null) {
                frame.recycle();
            }
            if (square != null && square != frame) {
                square.recycle();
            }
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
                // ignore
            }
        }
    }

    public static Bitmap decodeThumb(String path, int targetPx) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetPx
                    && bounds.outHeight / (sample * 2) >= targetPx) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, options);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void deleteQuietly(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        try {
            File file = new File(path);
            if (file.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        } catch (RuntimeException ignored) {
            // ignore
        }
    }
}
