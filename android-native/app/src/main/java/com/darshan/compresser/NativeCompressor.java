package com.darshan.compresser;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

public final class NativeCompressor {
    private NativeCompressor() {}

    public interface Progress { void update(int pct, String message); }

    public static MainActivity.NativeResult compress(
            Context context, Uri uri, String name, String mime, long originalBytes, long targetBytes, Progress progress) throws Exception {

        String m = mime == null ? "" : mime.toLowerCase();
        if (m.startsWith("image/")) {
            ImageCompression.Result r = ImageCompression.compress(context, uri, name, originalBytes, targetBytes);
            if (r.original) {
                File copy = FileUtils.newTemp(context, "original_", ext(name));
                FileUtils.copy(context, uri, copy);
                return new MainActivity.NativeResult(copy, name, mime, r.message, true);
            }
            progress.update(98, "Finishing image…");
            return new MainActivity.NativeResult(r.file, FileUtils.baseName(name) + "-compressed." + r.ext,
                    r.ext.equals("jpg") ? "image/jpeg" : "image/webp",
                    "Quality " + r.quality + "% • " + Math.round(r.scale * 100) + "% dimensions", false);
        }

        if (m.startsWith("video/") || looksVideo(name)) {
            return compressMedia(context, uri, name, true, originalBytes, targetBytes, progress);
        }

        if (m.startsWith("audio/") || looksAudio(name)) {
            return compressMedia(context, uri, name, false, originalBytes, targetBytes, progress);
        }

        if ("application/pdf".equals(m) || name.toLowerCase().endsWith(".pdf")) {
            progress.update(5, "Opening PDF…");
            PdfCompression.ImageResult r = PdfCompression.compress(context, uri, originalBytes, targetBytes);
            if (r.file == null) {
                File copy = FileUtils.newTemp(context, "original_", ".pdf");
                FileUtils.copy(context, uri, copy);
                return new MainActivity.NativeResult(copy, name, "application/pdf", r.detail, true);
            }
            progress.update(98, "Finishing PDF…");
            return new MainActivity.NativeResult(r.file, FileUtils.baseName(name) + "-compressed.pdf", "application/pdf", r.detail, false);
        }

        progress.update(25, "Compressing losslessly…");
        File gz = FileUtils.gzip(context, uri, name);
        if (originalBytes > 0 && gz.length() >= originalBytes) {
            gz.delete();
            File copy = FileUtils.newTemp(context, "original_", ext(name));
            FileUtils.copy(context, uri, copy);
            return new MainActivity.NativeResult(copy, name, mime, "Gzip was not smaller; original kept unchanged.", true);
        }
        progress.update(98, "Finishing…");
        return new MainActivity.NativeResult(gz, name + ".gz", "application/gzip", "Lossless gzip", false);
    }

    private static MainActivity.NativeResult compressMedia(Context context, Uri uri, String name, boolean video,
                                                            long originalBytes, long targetBytes, Progress progress) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<MainActivity.NativeResult> result = new AtomicReference<>();
        final AtomicReference<Exception> error = new AtomicReference<>();
        Handler main = new Handler(Looper.getMainLooper());

        main.post(() -> MediaCompression.compress(context, uri, name, video, originalBytes, targetBytes, new MediaCompression.Callback() {
            @Override public void progress(int pct, String label) { progress.update(pct, label); }

            @Override public void done(File output, long original, long outSize, String ext, String detail) {
                try {
                    if (output == null) {
                        File copy = FileUtils.newTemp(context, "original_", ext(name));
                        FileUtils.copy(context, uri, copy);
                        result.set(new MainActivity.NativeResult(copy, name, mimeFor(video), detail, true));
                    } else {
                        result.set(new MainActivity.NativeResult(output, FileUtils.baseName(name) + "-compressed." + ext, mimeFor(video), detail, false));
                    }
                } catch (Exception e) { error.set(e); }
                latch.countDown();
            }

            @Override public void failed(Exception e) { error.set(e); latch.countDown(); }
        }));

        latch.await();
        if (error.get() != null) throw error.get();
        if (result.get() == null) throw new Exception("Native media compressor returned no result.");
        return result.get();
    }

    private static String mimeFor(boolean video) { return video ? "video/mp4" : "audio/mp4"; }
    private static boolean looksVideo(String n) { String s=n.toLowerCase(); return s.endsWith(".mp4")||s.endsWith(".m4v")||s.endsWith(".mov")||s.endsWith(".mkv")||s.endsWith(".webm")||s.endsWith(".avi")||s.endsWith(".3gp"); }
    private static boolean looksAudio(String n) { String s=n.toLowerCase(); return s.endsWith(".mp3")||s.endsWith(".m4a")||s.endsWith(".aac")||s.endsWith(".wav")||s.endsWith(".ogg")||s.endsWith(".flac")||s.endsWith(".opus"); }
    private static String ext(String n) { int i=n.lastIndexOf('.'); return i>0 ? n.substring(i) : ".bin"; }
}
