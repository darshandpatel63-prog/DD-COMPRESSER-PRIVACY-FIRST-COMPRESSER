// Thin promise wrapper around js/workers/image-worker.js. All the actual
// compression logic lives in the worker so large photos never block the
// UI thread; this file just manages the message protocol and worker
// lifecycle (one worker per compression call, terminated when done, so a
// crashed/huge job can't hold memory hostage across files).

import { baseName, extensionForMime } from '../utils.js';

export function compressImage(file, { targetBytes, format }, onProgress) {
  return new Promise(async (resolve, reject) => {
    let worker;
    try {
      worker = new Worker('./js/workers/image-worker.js');
    } catch (err) {
      reject(new Error('Could not start the image worker: ' + err.message));
      return;
    }

    const id = 1;
    const workerUrl = new URL('../workers/image-worker.js', import.meta.url);
    worker.onmessage = (e) => {
      const msg = e.data;
      if (msg.id !== id) return;
      if (msg.type === 'progress') {
        onProgress(msg.pct);
        return;
      }
      worker.terminate();
      if (msg.type === 'error') {
        reject(new Error(msg.message));
        return;
      }
      const r = msg.result;
      if (r.useOriginal) {
        resolve({ useOriginal: true, status: r.status });
        return;
      }
      const ext = extensionForMime(r.mime);
      const blob = new Blob([r.buffer], { type: r.mime });
      resolve({
        useOriginal: false,
        blob,
        filename: `${baseName(file.name)}-compressed.${ext}`,
        hitTarget: r.hitTarget,
        status: r.status,
        detail: r.quality != null
          ? `${Math.round(r.quality * 100)}% quality${r.scale < 1 ? `, ${Math.round(r.scale * 100)}% size` : ''}`
          : (r.scale < 1 ? `${Math.round(r.scale * 100)}% size` : null),
      });
    };

    worker.onerror = (err) => {
      worker.terminate();
      reject(new Error('Image worker crashed: ' + (err.message || 'unknown error')));
    };

    const arrayBuffer = await file.arrayBuffer();
    worker.postMessage(
      { id, payload: { arrayBuffer, mime: file.type || `image/${file.name.split('.').pop()}`, name: file.name, targetBytes, format, originalBytes: file.size } },
      [arrayBuffer]
    );
  });
}
