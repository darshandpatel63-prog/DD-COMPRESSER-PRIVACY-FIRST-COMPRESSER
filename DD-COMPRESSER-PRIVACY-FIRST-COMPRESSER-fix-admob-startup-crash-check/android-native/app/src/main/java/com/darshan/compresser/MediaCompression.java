package com.darshan.compresser;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.transformer.Effects;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.AudioEncoderSettings;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaCompression {
    private MediaCompression() {}

    public interface Callback {
        void progress(int pct, String label);
        void done(File output, long originalBytes, long outputBytes, String ext, String detail);
        void failed(Exception error);
    }

    public static void compress(Context context, Uri uri, String name, boolean video, long originalBytes, long targetBytes, Callback callback) {
        if (originalBytes > 0 && originalBytes <= targetBytes) {
            callback.done(null, originalBytes, originalBytes, video ? "mp4" : "m4a", "Original already meets target; no lossy re-encode was needed.");
            return;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        long durationUs;
        int width = 0, height = 0;
        try {
            retriever.setDataSource(context, uri);
            String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            durationUs = Math.max(1_000_000L, Long.parseLong(d == null ? "1" : d) * 1000L);
            if (video) {
                width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), 1280);
                height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), 720);
            }
        } catch (Exception e) {
            callback.failed(new Exception("Could not read media metadata: " + e.getMessage(), e));
            return;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }

        long initialVideoBps = Math.max(160_000L, (long) (((targetBytes * 8.0 * 0.90) / (durationUs / 1_000_000.0)) - (video ? 96_000 : 0)));
        runPass(context, uri, video, targetBytes, originalBytes, durationUs, width, height, initialVideoBps, 0, callback);
    }

    private static void runPass(Context context, Uri uri, boolean video, long targetBytes, long originalBytes, long durationUs,
                                int srcW, int srcH, long totalVideoBps, int pass, Callback callback) {
        final long audioBps = video ? Math.max(64_000L, Math.min(128_000L, (long)(totalVideoBps * 0.12))) : Math.max(48_000L, Math.min(192_000L, totalVideoBps));
        final long videoBps = video ? Math.max(180_000L, totalVideoBps - audioBps) : 0L;
        int outputHeight = srcH;
        if (video) {
            double targetPixels = videoBps / (0.055 * 30.0);
            double desiredScale = Math.sqrt(targetPixels / Math.max(1.0, (double)srcW * srcH));
            desiredScale = Math.max(0.40, Math.min(1.0, desiredScale));
            outputHeight = Math.max(240, ((int)Math.round(srcH * desiredScale) / 2) * 2);
            if (outputHeight > srcH) outputHeight = srcH;
        }

        final int finalOutputHeight = outputHeight;
        File out;
        try { out = FileUtils.newTemp(context, "media_", video ? ".mp4" : ".m4a"); }
        catch (Exception e) { callback.failed(e); return; }

        MediaItem item = MediaItem.fromUri(uri);
        EditedMediaItem.Builder editedBuilder = new EditedMediaItem.Builder(item);
        if (video && outputHeight < srcH) {
            Presentation presentation = Presentation.createForHeight(outputHeight);
            Effects effects = new Effects(ImmutableList.of(), ImmutableList.of(presentation));
            editedBuilder.setEffects(effects);
        }
        EditedMediaItem edited = editedBuilder.build();

        DefaultEncoderFactory.Builder encoderBuilder = new DefaultEncoderFactory.Builder(context);
        if (video) {
            encoderBuilder.setRequestedVideoEncoderSettings(
                    new VideoEncoderSettings.Builder()
                            .setBitrate((int)Math.min(Integer.MAX_VALUE, videoBps))
                             .setiFrameIntervalSeconds(1.0f)
                            .build());
        }
        encoderBuilder.setRequestedAudioEncoderSettings(
                new AudioEncoderSettings.Builder()
                        .setBitrate((int)Math.min(Integer.MAX_VALUE, audioBps))
                        .build());

        Transformer.Builder transformerBuilder = new Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderBuilder.build());
        if (video) transformerBuilder.setVideoMimeType(MimeTypes.VIDEO_H264);

        final Transformer transformer = transformerBuilder.addListener(new Transformer.Listener() {
            @Override public void onCompleted(Composition composition, ExportResult result) {
                long size = out.length();
                if (size <= targetBytes || pass >= 3) {
                    if (size >= originalBytes && originalBytes > 0) {
                        out.delete();
                        callback.done(null, originalBytes, originalBytes, video ? "mp4" : "m4a", "Re-encoding would make this file larger, so the original was kept.");
                    } else {
                        String detail = video
                                ? (finalOutputHeight < srcH ? finalOutputHeight + "p • " + Math.round(videoBps / 1000.0) + " kbps video" : Math.round(videoBps / 1000.0) + " kbps video")
                                : Math.round(audioBps / 1000.0) + " kbps AAC";
                        callback.done(out, originalBytes, size, video ? "mp4" : "m4a", detail);
                    }
                } else {
                    out.delete();
                    long nextBps = Math.max(video ? 180_000L : 48_000L, (long)(totalVideoBps * 0.82));
                    callback.progress(Math.min(92, 55 + pass * 10), "Tuning quality and size…");
                    runPass(context, uri, video, targetBytes, originalBytes, durationUs, srcW, srcH, nextBps, pass + 1, callback);
                }
            }

            @Override public void onError(Composition composition, ExportResult result, ExportException exception) {
                out.delete();
                callback.failed(new Exception("Android media encoder failed: " + exception.getMessage(), exception));
            }
        }).build();

        callback.progress(Math.min(90, 10 + pass * 20), "Native " + (video ? "video" : "audio") + " encoding…");
        transformer.start(edited, out.getAbsolutePath());
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }
}
