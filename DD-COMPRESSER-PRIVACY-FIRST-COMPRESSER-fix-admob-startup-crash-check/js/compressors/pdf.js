// PDF compression, built on pdf-lib (window.PDFLib from the vendored
// <script> tag). Deliberately NOT page rasterization.
//
// The original project rendered every page to a canvas via PDF.js and
// rebuilt the document as a stack of images via jsPDF. That shrinks
// photo-heavy PDFs, but it also throws away selectable text, real vector
// graphics, form fields, and annotations on EVERY PDF, even ones that were
// mostly text to begin with — the README for the original project already
// admitted this limitation honestly.
//
// This version instead finds the JPEG images actually embedded in the PDF
// (the "/DCTDecode" XObjects — the same object PDF authors and scanners
// use for photos and scanned pages) and re-encodes just those, in place,
// through the same Canvas pipeline the image engine uses. Text, fonts,
// vector paths, and form fields are untouched because their underlying
// PDF objects are never touched. A PDF that's mostly text and vector
// content (a resume, an invoice, a slide export with no photos) will
// correctly show little or no reduction here — that's the honest result
// for a document with nothing large left to shrink, not a bug.
//
// Scope, stated plainly: only DCTDecode (JPEG) images in an RGB or
// Grayscale colorspace, without a soft mask, are recompressed.
//
// CMYK (and Separation/DeviceN "spot color") images are deliberately
// SKIPPED, left byte-identical to the original, even though skipping them
// can mean very little size reduction on a CMYK-heavy document. This was
// not a theoretical worry: a real 212MB scanned anatomy textbook's
// embedded diagrams — genuine Adobe YCCK/CMYK JPEGs (confirmed by their
// Adobe APP14 marker, transform=2) — came out of the Canvas re-encode
// pipeline almost solid black. Comparing the raw extracted stream against
// the same page rendered by a proper PDF renderer (poppler) confirmed the
// original data is only correct once a PDF/Adobe-aware CMYK inversion is
// applied — a step a generic browser JPEG decode of a standalone stream
// does not reliably perform. Rather than ship a hand-rolled CMYK-to-RGB
// correction this code has no way to verify against a real browser, CMYK
// images are treated the same as SMask images: left completely untouched.
// PNG-style (FlateDecode raw bitmap) images are excluded for the same
// reason as before. See README "PDF engine" for the full writeup.

import { baseName } from '../utils.js';

const { PDFDocument, PDFName, PDFDict, PDFArray, PDFRef, PDFRawStream, PDFNumber } = window.PDFLib || {};

function resolveRef(pdfDoc, obj, depth = 0) {
  while (obj instanceof PDFRef && depth < 6) {
    obj = pdfDoc.context.lookup(obj);
    depth++;
  }
  return obj;
}

// Returns the number of color components (1=Gray, 3=RGB, 4=CMYK/spot), or
// null if it can't be determined — treated as "don't touch it" by the
// caller, same as a confirmed CMYK result.
function colorSpaceComponents(pdfDoc, csRaw) {
  const cs = resolveRef(pdfDoc, csRaw);
  if (!cs) return null;
  const name = cs.toString ? cs.toString() : '';
  if (name === '/DeviceCMYK') return 4;
  if (name === '/DeviceRGB' || name === '/CalRGB') return 3;
  if (name === '/DeviceGray' || name === '/CalGray') return 1;
  if (cs instanceof PDFArray) {
    const first = resolveRef(pdfDoc, cs.get(0));
    const fname = first?.toString ? first.toString() : '';
    if (fname === '/ICCBased') {
      const iccStream = resolveRef(pdfDoc, cs.get(1));
      const n = resolveRef(pdfDoc, iccStream?.dict?.get(PDFName.of('N')));
      return n ? Number(n.toString()) : null;
    }
    if (fname === '/Indexed') return colorSpaceComponents(pdfDoc, cs.get(1));
    if (fname === '/DeviceN' || fname === '/Separation') return 4; // spot-color inks: CMYK-adjacent, same risk
  }
  return null;
}

