package com.xiazongji.littlestar;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persists the last uncaught exception so a non-technical user can share it. */
public final class CrashReporter {
    private static final String FILE_NAME = "last_crash.txt";

    private CrashReporter() {
    }

    public static void install(final Context context) {
        final Context appContext = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                writeReport(appContext, thread, error);
            } catch (Throwable ignored) {
                // Never hide the original crash.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    public static boolean hasReport(Context context) {
        File file = reportFile(context);
        return file.isFile() && file.length() > 0L;
    }

    public static String read(Context context) {
        File file = reportFile(context);
        if (!file.isFile()) {
            return "";
        }
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void clear(Context context) {
        File file = reportFile(context);
        if (file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static File reportFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static void writeReport(Context context, Thread thread, Throwable error) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date());
        String report = "小星星视频崩溃日志\n"
                + "时间: " + time + "\n"
                + "版本: 1.3.0 (versionCode 15)\n"
                + "设备: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "系统: Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "线程: " + (thread == null ? "unknown" : thread.getName()) + "\n\n"
                + "异常:\n" + Log.getStackTraceString(error) + "\n";

        try (FileOutputStream out = new FileOutputStream(reportFile(context))) {
            out.write(report.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // Internal storage may be unavailable; the next launch will have no report.
        }

        // Best effort: a file under Android/data is useful if the phone is connected to a Mac.
        try {
            File external = context.getExternalFilesDir("logs");
            if (external != null) {
                if (!external.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    external.mkdirs();
                }
                File copy = new File(external, "last_crash.txt");
                try (FileOutputStream out = new FileOutputStream(copy)) {
                    out.write(report.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable ignored) {
            // Optional.
        }
    }
}
