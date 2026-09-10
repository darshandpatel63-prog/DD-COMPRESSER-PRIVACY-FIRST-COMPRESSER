// Video compression. Bitrate is derived from the target size and the
// clip's own duration (measured once, cheaply, via a <video> element — see
// utils.probeMediaDuration — rather than asking FFmpeg to parse the file
// just to answer that question).
//
// The original project computed this same "target bytes -> bits/second"
// value correctly, then appended ffmpeg's "k" (×1000) suffix directly to
// the raw bits/second number when building the -b:v flag — e.g. asking for
// "1333333k" instead of "1333k", a target 1000x too high. That's the
// bpsToKFlag() divide-by-1000 below; skipping it reproduces that bug.

import { probeMediaDuration, baseName, uid, checkMediaFileSize } from '../utils.js';
import { getFFmpeg, bpsToKFlag, cleanupFiles } from './ffmpeg-engine.js';

const MIN_VIDEO_BPS = 40_000;
const MAX_VIDEO_BPS = 12_000_000;
const SAFETY_MARGIN = 0.92; // leaves headroom for container/mux overhead so output tends to land at-or-under target, not over

function pickAudioBps(totalBps) {
  if (totalBps < 160_000) return 48_000;
  if (totalBps < 400_000) return 64_000;
  return 96_000;
}

// A simple resolution ladder: very low bitrates look far better at a
// smaller frame size than stretched thin over a large one.
function pickHeightCap(videoBps, currentHeight) {
  if (videoBps < 80_000) return Math.min(currentHeight, 240);
  if (videoBps < 150_000) return Math.min(currentHeight, 360);
  if (videoBps < 300_000) return Math.min(currentHeight, 480);
  return currentHeight;
}

export async function compressVideo(file, { targetBytes, format }, onProgress) {
  const sizeCheck = checkMediaFileSize(file);
  if (!sizeCheck.ok) throw new Error(sizeCheck.message);

  if (targetBytes >= file.size) {
    return { useOriginal: true, status: 'Already at or under your target size — left unchanged.' };
  }

  onProgress(3, 'Reading video length…');
  const duration = await probeMediaDuration(file, 'video').catch(() => null);
  if (!duration) throw new Error('Could not read this video\u2019s duration/metadata — it may be corrupted or an unsupported container.');

  const dims = await probeDimensions(file).catch(() => ({ width: 1280, height: 720 }));

  onProgress(6, 'Loading local FFmpeg core…');
  const ffmpeg = await getFFmpeg();
  ffmpeg.on('progress', ({ progress }) => {
    if (Number.isFinite(progress)) onProgress(10 + Math.min(85, Math.round(progress * 85)), 'Encoding…');
  });

  const inputName = `in_${uid()}.${file.name.split('.').pop() || 'bin'}`;
  const isWebm = format === 'webm';
  const outputName = `out_${uid()}.${isWebm ? 'webm' : 'mp4'}`;

  await ffmpeg.writeFile(inputName, new Uint8Array(await file.arrayBuffer()));

  const totalBps = Math.max(1, (targetBytes * 8 * SAFETY_MARGIN) / duration);
  let audioBps = pickAudioBps(totalBps);
  let videoBps = Math.min(MAX_VIDEO_BPS, Math.max(MIN_VIDEO_BPS, totalBps - audioBps));
  if (videoBps === MIN_VIDEO_BPS) audioBps = Math.max(32_000, totalBps - videoBps); // extremely tight target: keep a viable audio floor by trimming further

  const heightCap = pickHeightCap(videoBps, dims.height);
  const scaleFilter = heightCap < dims.height ? ['-vf', `scale=-2:${heightCap}`] : [];

  const args = isWebm
    ? [
        '-i', inputName, '-map_metadata', '-1',
        ...scaleFilter,
        '-c:v', 'libvpx-vp9', '-b:v', bpsToKFlag(videoBps), '-maxrate', bpsToKFlag(videoBps * 1.45), '-bufsize', bpsToKFlag(videoBps * 2),
        '-row-mt', '1', '-deadline', 'realtime', '-cpu-used', '5',
        '-c:a', 'libopus', '-b:a', bpsToKFlag(audioBps),
        '-y', outputName,
      ]
    : [
        '-i', inputName, '-map_metadata', '-1',
        ...scaleFilter,
        '-c:v', 'libx264', '-preset', 'veryfast', '-b:v', bpsToKFlag(videoBps), '-maxrate', bpsToKFlag(videoBps * 1.3), '-bufsize', bpsToKFlag(videoBps * 2),
        '-pix_fmt', 'yuv420p',
        '-c:a', 'aac', '-b:a', bpsToKFlag(audioBps),
        '-movflags', '+faststart',
        '-y', outputName,
      ];

  let data;
  try {
    const ret = await ffmpeg.exec(args);
    if (ret !== 0) throw new Error(`FFmpeg exited with code ${ret}`);
    data = await ffmpeg.readFile(outputName);
  } catch (err) {
    await cleanupFiles(ffmpeg, [inputName, outputName]);
    throw new Error('FFmpeg could not compress this video: ' + err.message);
  }
  await cleanupFiles(ffmpeg, [inputName, outputName]);

  const mime = isWebm ? 'video/webm' : 'video/mp4';
  const blob = new Blob([data.buffer], { type: mime });

  if (blob.size >= file.size) {
    return {
      useOriginal: false, blob, filename: `${baseName(file.name)}-compressed.${isWebm ? 'webm' : 'mp4'}`,
      hitTarget: false, detail: `${Math.round(videoBps / 1000)} kbps video`,
      status: 'This clip is already efficiently encoded — the recompressed version came out larger, so this is the smallest we could get without noticeably worse quality.',
    };
  }

  return {
    useOriginal: false,
    blob,
    filename: `${baseName(file.name)}-compressed.${isWebm ? 'webm' : 'mp4'}`,
    hitTarget: blob.size <= targetBytes,
    detail: `${Math.round(videoBps / 1000)} kbps video + ${Math.round(audioBps / 1000)} kbps audio${heightCap < dims.height ? `, scaled to ${heightCap}p` : ''}`,
    status: blob.size <= targetBytes
      ? 'Target reached.'
      : 'Bitrate targeting got close but landed slightly above target — real footage rarely matches an estimate exactly. Showing the actual result rather than a false guarantee.',
  };
}

function probeDimensions(file) {
  return new Promise((resolve, reject) => {
    const video = document.createElement('video');
    video.preload = 'metadata';
    const url = URL.createObjectURL(file);
    video.onloadedmetadata = () => {
      const w = video.videoWidth || 1280, h = video.videoHeight || 720;
      URL.revokeObjectURL(url);
      resolve({ width: w, height: h });
    };
    video.onerror = () => { URL.revokeObjectURL(url); reject(new Error('metadata read failed')); };
    video.src = url;
  });
}