function findRecompressibleImages(pdfDoc) {
  const found = [];
  let skippedCmyk = 0;
  for (const page of pdfDoc.getPages()) {
    let resources;
    try { resources = page.node.Resources(); } catch { continue; }
    if (!resources) continue;
    const xobjRef = resources.get(PDFName.of('XObject'));
    if (!xobjRef) continue;
    let xobjDict;
    try { xobjDict = pdfDoc.context.lookup(xobjRef, PDFDict); } catch { continue; }
    for (const [, ref] of xobjDict.entries()) {
      let stream;
      try { stream = pdfDoc.context.lookup(ref); } catch { continue; }
      if (!(stream instanceof PDFRawStream)) continue;
      const subtype = stream.dict.get(PDFName.of('Subtype'));
      const filter = stream.dict.get(PDFName.of('Filter'));
      const smask = stream.dict.get(PDFName.of('SMask'));
      if (!(subtype && subtype.toString() === '/Image' && filter && filter.toString() === '/DCTDecode' && !smask)) continue;

      const components = colorSpaceComponents(pdfDoc, stream.dict.get(PDFName.of('ColorSpace')));
      if (components !== 1 && components !== 3) { skippedCmyk++; continue; }

      const w = Number(stream.dict.get(PDFName.of('Width'))?.toString() || 0);
      const h = Number(stream.dict.get(PDFName.of('Height'))?.toString() || 0);
      found.push({ stream, original: stream.getContents(), origWidth: w || null, origHeight: h || null });
    }
  }
  return { found, skippedCmyk };
}

// Same fast, deterministic safety net as js/workers/image-worker.js: a
// tiny (16x16) brightness sample, cheap enough to run on every single
// embedded image without materially slowing down a many-hundred-image
// document. Catches the exact "normally-lit source, near-black output"
// signature of an unhandled color-space problem automatically, on top of
// (not instead of) the specific CMYK exclusion above — real defense in
// depth against this whole class of bug, not just the one instance of it
// that was found and fixed.
async function sampleBrightness(bytes, mime) {
  const SZ = 16;
  const bitmap = await createImageBitmap(new Blob([bytes], { type: mime }));
  const canvas = new OffscreenCanvas(SZ, SZ);
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(bitmap, 0, 0, SZ, SZ);
  bitmap.close?.();
  const { data } = ctx.getImageData(0, 0, SZ, SZ);
  let sum = 0;
  for (let i = 0; i < data.length; i += 4) sum += (data[i] + data[i + 1] + data[i + 2]) / 3;
  return sum / (SZ * SZ);
}
const SUSPICIOUS_DARK_BRIGHTNESS = 12;
const NOT_ALREADY_DARK_BRIGHTNESS = 40;

async function reencodeOne(image, quality, maxDim) {
  let targetW, targetH;
  if (image.origWidth && image.origHeight && Math.max(image.origWidth, image.origHeight) > maxDim) {
    const scale = maxDim / Math.max(image.origWidth, image.origHeight);
    targetW = Math.max(1, Math.round(image.origWidth * scale));
    targetH = Math.max(1, Math.round(image.origHeight * scale));
  }
  // When we already know (from the PDF's own /Width /Height, no decode
  // needed) that we're downscaling, ask createImageBitmap to resize during
  // decode rather than decoding at full resolution and scaling afterward.
  // For a high-DPI scanned page this measurably skips work instead of
  // discarding it after the fact — the difference that matters for a
  // document with hundreds of embedded scans.
  const bitmap = targetW
    ? await createImageBitmap(new Blob([image.original], { type: 'image/jpeg' }), { resizeWidth: targetW, resizeHeight: targetH, resizeQuality: 'medium' })
    : await createImageBitmap(new Blob([image.original], { type: 'image/jpeg' }));
  const w = targetW || bitmap.width, h = targetH || bitmap.height;
  const canvas = new OffscreenCanvas(w, h);
  const ctx = canvas.getContext('2d');
  ctx.drawImage(bitmap, 0, 0, w, h);
  bitmap.close?.();
  const blob = await canvas.convertToBlob({ type: 'image/jpeg', quality });
  return { bytes: new Uint8Array(await blob.arrayBuffer()), w, h };
}

function applyBytes(image, bytes, w, h) {
  image.stream.contents = bytes;
  image.stream.dict.set(PDFName.of('Length'), PDFNumber.of(bytes.length));
  if (w && h) {
    image.stream.dict.set(PDFName.of('Width'), PDFNumber.of(w));
    image.stream.dict.set(PDFName.of('Height'), PDFNumber.of(h));
  }
  // Canvas always encodes RGB JPEG regardless of the source color space.
  image.stream.dict.set(PDFName.of('ColorSpace'), PDFName.of('DeviceRGB'));
  image.stream.dict.delete(PDFName.of('Decode'));
}

