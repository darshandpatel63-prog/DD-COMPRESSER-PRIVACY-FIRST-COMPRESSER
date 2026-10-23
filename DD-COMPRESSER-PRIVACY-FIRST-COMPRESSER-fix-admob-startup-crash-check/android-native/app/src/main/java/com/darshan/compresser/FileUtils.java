package com.darshan.compresser;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.*;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

public final class FileUtils {
    private FileUtils() {}

    public static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {}
        String s = uri.getLastPathSegment();
        return s == null ? "selected-file" : s;
    }

    public static long size(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static String mime(Context context, Uri uri, String name) {
        String m = context.getContentResolver().getType(uri);
        if (m != null && !m.isEmpty()) return m.toLowerCase(Locale.US);
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".bmp")) return "image/bmp";
        if (n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".mov") || n.endsWith(".mkv") || n.endsWith(".webm")) return "video/*";
        if (n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".flac")) return "audio/*";
        if (n.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    public static File newTemp(Context context, String prefix, String suffix) throws IOException {
        File dir = new File(context.getCacheDir(), "shared");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not prepare temporary storage.");
        return File.createTempFile(prefix, suffix, dir);
    }

    public static void copy(Context context, Uri source, File dest) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(source);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            if (in == null) throw new IOException("Could not open the selected file.");
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
    }

    public static File gzip(Context context, Uri source, String name) throws IOException {
        String safe = name.replaceAll("[\\/:*?\"<>|\\x00-\\x1F]", "_");
        File out = newTemp(context, "compressed_", ".gz");
        try (InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(source));
             GZIPOutputStream gz = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(out)), 64 * 1024)) {
            if (in == null) throw new IOException("Could not open the selected file.");
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) gz.write(buf, 0, n);
        }
        return out;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes / 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.1f KB", v);
        v /= 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.1f MB", v);
        return String.format(Locale.US, "%.2f GB", v / 1024.0);
    }

    public static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
