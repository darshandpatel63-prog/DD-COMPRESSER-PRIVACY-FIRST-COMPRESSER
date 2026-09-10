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
// Scope, stated plainly: only DCTDecode (JPEG) images without a soft mask
// are recompressed. PNG-style (FlateDecode raw bitmap) images and images
// with an alpha soft mask are left exactly as they are, rather than risk
// corrupting a transparency effect or a color space this code hasn't
// verified against. See README "PDF engine" for the full writeup.

import { baseName } from '../utils.js';

const { PDFDocument, PDFName, PDFDict, PDFRawStream, PDFNumber } = window.PDFLib || {};

function findRecompressibleImages(pdfDoc) {
  const found = [];
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
      if (subtype && subtype.toString() === '/Image' && filter && filter.toString() === '/DCTDecode' && !smask) {
        const w = Number(stream.dict.get(PDFName.of('Width'))?.toString() || 0);
        const h = Number(stream.dict.get(PDFName.of('Height'))?.toString() || 0);
        found.push({ stream, original: stream.getContents(), origWidth: w || null, origHeight: h || null });
      }
    }
  }
  return found;
}

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

  const images = findRecompressibleImages(pdfDoc);
  if (images.length === 0) {
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
  for (const image of images) {
    if (image._trialKey !== finalKey) image._trial = await reencodeOne(image, bestQuality, bestMaxDim);
    applyBytes(image, image._trial.bytes, image._trial.w, image._trial.h);
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
  return {
    useOriginal: false,
    blob,
    filename: `${baseName(file.name)}-compressed.pdf`,
    hitTarget: outBytes.length <= targetBytes,
    detail: `${images.length} embedded image${images.length === 1 ? '' : 's'} recompressed at ${Math.round(bestQuality * 100)}% quality`,
    status: outBytes.length <= targetBytes
      ? 'Target reached — text and vector content untouched.'
      : 'Target not fully reached without over-compressing the embedded photos — showing the best result. Text and vector content are untouched either way.',
  };
}
