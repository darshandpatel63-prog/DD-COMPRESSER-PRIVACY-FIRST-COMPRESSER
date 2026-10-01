package com.darshan.compresser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public final class ImageCompression {
    private ImageCompression() {}

    public static Result compress(Context context, android.net.Uri uri, String name, long originalBytes,
                                   long targetBytes, String requestedFormat) throws Exception {
        if (originalBytes > 0 && originalBytes <= targetBytes)
            return Result.original("Already at or under target; original kept unchanged.");

        String lower = name.toLowerCase();
        if (lower.endsWith(".gif"))
            return Result.original("Animated GIF is kept unchanged so animation is not destroyed.");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open image.");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw new IOException("This image could not be decoded.");

        int sample = 1;
        int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxSide / sample > 4096) sample *= 2;

        Bitmap bitmap;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open image.");
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) throw new IOException("This image could not be decoded.");

        String format = requestedFormat == null ? "Auto" : requestedFormat;
        boolean sourceJpeg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");

        Bitmap.CompressFormat compressFormat;
        String ext;
        if ("JPEG".equalsIgnoreCase(format)) {
            compressFormat = Bitmap.CompressFormat.JPEG;
            ext = "jpg";
        } else if ("PNG".equalsIgnoreCase(format)) {
            compressFormat = Bitmap.CompressFormat.PNG;
            ext = "png";
        } else if ("WebP".equalsIgnoreCase(format)) {
            compressFormat = Build.VERSION.SDK_INT >= 30
                    ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
            ext = "webp";
        } else if (sourceJpeg) {
            compressFormat = Bitmap.CompressFormat.JPEG;
            ext = "jpg";
        } else if (Build.VERSION.SDK_INT >= 30) {
            compressFormat = Bitmap.CompressFormat.WEBP_LOSSY;
            ext = "webp";
        } else {
            compressFormat = Bitmap.CompressFormat.WEBP;
            ext = "webp";
        }

        List<Double> scales = new ArrayList<>();
        for (double s = 1.0; s >= 0.25; s -= 0.08) scales.add(s);

        Candidate best = null;
        for (double scale : scales) {
            int w = Math.max(96, (int)Math.round(bitmap.getWidth() * scale));
            int h = Math.max(96, (int)Math.round(bitmap.getHeight() * scale));
            Bitmap scaled = (w == bitmap.getWidth() && h == bitmap.getHeight())
                    ? bitmap : Bitmap.createScaledBitmap(bitmap, w, h, true);

            int lo = compressFormat == Bitmap.CompressFormat.PNG ? 100 : 70;
            int hi = 96;
            Candidate atScale = null;

            while (lo <= hi) {
                int q = (lo + hi) / 2;
                File candidateFile = FileUtils.newTemp(context, "img_trial_", "." + ext);
                try (FileOutputStream out = new FileOutputStream(candidateFile)) {
                    if (!scaled.compress(compressFormat, q, out))
                        throw new IOException("Android image encoder failed.");
                }
                long size = candidateFile.length();

                if (size <= targetBytes && size < (originalBytes > 0 ? originalBytes : Long.MAX_VALUE)) {
                    atScale = new Candidate(candidateFile, q, scale, size);
                    lo = q + 1;
                } else {
                    candidateFile.delete();
                    hi = q - 1;
                }
            }

            if (atScale != null) {
                if (best != null) best.file.delete();
                best = atScale;
                if (scaled != bitmap) scaled.recycle();
                break;
            }

            File fallback = FileUtils.newTemp(context, "img_fallback_", "." + ext);
            try (FileOutputStream out = new FileOutputStream(fallback)) {
                int q = compressFormat == Bitmap.CompressFormat.PNG ? 100 : 70;
                if (!scaled.compress(compressFormat, q, out))
                    throw new IOException("Android image encoder failed.");
            }
            if (fallback.length() < (originalBytes > 0 ? originalBytes : Long.MAX_VALUE)) {
                if (best == null || fallback.length() < best.size) {
                    if (best != null) best.file.delete();
                    best = new Candidate(fallback, 70, scale, fallback.length());
                } else {
                    fallback.delete();
                }
            } else {
                fallback.delete();
            }
            if (scaled != bitmap) scaled.recycle();
        }

        bitmap.recycle();
        if (best == null)
            throw new IOException("Could not make this image smaller within the selected format and target size.");

        return Result.file(best.file, ext, best.size, best.quality, best.scale);
    }

    public static final class Result {
        final File file; final String ext; final long size; final int quality;
        final double scale; final boolean original; final String message;
        private Result(File f,String e,long s,int q,double sc,boolean o,String m){
            file=f;ext=e;size=s;quality=q;scale=sc;original=o;message=m;
        }
        static Result file(File f,String e,long s,int q,double sc){return new Result(f,e,s,q,sc,false,null);}
        static Result original(String m){return new Result(null,null,0,0,1,true,m);}
    }

    private static final class Candidate {
        final File file; final int quality; final double scale; final long size;
        Candidate(File f,int q,double s,long z){file=f;quality=q;scale=s;size=z;}
    }
}
