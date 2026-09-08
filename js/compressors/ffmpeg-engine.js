// Loads @ffmpeg/ffmpeg (already on window.FFmpegWASM via the <script> tag in
// index.html) and its WebAssembly core, entirely from local files. Reused
// as a singleton so the ~30MB core is only fetched and instantiated once
// per page load, no matter how many videos/audio files get compressed.
//
// Why local files, converted to blob: URLs, instead of a CDN:
//   @ffmpeg/ffmpeg's internal worker resolves its own chunk (814.ffmpeg.js)
//   relative to wherever ffmpeg.js itself was <script src>'d from — see the
//   comment in index.html. That fixes the Worker-construction crash. The
//   core (ffmpeg-core.js/.wasm) is loaded separately, *inside* that worker,
//   via fetch(); fetch() isn't subject to the same-origin Worker
//   restriction, so it would technically work from a CDN too — but this
//   project's brief asks for local-first, dependency-free assets, so both
//   pieces are vendored under ./vendor/ and converted to blob: URLs before
//   being handed to ffmpeg.load(). Blob URLs are always treated as
//   same-origin, which sidesteps any environment where fetch() of a plain
//   relative path behaves unexpectedly (e.g. some static-file hosts).

import { toBlobURL } from '../utils.js';

const CORE_JS = new URL('../../vendor/ffmpeg/core/ffmpeg-core.js', import.meta.url).href;
const CORE_WASM = new URL('../../vendor/ffmpeg/core/ffmpeg-core.wasm', import.meta.url).href;

let instance = null;
let loadingPromise = null;

export function bpsToKFlag(bitsPerSecond) {
  // ffmpeg's -b:v/-b:a "k" suffix means "×1000". Our sizing math produces
  // raw bits/second, so this divide is required before appending "k".
  // (The original project appended "k" directly to a bits/second number —
  // a 1000x error that effectively made its bitrate ceiling meaningless;
  // see js/compressors/video.js and README "Bugs fixed" for the details.)
  return Math.max(1, Math.round(bitsPerSecond / 1000)) + 'k';
}

export async function getFFmpeg(onLog) {
  if (instance) return instance;
  if (loadingPromise) return loadingPromise;

  loadingPromise = (async () => {
    if (!window.FFmpegWASM || !window.FFmpegWASM.FFmpeg) {
      throw new Error('FFmpeg failed to load from ./vendor/ffmpeg/ffmpeg.js — check that the file exists and the browser console for a blocked-script error.');
    }
    const ffmpeg = new window.FFmpegWASM.FFmpeg();
    if (onLog) ffmpeg.on('log', (d) => onLog(d.message));

    const [coreURL, wasmURL] = await Promise.all([
      toBlobURL(CORE_JS, 'text/javascript'),
      toBlobURL(CORE_WASM, 'application/wasm'),
    ]);

    await ffmpeg.load({ coreURL, wasmURL });
    instance = ffmpeg;
    return ffmpeg;
  })();

  try {
    return await loadingPromise;
  } catch (err) {
    loadingPromise = null; // allow a retry on the next call instead of caching a permanent failure
    throw err;
  }
}

// Virtual-filesystem + worker cleanup shared by video.js and audio.js so a
// failed job never leaves stray files inside FFmpeg's MEMFS.
export async function cleanupFiles(ffmpeg, names) {
  for (const n of names) {
    try { await ffmpeg.deleteFile(n); } catch { /* already gone — fine */ }
  }
}
