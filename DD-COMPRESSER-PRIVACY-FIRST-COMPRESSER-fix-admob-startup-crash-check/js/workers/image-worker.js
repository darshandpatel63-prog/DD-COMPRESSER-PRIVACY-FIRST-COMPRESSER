// Image compression engine — entirely our own code (Canvas/OffscreenCanvas
// + a plain binary search), no external library. Runs in a dedicated Worker
// so a big photo never blocks the UI thread on a phone.
//
// Algorithm summary (see README "Image engine" for the full writeup):
//   1. If the target is already met or exceeded by the original file, do
//      nothing — never re-encode a file that doesn't need it.
//   2. For the chosen format(s), binary-search JPEG/WebP quality at the
//      current dimensions. If a quality >= a "looks fine" floor fits the
//      target, take it.
//   3. If even the lowest acceptable quality is still over target, shrink
//      dimensions using a ratio estimated from the actual measured
//      overshoot (not a guess), and repeat.
//   4. PNG has no quality knob in the Canvas API, so it is driven by
//      dimension alone; in "auto" mode, if PNG genuinely can't reach the
//      target, the search automatically continues in WebP instead and the
//      result says so — it does not just report a PNG that misses target.
//   5. Whatever the search finds, the final candidate is only accepted if
//      it is smaller than the original file. Re-encoding a small, already
//      optimized image can easily produce something LARGER (a photo saved
//      as PNG is a classic case) — this is the exact "already-small image
//      gets stuck around ~400 KB" failure mode from the original project,
//      and the fix is simply to never accept a result that lost the bet.

const MIN_DIM = 96; // never scale a side below this many pixels
const MIN_Q = 0.08;
const MAX_Q = 0.95;
const QUALITY_ITERATIONS = 7;
const MAX_PASSES_PER_FORMAT = 9;
const GOOD_ENOUGH_QUALITY = 0.4; // stop shrinking further once quality is at least this

// Reads the number of color components straight out of a JPEG's SOF marker
// — 1 = Grayscale, 3 = RGB/YCbCr (the normal web case), 4 = CMYK/YCCK. This
// needs no decode at all, just walking the marker sequence, so it's cheap
// enough to run on every JPEG before touching Canvas.
//
// Why this matters: a CMYK/YCCK JPEG (the common export from print/design
// software — Photoshop, InDesign, professional scanners) usually needs an
// Adobe-aware color inversion to display correctly, which this project's
// PDF engine confirmed a generic createImageBitmap() decode does not
// reliably apply — real embedded scans came out solid black (see README
// bug #8). The same risk exists for a standalone CMYK JPEG uploaded
// directly, so it gets the same treatment: detect it here and refuse
// rather than silently bake in wrong colors.
function jpegComponentCount(bytes) {
  if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) return null; // not a JPEG (SOI)
  let offset = 2;
  while (offset + 3 < bytes.length) {
    if (bytes[offset] !== 0xff) { offset++; continue; } // resync defensively
    const marker = bytes[offset + 1];
    if (marker === 0xff) { offset++; continue; } // fill byte
    if (marker === 0xd9) break; // EOI
    if ((marker >= 0xd0 && marker <= 0xd7) || marker === 0x01) { offset += 2; continue; } // no-payload markers
    const length = (bytes[offset + 2] << 8) | bytes[offset + 3];
    const isSOF = marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
    if (isSOF) {
      const componentsOffset = offset + 9; // marker(2) + length(2) + precision(1) + height(2) + width(2)
      return componentsOffset < bytes.length ? bytes[componentsOffset] : null;
    }
    if (length < 2) break; // malformed — stop rather than loop
    offset += 2 + length;
  }
  return null;
}

function nextScale(currentScale, measuredSize, targetBytes) {
  const ratio = Math.sqrt(targetBytes / Math.max(1, measuredSize));
  const step = Math.max(0.5, Math.min(0.85, ratio));
  return Math.max(currentScale * step, 0.04);
}

async function encode(bitmap, w, h, mime, quality) {
  const canvas = new OffscreenCanvas(w, h);
  const ctx = canvas.getContext('2d');
  if (mime === 'image/jpeg') {
    // JPEG has no alpha channel; flatten onto white the way every real
    // JPEG encoder implicitly does, instead of letting transparent pixels
    // turn black.
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, w, h);
  }
  ctx.drawImage(bitmap, 0, 0, w, h);
  const opts = mime === 'image/png' ? { type: mime } : { type: mime, quality };
  return canvas.convertToBlob(opts);
}

