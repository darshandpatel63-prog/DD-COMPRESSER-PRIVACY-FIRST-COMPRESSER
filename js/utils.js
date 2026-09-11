// Small, dependency-free helpers shared by every module. Nothing in this
// file touches the network except toBlobURL(), which only ever reads a
// same-origin file this app ships with (see ffmpeg-engine.js for why that
// matters).

import { isNativeApp, isNativeSaveAvailable, saveNative } from './capacitor-bridge.js';

export function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return '—';
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let val = bytes / 1024;
  let i = 0;
  while (val >= 1024 && i < units.length - 1) {
    val /= 1024;
    i++;
  }
  return `${val < 10 ? val.toFixed(2) : val < 100 ? val.toFixed(1) : Math.round(val)} ${units[i]}`;
}

const UNIT_MULT = { KB: 1024, MB: 1024 ** 2, GB: 1024 ** 3, TB: 1024 ** 4 };

export function parseTargetBytes(value, unit) {
  const n = Number(value);
  if (!Number.isFinite(n) || n <= 0) return null;
  return Math.round(n * (UNIT_MULT[unit] || UNIT_MULT.KB));
}

export function clamp(n, lo, hi) {
  return Math.max(lo, Math.min(hi, n));
}

export function getExtension(filename) {
  const parts = filename.toLowerCase().split('.');
  return parts.length > 1 ? parts.pop() : '';
}

export function baseName(filename) {
  const idx = filename.lastIndexOf('.');
  return idx > 0 ? filename.slice(0, idx) : filename;
}

export function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

const IMAGE_EXT = new Set(['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp']);
const VIDEO_EXT = new Set(['mp4', 'mov', 'webm', 'avi', 'mkv', 'm4v', 'mpeg', 'mpg', '3gp', 'ogv']);
const AUDIO_EXT = new Set(['mp3', 'wav', 'm4a', 'aac', 'ogg', 'opus', 'flac', 'wma']);

// File.type is trusted first (it comes from the OS/browser's own sniffing),
// the extension is only a fallback for the files with no/odd MIME type that
// browsers occasionally hand back for local files.
export function detectCategory(file) {
  const ext = getExtension(file.name);
  if (file.type.startsWith('image/') || IMAGE_EXT.has(ext)) return 'image';
  if (file.type === 'application/pdf' || ext === 'pdf') return 'pdf';
  if (file.type.startsWith('video/') || VIDEO_EXT.has(ext)) return 'video';
  if (file.type.startsWith('audio/') || AUDIO_EXT.has(ext)) return 'audio';
  return 'other';
}

// Fetches a same-origin file this app ships with and hands back a blob: URL.
// Used for the FFmpeg core (see ffmpeg-engine.js). Deliberately does NOT
// accept arbitrary/remote URLs — see the allow-list check below — because
// this helper exists to load OUR OWN local assets, never a user's file and
// never a third-party origin.
export async function toBlobURL(localUrl, mimeType) {
  const resolved = new URL(localUrl, document.baseURI);
  if (resolved.origin !== location.origin) {
    throw new Error('toBlobURL only accepts same-origin local assets, refusing: ' + resolved.href);
  }
  const res = await fetch(resolved.href);
  if (!res.ok) throw new Error(`Could not load local asset: ${localUrl}`);
  const buf = await res.arrayBuffer();
  return URL.createObjectURL(new Blob([buf], { type: mimeType }));
}

// Same idea, but reassembles a file that was split into several parts
// before being committed to the repo. ffmpeg-core.wasm is ~32MB — over
// GitHub's 25MB web-upload limit, and over what phone-based editors like
// Spck/Acode can handle — so it ships as ordered byte-range parts and gets
// concatenated back into one buffer here. The result is byte-identical to
// the original single .wasm (verified with a checksum before this shipped).
// Order matters and is preserved: Promise.all() resolves in the same order
// the input array was given, regardless of which fetch finishes first.
export async function toBlobURLFromParts(localUrls, mimeType) {
  const buffers = await Promise.all(
    localUrls.map(async (u) => {
      const resolved = new URL(u, document.baseURI);
      if (resolved.origin !== location.origin) {
        throw new Error('toBlobURLFromParts only accepts same-origin local assets, refusing: ' + resolved.href);
      }
      const res = await fetch(resolved.href);
      if (!res.ok) throw new Error(`Could not load local asset part: ${u}`);
      return res.arrayBuffer();
    })
  );
  return URL.createObjectURL(new Blob(buffers, { type: mimeType }));
}

