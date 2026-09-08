// For everything that isn't image/video/audio/pdf: lossless gzip using the
// browser's own native CompressionStream, with zero external library. This
// genuinely helps text-like files (JSON, logs, source code, CSV) and is
// honest about not helping already-compressed ones (zip, most binaries).

import { baseName } from '../utils.js';

export async function compressGeneric(file, _opts, onProgress) {
  if (typeof CompressionStream === 'undefined') {
    throw new Error('This browser does not support the native CompressionStream API needed to gzip this file.');
  }
  onProgress(15, 'Compressing…');
  const cs = new CompressionStream('gzip');
  const compressedStream = file.stream().pipeThrough(cs);
  const blob = await new Response(compressedStream).blob();
  onProgress(90, 'Finishing…');

  if (blob.size >= file.size) {
    return {
      useOriginal: true,
      status: 'This file is already compact (or already compressed) — gzip made it larger, so the original is kept as-is.',
    };
  }

  return {
    useOriginal: false,
    blob,
    filename: `${file.name}.gz`,
    hitTarget: true,
    detail: 'lossless gzip',
    status: 'Compressed losslessly with gzip — nothing about the content was changed, only how it\u2019s packed.',
  };
}