async function searchFormat(bitmap, mime, targetBytes, track) {
  const W = bitmap.width, H = bitmap.height;
  let scale = 1;
  let best = null; // {blob, scale, quality}

  for (let pass = 0; pass < MAX_PASSES_PER_FORMAT; pass++) {
    const w = Math.max(MIN_DIM, Math.round(W * scale));
    const h = Math.max(MIN_DIM, Math.round(H * scale));
    const atFloor = w <= MIN_DIM && h <= MIN_DIM;

    if (mime === 'image/png') {
      const blob = await encode(bitmap, w, h, mime, null);
      track(blob, scale, null);
      if (blob.size <= targetBytes) return { blob, scale, quality: null, hitTarget: true };
      if (!best || blob.size < best.blob.size) best = { blob, scale, quality: null };
      if (atFloor) return { ...best, hitTarget: false };
      scale = nextScale(scale, blob.size, targetBytes);
      continue;
    }

    let lo = MIN_Q, hi = MAX_Q, bestAtScale = null, qAtScale = null, smallestAtScale = null;
    for (let i = 0; i < QUALITY_ITERATIONS; i++) {
      const q = (lo + hi) / 2;
      const blob = await encode(bitmap, w, h, mime, q);
      track(blob, scale, q);
      if (!smallestAtScale || blob.size < smallestAtScale.size) smallestAtScale = blob;
      if (blob.size > targetBytes) {
        hi = q;
      } else {
        lo = q;
        bestAtScale = blob;
        qAtScale = q;
      }
    }

    if (bestAtScale) {
      if (!best || bestAtScale.size > best.blob.size) best = { blob: bestAtScale, scale, quality: qAtScale };
      if (qAtScale >= GOOD_ENOUGH_QUALITY || atFloor) {
        return { blob: bestAtScale, scale, quality: qAtScale, hitTarget: true };
      }
      // It fits, but only at low quality — a smaller frame might fit at a
      // visibly better quality, so keep going rather than settle early.
    } else if (!best && smallestAtScale) {
      // Nothing at this scale reached target even at MIN_Q; remember the
      // smallest thing we measured as a fallback candidate.
      if (!best || smallestAtScale.size < best.blob.size) best = { blob: smallestAtScale, scale, quality: MIN_Q };
    }

    if (atFloor) return best ? { ...best, hitTarget: !!bestAtScale } : { blob: null, hitTarget: false };
    const measured = smallestAtScale ? smallestAtScale.size : targetBytes * 2;
    scale = nextScale(scale, measured, targetBytes);
  }

  return best ? { ...best, hitTarget: best.blob.size <= targetBytes } : { blob: null, hitTarget: false };
}

// A fast, deterministic safety net — NOT a cosmetic check. Draws a bitmap
// into a tiny (24x24) canvas and returns its average brightness (0-255).
// Comparing this before/after catches a specific, real failure signature:
// output that went from normally-lit to near-black, which is exactly what
// an unhandled CMYK/color-space misinterpretation produces (see README bug
// #8 — this is the same class of corruption, now checked for automatically
// on every single compression, not just PDFs). 24x24 is enough pixels to
// be a stable average without costing more than a couple of milliseconds.
async function sampleBrightness(bitmapSource) {
  const SZ = 24;
  const canvas = new OffscreenCanvas(SZ, SZ);
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(bitmapSource, 0, 0, SZ, SZ);
  const { data } = ctx.getImageData(0, 0, SZ, SZ);
  let sum = 0;
  for (let i = 0; i < data.length; i += 4) sum += (data[i] + data[i + 1] + data[i + 2]) / 3;
  return sum / (SZ * SZ);
}
const SUSPICIOUS_DARK_BRIGHTNESS = 12; // near-solid-black, out of 255
const NOT_ALREADY_DARK_BRIGHTNESS = 40; // original has to have had real content to lose