// Reliable download. In a normal browser this is the classic blob: URL +
// hidden <a download> trick. Inside the installed Android app (a Capacitor
// WebView), that trick has nothing to hook into — a bare WebView has no
// download manager of its own, which is exactly why the button did
// nothing when tested in the app. When running natively AND the
// Filesystem plugin is available, this saves through Capacitor instead;
// see js/capacitor-bridge.js for the full explanation and what still
// needs to be added on the native project side for that path to activate.
export async function downloadBlob(blob, filename) {
  if (isNativeApp()) {
    if (isNativeSaveAvailable()) {
      try {
        const result = await saveNative(blob, filename);
        return { savedNatively: true, uri: result.uri };
      } catch (err) {
        throw new Error('Could not save the file: ' + (err.message || err));
      }
    }
    throw new Error('Saving files isn\u2019t enabled in this app build yet (the Filesystem plugin hasn\u2019t been added to the Android project). Ask the developer to update the app, or open this same site in your phone\u2019s regular browser to download normally.');
  }
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
  return { savedNatively: false };
}

// Cheap, good-enough duration probe using a real <video>/<audio> element
// instead of firing up FFmpeg just to ask "how long is this" — avoids
// loading the ~30MB WASM core for files that turn out to be unsupported,
// and is much faster than asking FFmpeg to parse the file first.
export function probeMediaDuration(file, kind) {
  return new Promise((resolve, reject) => {
    const el = document.createElement(kind === 'audio' ? 'audio' : 'video');
    el.preload = 'metadata';
    const url = URL.createObjectURL(file);
    const cleanup = () => URL.revokeObjectURL(url);
    el.onloadedmetadata = () => {
      const d = el.duration;
      cleanup();
      if (!Number.isFinite(d) || d <= 0) reject(new Error('Could not read duration'));
      else resolve(d);
    };
    el.onerror = () => {
      cleanup();
      reject(new Error('Could not read media metadata'));
    };
    el.src = url;
  });
}

export function extensionForMime(mime) {
  const map = {
    'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp',
    'video/mp4': 'mp4', 'video/webm': 'webm',
    'audio/mpeg': 'mp3', 'audio/aac': 'aac', 'audio/ogg': 'ogg', 'audio/wav': 'wav', 'audio/x-wav': 'wav',
    'application/pdf': 'pdf', 'application/gzip': 'gz',
  };
  return map[mime] || 'bin';
}

// FFmpeg's WASM build is WASM32, which has a hard 4GiB address-space
// ceiling no matter how much RAM the device has — and that space has to
// hold the input file, FFmpeg's own working/decode memory, AND the output
// file all at once. This is a property of running a 32-bit WebAssembly
// engine, full stop — it applies exactly as much inside the installed
// Android app as it does in a browser tab, because the installed app is
// ALSO a WebView running the SAME JavaScript/WebAssembly engine (Capacitor
// does not replace or bypass it). Packaging this project as an .apk does
// not, and cannot, remove this ceiling; only a genuinely different,
// natively-compiled engine (real Android code calling a native FFmpeg
// library, not this project) could do that, and that would be a different
// app, not this one wrapped differently.
//
// What the installed app DOES get, honestly: its own dedicated process
// instead of one tab sharing a browser's overall memory budget with
// however many other tabs are open, which in practice tends to allow a
// somewhat higher ceiling before the OS intervenes. "Somewhat higher," not
// "unlimited" — the 4GB WASM32 wall is still there underneath either way.
export const MEDIA_SIZE_LIMITS = {
  get HARD_MAX_BYTES() { return (isNativeApp() ? 3 : 1.75) * 1024 ** 3; },
  get WARN_ABOVE_BYTES() { return (isNativeApp() ? 1 : 0.6) * 1024 ** 3; },
};

export function checkMediaFileSize(file) {
  if (file.size > MEDIA_SIZE_LIMITS.HARD_MAX_BYTES) {
    return {
      ok: false,
      message: `This file is ${formatBytes(file.size)} — over the ${formatBytes(MEDIA_SIZE_LIMITS.HARD_MAX_BYTES)} limit this engine can reliably hold in memory at once (FFmpeg's WebAssembly build has a hard 4GB address-space ceiling that has to fit the input, working memory, and output together — true whether this is running in a browser tab or the installed app, since both use the same underlying engine). Trim or split this file first, or compress it with a native desktop app instead.`,
    };
  }
  if (file.size > MEDIA_SIZE_LIMITS.WARN_ABOVE_BYTES) {
    return {
      ok: true,
      warning: `${formatBytes(file.size)} is large for in-browser processing — this may be slow and, on a phone with limited memory, could fail partway through. Keep this tab/app in the foreground while it runs.`,
    };
  }
  return { ok: true };
}
