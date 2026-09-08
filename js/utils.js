// Small, dependency-free helpers shared by every module. Nothing in this
// file touches the network except toBlobURL(), which only ever reads a
// same-origin file this app ships with (see ffmpeg-engine.js for why that
// matters).

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

// Reliable download: object URLs must not be revoked before the browser has
// actually started the download, but leaving them forever leaks memory for
// a session that compresses many files. A short delay covers every browser
// that matters without keeping large blobs alive indefinitely.
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
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
