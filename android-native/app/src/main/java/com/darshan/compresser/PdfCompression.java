package com.darshan.compresser;

import android.content.Context;
import android.graphics.Bitmap;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.PDPage;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class PdfCompression {
    private PdfCompression() {}

    public static ImageResult compress(Context context, android.net.Uri uri, long originalBytes, long targetBytes) throws Exception {
        if (originalBytes > 0 && originalBytes <= targetBytes) return new ImageResult(null, originalBytes, "Original already meets target.");
        PDFBoxResourceLoader.init(context.getApplicationContext());

        File sourceFile = FileUtils.newTemp(context, "pdf_source_", ".pdf");
        FileUtils.copy(context, uri, sourceFile);

        Candidate best = null;
        float[] qualities = {0.92f, 0.86f, 0.80f};
        int[] maxDims = {2600, 2200, 1800};

        try {
            for (int pass = 0; pass < qualities.length; pass++) {
                File trial = FileUtils.newTemp(context, "pdf_trial_", ".pdf");
                PDDocument doc = PDDocument.load(sourceFile);
                try {
                    for (PDPage page : doc.getPages()) {
                        PDResources resources = page.getResources();
                        if (resources == null) continue;
                        for (com.tom_roush.pdfbox.cos.COSName name : resources.getXObjectNames()) {
                            try {
                                PDXObject object = resources.getXObject(name);
                                if (!(object instanceof PDImageXObject)) continue;
                                PDImageXObject image = (PDImageXObject)object;
                                Bitmap bitmap = image.getImage();
                                if (bitmap == null || bitmap.getWidth() < 120 || bitmap.getHeight() < 120) {
                                    if (bitmap != null) bitmap.recycle();
                                    continue;
                                }
                                Bitmap working = bitmap;
                                int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
                                if (max > maxDims[pass]) {
                                    float scale = maxDims[pass] / (float)max;
                                    working = Bitmap.createScaledBitmap(
                                            bitmap,
                                            Math.max(96, Math.round(bitmap.getWidth()*scale)),
                                            Math.max(96, Math.round(bitmap.getHeight()*scale)),
                                            true);
                                }
                                if (!working.hasAlpha()) {
                                    PDImageXObject replacement = JPEGFactory.createFromImage(doc, working, qualities[pass]);
                                    if (replacement.getCOSObject().getLength() < image.getCOSObject().getLength()) {
                                        resources.put(name, replacement);
                                    }
                                }
                                if (working != bitmap) working.recycle();
                                bitmap.recycle();
                            } catch (Exception ignored) {
                                // Unsupported/complex image stays untouched; never rasterize the page.
                            }
                        }
                    }
                    doc.save(trial);
                } finally {
                    doc.close();
                }

                long size = trial.length();
                if (size < originalBytes) {
                    if (best == null || size < best.size) {
                        if (best != null) best.file.delete();
                        best = new Candidate(trial, size, qualities[pass], maxDims[pass]);
                    } else {
                        trial.delete();
                    }
                    if (size <= targetBytes) break;
                } else {
                    trial.delete();
                }
            }
        } finally {
            sourceFile.delete();
        }

        if (best == null) return new ImageResult(null, originalBytes, "No embedded raster images could be reduced safely; text, vector graphics and forms were preserved.");
        return new ImageResult(best.file, best.size, best.quality + " JPEG image optimization, max " + best.maxDim + "px");
    }

    public static final class ImageResult {
        final File file; final long size; final String detail;
        ImageResult(File f,long s,String d){file=f;size=s;detail=d;}
    }
    private static final class Candidate {
        final File file; final long size; final float quality; final int maxDim;
        Candidate(File f,long s,float q,int m){file=f;size=s;quality=q;maxDim=m;}
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[128 * 1024]; int n;
        while ((n = in.read(buf)) >= 0) out.write(buf,0,n);
        return out.toByteArray();
    }
}
