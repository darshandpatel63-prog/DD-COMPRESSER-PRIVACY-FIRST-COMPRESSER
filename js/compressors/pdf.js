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
        found.push({ stream, original: stream.getContents() });
      }
    }
  }
  return found;
}

async function decode(bytes) {
  return createImageBitmap(new Blob([bytes], { type: 'image/jpeg' }));
}

async function reencodeOne(image, quality, maxDim) {
  const bitmap = await decode(image.original);
  let w = bitmap.width, h = bitmap.height;
  if (Math.max(w, h) > maxDim) {
    const scale = maxDim / Math.max(w, h);
    w = Math.max(1, Math.round(w * scale));
    h = Math.max(1, Math.round(h * scale));
  }
  const canvas = new OffscreenCanvas(w, h);
  const ctx = canvas.getContext('2d');
  ctx.drawImage(bitmap, 0, 0, w, h);
  bitmap.close?.();
  const blob = await canvas.convertToBlob({ type: 'image/jpeg', quality });
  return new Uint8Array(await blob.arrayBuffer());
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

  onProgress(5, 'Opening PDF…');
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

  // Fast estimate loop: recompress in-memory and sum byte counts, without
  // paying for a full PDF serialization on every trial.
  async function tryQuality(quality, maxDim) {
    let sum = 0;
    let i = 0;
    for (const image of images) {
      const out = await reencodeOne(image, quality, maxDim);
      image._trial = out;
      sum += out.length;
      i++;
      onProgress(10 + Math.round((i / images.length) * 60), `Recompressing embedded image ${i}/${images.length}…`);
    }
    return sum;
  }

  const targetImageBytes = Math.max(1024, targetBytes - overheadBytes);
  let lo = 0.12, hi = 0.85, bestQuality = null, bestMaxDim = 2200, bestEstimate = Infinity;
  const dimLadder = [2200, 1600, 1100];

  for (const maxDim of dimLadder) {
    lo = 0.12; hi = 0.85;
    let bestAtDim = null;
    for (let iter = 0; iter < 5; iter++) {
      const q = (lo + hi) / 2;
      const est = await tryQuality(q, maxDim);
      if (est <= targetImageBytes) { lo = q; bestAtDim = { quality: q, estimate: est }; }
      else { hi = q; }
    }
    if (bestAtDim) { bestQuality = bestAtDim.quality; bestMaxDim = maxDim; bestEstimate = bestAtDim.estimate; break; }
    if (bestEstimate === Infinity) { bestQuality = lo; bestMaxDim = maxDim; }
  }

  onProgress(75, 'Rebuilding PDF…');
  await tryQuality(bestQuality, bestMaxDim); // final pass at the chosen settings
  for (const image of images) {
    const bitmap = await decode(image.original);
    let w = bitmap.width, h = bitmap.height;
    bitmap.close?.();
    if (Math.max(w, h) > bestMaxDim) {
      const scale = bestMaxDim / Math.max(w, h);
      w = Math.max(1, Math.round(w * scale));
      h = Math.max(1, Math.round(h * scale));
    }
    applyBytes(image, image._trial, w, h);
  }

  const outBytes = await pdfDoc.save({ useObjectStreams: true });
  onProgress(95, 'Finishing…');

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