export async function compressPdf(file, { targetBytes }, onProgress) {
  if (!PDFDocument) throw new Error('pdf-lib did not load from ./vendor/pdf-lib/pdf-lib.min.js.');
  if (targetBytes >= file.size) {
    return { useOriginal: true, status: 'Already at or under your target size — left unchanged.' };
  }

  onProgress(2, 'Opening PDF…');
  const bytes = new Uint8Array(await file.arrayBuffer());
  const pdfDoc = await PDFDocument.load(bytes, { ignoreEncryption: true });

  const { found: images, skippedCmyk } = findRecompressibleImages(pdfDoc);
  if (images.length === 0) {
    if (skippedCmyk > 0) {
      return {
        useOriginal: true,
        status: `Found ${skippedCmyk} embedded image${skippedCmyk === 1 ? '' : 's'}, but all of them use a CMYK/spot-color colorspace — common in print-sourced scans — which this engine deliberately leaves untouched rather than risk incorrect colors (see README "PDF engine"). Nothing else in this PDF was large enough to meaningfully compress.`,
      };
    }
    return {
      useOriginal: true,
      status: 'No recompressible embedded photos found — this PDF is mostly text/vector content, so DD Compressor leaves it untouched rather than rasterizing pages (which would destroy the selectable text).',
    };
  }

  const originalImageBytes = images.reduce((s, i) => s + i.original.length, 0);
  const overheadBytes = Math.max(0, file.size - originalImageBytes);
  const targetImageBytes = Math.max(1024, targetBytes - overheadBytes);
  const targetRatio = targetImageBytes / originalImageBytes;
  const dimLadder = [2200, 1600, 1100];

  // Many-image documents (scanned books, multi-hundred-page study PDFs)
  // used to run the full grid search — up to 3 dimension levels x 5
  // quality guesses, EVERY ONE OF WHICH re-encoded every single image —
  // against the entire document. For a 906-image anatomy textbook that
  // meant up to ~14,500 encode operations and a progress bar that visibly
  // reset to ~10% at the start of each of those 16 passes, which looks
  // exactly like "it finishes, then restarts" even though it was actually
  // working the whole time. Fixed here two ways: (1) above SAMPLE_THRESHOLD
  // images, the quality/dimension search runs against a small spread-out
  // SAMPLE instead of the whole document, then applies that setting in
  // exactly one full pass — not sixteen; (2) progress is now cumulative
  // across the whole operation instead of restarting every trial.
  const SAMPLE_THRESHOLD = 30;
  const manyImages = images.length > SAMPLE_THRESHOLD;

  let bestQuality, bestMaxDim;

  if (!manyImages) {
    let bestEstimate = Infinity;
    for (const maxDim of dimLadder) {
      let lo = 0.12, hi = 0.85, bestAtDim = null;
      for (let iter = 0; iter < 5; iter++) {
        const q = (lo + hi) / 2;
        let sum = 0;
        for (let i = 0; i < images.length; i++) {
          const out = await reencodeOne(images[i], q, maxDim);
          images[i]._trial = out;
          images[i]._trialKey = `${q}:${maxDim}`;
          sum += out.bytes.length;
        }
        onProgress(5 + Math.round(((dimLadder.indexOf(maxDim) * 5 + iter + 1) / (dimLadder.length * 5)) * 55), `Searching best quality…`);
        if (sum <= targetImageBytes) { lo = q; bestAtDim = { quality: q, estimate: sum }; } else { hi = q; }
      }
      if (bestAtDim) { bestQuality = bestAtDim.quality; bestMaxDim = maxDim; bestEstimate = bestAtDim.estimate; break; }
      if (bestEstimate === Infinity) { bestQuality = lo; bestMaxDim = maxDim; }
    }
  } else {
    // Spread the sample across the whole document (not just the first N
    // pages) so a document that starts with a plain cover page doesn't
    // skew the estimate for the denser pages that follow.
    const SAMPLE_SIZE = Math.min(images.length, 10);
    const sampleIdx = [...new Set(Array.from({ length: SAMPLE_SIZE }, (_, k) => Math.floor((k * images.length) / SAMPLE_SIZE)))];
    const sample = sampleIdx.map((i) => images[i]);
    const sampleOriginalBytes = sample.reduce((s, i) => s + i.original.length, 0);

    onProgress(4, `Found ${images.length} embedded images — estimating the right quality from a sample first…`);
    let bestEstimate = Infinity;
    searchDone:
    for (const maxDim of dimLadder) {
      let lo = 0.12, hi = 0.85, bestAtDim = null;
      for (let iter = 0; iter < 5; iter++) {
        const q = (lo + hi) / 2;
        let sum = 0;
        for (const img of sample) {
          const out = await reencodeOne(img, q, maxDim);
          img._trial = out;
          img._trialKey = `${q}:${maxDim}`;
          sum += out.bytes.length;
        }
        const ratio = sum / sampleOriginalBytes;
        if (ratio <= targetRatio) { lo = q; bestAtDim = { quality: q, estimate: ratio }; } else { hi = q; }
      }
      if (bestAtDim) { bestQuality = bestAtDim.quality; bestMaxDim = maxDim; bestEstimate = bestAtDim.estimate; break searchDone; }
      if (bestEstimate === Infinity) { bestQuality = lo; bestMaxDim = maxDim; }
    }

    // One real pass, timed after the first few images so the "time
    // remaining" estimate is based on this device's actual speed rather
    // than a guess.
    const fullPassStart = performance.now();
    let done = 0;
    for (const image of images) {
      const out = await reencodeOne(image, bestQuality, bestMaxDim);
      image._trial = out;
      image._trialKey = `${bestQuality}:${bestMaxDim}`;
      done++;
      if (done === 5) {
        const perImageMs = (performance.now() - fullPassStart) / 5;
        const remainingMs = perImageMs * (images.length - done);
        const mins = Math.max(1, Math.round(remainingMs / 60000));
        onProgress(15, `Recompressing ${images.length} images — roughly ${mins} minute${mins === 1 ? '' : 's'} left. Keep this tab open.`);
      } else if (done % 10 === 0 || done === images.length) {
        onProgress(10 + Math.round((done / images.length) * 75), `Recompressing embedded image ${done}/${images.length}…`);
      }
    }
  }

  onProgress(88, 'Rebuilding PDF…');
  const finalKey = `${bestQuality}:${bestMaxDim}`;
  let auditBlocked = 0;
  for (const image of images) {
    if (image._trialKey !== finalKey) image._trial = await reencodeOne(image, bestQuality, bestMaxDim);
    let safe = true;
    try {
      const [before, after] = await Promise.all([
        sampleBrightness(image.original, 'image/jpeg'),
        sampleBrightness(image._trial.bytes, 'image/jpeg'),
      ]);
      if (before > NOT_ALREADY_DARK_BRIGHTNESS && after < SUSPICIOUS_DARK_BRIGHTNESS) safe = false;
    } catch { /* audit couldn't run for this image — fail open, same as the image engine */ }
    if (safe) {
      applyBytes(image, image._trial.bytes, image._trial.w, image._trial.h);
    } else {
      auditBlocked++; // leave this one image exactly as it was in the original
    }
  }

  const outBytes = await pdfDoc.save({ useObjectStreams: true });
  onProgress(97, 'Finishing…');

  if (outBytes.length >= file.size) {
    return {
      useOriginal: true,
      status: 'This PDF is already efficiently packed — recompressing its images would only make the file larger.',
    };
  }

  const blob = new Blob([outBytes], { type: 'application/pdf' });
  const cmykNote = skippedCmyk > 0 ? `; ${skippedCmyk} CMYK image${skippedCmyk === 1 ? '' : 's'} left untouched` : '';
  const auditNote = auditBlocked > 0 ? `; ${auditBlocked} image${auditBlocked === 1 ? '' : 's'} blocked by the safety check and left untouched` : '';
  return {
    useOriginal: false,
    blob,
    filename: `${baseName(file.name)}-compressed.pdf`,
    hitTarget: outBytes.length <= targetBytes,
    detail: `${images.length - auditBlocked} embedded image${images.length - auditBlocked === 1 ? '' : 's'} recompressed at ${Math.round(bestQuality * 100)}% quality${cmykNote}${auditNote}`,
    status: outBytes.length <= targetBytes
      ? 'Target reached — text and vector content untouched.'
      : 'Target not fully reached without over-compressing the embedded photos — showing the best result. Text and vector content are untouched either way.',
  };
}