async function compress({ arrayBuffer, mime: sourceMime, name, targetBytes, format, originalBytes }, progress) {
  if (typeof OffscreenCanvas === 'undefined') {
    throw new Error('This browser does not support OffscreenCanvas, which the image engine needs.');
  }
  if (targetBytes >= originalBytes) {
    return { useOriginal: true, status: 'Already at or under your target size — left unchanged.' };
  }

  const bytesView = new Uint8Array(arrayBuffer);
  const jpegComponents = (sourceMime === 'image/jpeg' || sourceMime === 'image/jpg') ? jpegComponentCount(bytesView) : null;
  if (jpegComponents === 4) {
    throw new Error('This is a CMYK JPEG (common from print/design software like Photoshop or a professional scanner). Browser-based decoding does not reliably preserve CMYK colors — recompressing it risks producing wrong or near-black colors, the same issue confirmed and documented for this project\u2019s PDF engine. Convert it to RGB first (most photo editors have a "convert to RGB" or "Assign/Convert Profile" option), then compress the RGB version here.');
  }

  const bitmap = await createImageBitmap(new Blob([arrayBuffer], { type: sourceMime }));

  let mimeList;
  const autoMode = format === 'auto';
  if (format === 'jpeg') mimeList = ['image/jpeg'];
  else if (format === 'png') mimeList = ['image/png'];
  else if (format === 'webp') mimeList = ['image/webp'];
  else mimeList = ['image/webp', 'image/jpeg'];

  let attempts = 0;
  const track = () => { attempts++; progress(Math.min(92, 10 + attempts * 2)); };

  let finalResult = null;
  let usedMime = mimeList[0];
  let switchedFormat = false;

  for (let i = 0; i < mimeList.length; i++) {
    const mime = mimeList[i];
    const result = await searchFormat(bitmap, mime, targetBytes, track);
    if (result.blob) {
      usedMime = mime;
      finalResult = result;
      if (result.hitTarget) break;
    }
    if (!autoMode) break; // explicit format choice: don't silently substitute another format
    if (i < mimeList.length - 1) switchedFormat = true; else if (finalResult) switchedFormat = false;
  }

  // Sample the original's brightness before closing it — needed for the
  // post-compression audit below, and a closed ImageBitmap can't be drawn
  // from again.
  const originalBrightness = await sampleBrightness(bitmap);
  bitmap.close?.();

  if (!finalResult || !finalResult.blob) {
    throw new Error('Could not produce a smaller version of this image.');
  }

  const buf = await finalResult.blob.arrayBuffer();

  // Post-compression audit: decode the actual candidate and compare it
  // against the original. If a normally-lit image came out looking
  // solid black, refuse the result outright rather than hand back
  // something silently corrupted.
  try {
    const outputBitmap = await createImageBitmap(new Blob([buf], { type: usedMime }));
    const afterBrightness = await sampleBrightness(outputBitmap);
    outputBitmap.close?.();
    if (originalBrightness > NOT_ALREADY_DARK_BRIGHTNESS && afterBrightness < SUSPICIOUS_DARK_BRIGHTNESS) {
      return {
        useOriginal: true,
        status: 'A safety check caught the compressed result looking suspiciously different from the original (much darker than expected) and blocked it rather than risk handing back a corrupted file. The original is kept as-is — please report this so it can be fixed properly.',
      };
    }
  } catch {
    // If the audit step itself can't run for some reason, fail open rather
    // than block a perfectly good result over a check that didn't complete.
  }

  if (finalResult.blob.size >= originalBytes) {
    if (autoMode) {
      return { useOriginal: true, status: 'Already efficient — recompressing would only make it larger.' };
    }
    return {
      useOriginal: false, buffer: buf, mime: usedMime, hitTarget: false, switchedFormat: false,
      quality: finalResult.quality, scale: finalResult.scale,
      status: `This output format needs more data than the original file at any usable quality — showing the smallest ${usedMime.replace('image/', '').toUpperCase()} found.`,
    };
  }

  let status;
  if (finalResult.hitTarget) {
    status = switchedFormat
      ? `Target reached — automatically switched to ${usedMime.replace('image/', '').toUpperCase()} because PNG couldn't reach it.`
      : 'Target reached.';
  } else {
    status = 'Target not fully reached — showing the best result possible without excessive quality loss.';
  }

  return {
    useOriginal: false,
    buffer: buf,
    mime: usedMime,
    hitTarget: finalResult.hitTarget,
    switchedFormat,
    quality: finalResult.quality,
    scale: finalResult.scale,
    status,
  };
}

self.onmessage = async (e) => {
  const { id, payload } = e.data;
  try {
    const result = await compress(payload, (pct) => self.postMessage({ id, type: 'progress', pct }));
    const transfer = result.buffer ? [result.buffer] : [];
    self.postMessage({ id, type: 'done', result }, transfer);
  } catch (err) {
    self.postMessage({ id, type: 'error', message: err && err.message ? err.message : String(err) });
  }
};
