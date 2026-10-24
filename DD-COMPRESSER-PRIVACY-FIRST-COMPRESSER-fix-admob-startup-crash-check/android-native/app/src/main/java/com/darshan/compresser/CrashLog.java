package com.darshan.compresser;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes a readable crash report when the app dies, so the real reason is never a guess.
 *
 * Where to find it after a crash:
 *   Downloads / DD Compressor / DD-Compressor-crash-<date>-<time>.txt   (open with any file manager)
 *   and a private copy in the app's files folder (last-crash.txt).
 *
 * It is installed in DDApplication.attachBaseContext(), i.e. BEFORE content providers and
 * libraries start, so it also catches crashes that happen while the process is starting.
 * Only the stack trace and basic device info are written. No file contents, no personal data,
 * and nothing is ever sent anywhere.
 */
final class CrashLog {
    private CrashLog() {}

    /** Set to false to stop writing the public copy in Downloads (the private copy stays). */
    static final boolean SAVE_TO_DOWNLOADS = true;

    private static volatile boolean writing;

    static void install(final Context app) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { save(app, thread, error); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** Pure text builder (no Android calls except Build.*), easy to test. */
    static String format(Thread thread, Throwable error, long timeMillis, String processName) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("DD Compressor crash report");
        pw.println("time    : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(timeMillis)));
        pw.println("device  : " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("android : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("process : " + processName);
        pw.println("thread  : " + (thread == null ? "?" : thread.getName()));
        pw.println("debug   : " + BuildConfig.DEBUG);
        pw.println();
        error.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static void save(Context app, Thread thread, Throwable error) {
        if (writing) return; // never recurse if saving itself fails
        writing = true;
        long now = System.currentTimeMillis();
        String process = "?";
        try { process = android.app.Application.getProcessName(); } catch (Throwable ignored) { }
        byte[] bytes = format(thread, error, now, process).getBytes(StandardCharsets.UTF_8);

        try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), "last-crash.txt"))) {
            out.write(bytes);
        } catch (Throwable ignored) { }

        if (!SAVE_TO_DOWNLOADS) return;
        try {
            ContentResolver resolver = app.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME,
                    "DD-Compressor-crash-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(now)) + ".txt");
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DD Compressor");
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return;
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out != null) out.write(bytes);
            }
        } catch (Throwable ignored) { }
    }
}
