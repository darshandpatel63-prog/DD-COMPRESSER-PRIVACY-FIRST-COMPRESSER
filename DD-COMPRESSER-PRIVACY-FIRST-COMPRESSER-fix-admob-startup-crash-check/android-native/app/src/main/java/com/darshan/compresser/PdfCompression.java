package com.darshan.compresser;

import android.content.Context;
import android.graphics.Bitmap;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSStream;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public final class PdfCompression {
    private PdfCompression() {}

    public static ImageResult compress(Context context, android.net.Uri uri, long originalBytes, long targetBytes) throws Exception {
        if (originalBytes > 0 && originalBytes <= targetBytes) {
            return new ImageResult(null, originalBytes, "Original already meets target.");
        }
        PDFBoxResourceLoader.init(context.getApplicationContext());

        File sourceFile = FileUtils.newTemp(context, "pdf_source_", ".pdf");
        FileUtils.copy(context, uri, sourceFile);

        Candidate best = null;
        float[] qualities = {0.92f, 0.86f, 0.80f};
        int[] maxDims = {2600, 2200, 1800};

        try {
            // Lossless pass for text/vector PDFs before changing image quality.
            File structuralTrial = FileUtils.newTemp(context, "pdf_struct_", ".pdf");
            PDDocument structuralDoc = PDDocument.load(sourceFile);
            try {
                optimizeTextAndVectorStreams(structuralDoc);
                structuralDoc.save(structuralTrial);
            } finally {
                structuralDoc.close();
            }

            long structuralSize = structuralTrial.length();
            if (structuralSize < originalBytes) {
                best = new Candidate(structuralTrial, structuralSize, 1.0f, 0, true);
                if (structuralSize <= targetBytes) {
                    return new ImageResult(best.file, best.size,
                            "Lossless text/vector stream optimization.");
                }
            } else {
                structuralTrial.delete();
            }

            for (int pass = 0; pass < qualities.length; pass++) {
                File trial = FileUtils.newTemp(context, "pdf_trial_", ".pdf");
                PDDocument doc = PDDocument.load(sourceFile);
                try {
                    // Keep text, vector graphics and layout as native PDF content.
                    optimizeTextAndVectorStreams(doc);

                    for (PDPage page : doc.getPages()) {
                        PDResources resources = page.getResources();
                        if (resources == null) continue;
                        for (COSName name : resources.getXObjectNames()) {
                            try {
                                PDXObject object = resources.getXObject(name);
                                if (!(object instanceof PDImageXObject)) continue;
                                PDImageXObject image = (PDImageXObject) object;
                                Bitmap bitmap = image.getImage();
                                if (bitmap == null || bitmap.getWidth() < 120 || bitmap.getHeight() < 120) {
                                    if (bitmap != null) bitmap.recycle();
                                    continue;
                                }

                                Bitmap working = bitmap;
                                int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
                                if (max > maxDims[pass]) {
                                    float scale = maxDims[pass] / (float) max;
                                    working = Bitmap.createScaledBitmap(
                                            bitmap,
                                            Math.max(96, Math.round(bitmap.getWidth() * scale)),
                                            Math.max(96, Math.round(bitmap.getHeight() * scale)),
                                            true);
                                }

                                if (!working.hasAlpha()) {
                                    PDImageXObject replacement =
                                            JPEGFactory.createFromImage(doc, working, qualities[pass]);
                                    if (replacement.getCOSObject().getLength()
                                            < image.getCOSObject().getLength()) {
                                        resources.put(name, replacement);
                                    }
                                }

                                if (working != bitmap) working.recycle();
                                bitmap.recycle();
                            } catch (Exception ignored) {
                                // Unsupported/complex image stays untouched.
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
                        best = new Candidate(trial, size, qualities[pass], maxDims[pass], false);
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

        if (best == null) {
            return new ImageResult(null, originalBytes,
                    "No safe reduction found. Text, vector graphics, forms and embedded content were preserved.");
        }

        String detail = best.structuralOnly
                ? "Lossless text/vector stream optimization"
                : best.quality + " JPEG image optimization, max " + best.maxDim
                    + "px + lossless text/vector optimization";
        return new ImageResult(best.file, best.size, detail);
    }

    /**
     * Losslessly compress page-content streams. These contain text operators,
     * vector drawing commands and layout instructions. They are never rasterized.
     */
    private static void optimizeTextAndVectorStreams(PDDocument doc) {
        for (PDPage page : doc.getPages()) {
            try {
                COSBase contents = page.getCOSObject().getItem(COSName.CONTENTS);
                if (contents instanceof COSStream) {
                    flateStream((COSStream) contents);
                } else if (contents instanceof COSArray) {
                    COSArray array = (COSArray) contents;
                    for (int i = 0; i < array.size(); i++) {
                        COSBase item = array.getObject(i);
                        if (item instanceof COSStream) {
                            flateStream((COSStream) item);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Keep a problematic page stream untouched.
            }
        }
    }

    private static void flateStream(COSStream stream) {
        try {
            byte[] decoded;
            try (InputStream in = stream.createInputStream()) {
                decoded = readAll(in);
            }
            if (decoded.length == 0) return;

            OutputStream out = stream.createOutputStream(COSName.FLATE_DECODE);
            out.write(decoded);
            out.close();
        } catch (Exception ignored) {
            // Never sacrifice PDF validity for an optimization.
        }
    }

    public static final class ImageResult {
        final File file; final long size; final String detail;
        ImageResult(File f, long s, String d) { file = f; size = s; detail = d; }
    }

    private static final class Candidate {
        final File file; final long size; final float quality; final int maxDim;
        final boolean structuralOnly;
        Candidate(File f, long s, float q, int m, boolean structural) {
            file = f; size = s; quality = q; maxDim = m; structuralOnly = structural;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[128 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
