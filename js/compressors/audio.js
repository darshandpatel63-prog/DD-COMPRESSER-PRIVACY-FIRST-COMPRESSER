// Audio compression. Same local FFmpeg core as video.js (getFFmpeg() is a
// singleton, so if a video was compressed earlier in the session this is
// instant — no second ~30MB load).
//
// WAV is uncompressed PCM, so it has no "quality" or "bitrate" to turn
// down — the only levers are sample rate and channel count. Asking for a
// WAV under a small target with default settings would just produce a
// truncated or corrupt file, so WAV takes a different, DSP-appropriate
// path instead of pretending a bitrate flag will help.

import { probeMediaDuration, baseName, uid, checkMediaFileSize } from '../utils.js';
import { getFFmpeg, bpsToKFlag, cleanupFiles } from './ffmpeg-engine.js';

const CODEC_BY_FORMAT = {
  mp3: { codec: 'libmp3lame', mime: 'audio/mpeg', min: 32_000, max: 320_000 },
  aac: { codec: 'aac', mime: 'audio/aac', min: 32_000, max: 256_000 },
  ogg: { codec: 'libopus', mime: 'audio/ogg', min: 16_000, max: 256_000 },
};

export async function compressAudio(file, { targetBytes, format }, onProgress) {
  const sizeCheck = checkMediaFileSize(file);
  if (!sizeCheck.ok) throw new Error(sizeCheck.message);

  if (targetBytes >= file.size) {
    return { useOriginal: true, status: 'Already at or under your target size — left unchanged.' };
  }

  onProgress(4, 'Reading audio length…');
  const duration = await probeMediaDuration(file, 'audio').catch(() => null);
  if (!duration) throw new Error('Could not read this file\u2019s duration/metadata — it may be corrupted or an unsupported container.');

  onProgress(8, 'Loading local FFmpeg core…');
  const ffmpeg = await getFFmpeg();
  ffmpeg.on('progress', ({ progress }) => {
    if (Number.isFinite(progress)) onProgress(12 + Math.min(80, Math.round(progress * 80)), 'Encoding…');
  });

  const inputName = `in_${uid()}.${file.name.split('.').pop() || 'bin'}`;
  const outputName = `out_${uid()}.${format}`;
  await ffmpeg.writeFile(inputName, new Uint8Array(await file.arrayBuffer()));

  let args, detail;
  const targetBps = (targetBytes * 8 * 0.92) / duration;

  if (format === 'wav') {
    // No bitrate lever for PCM — solve for a sample rate instead.
    // bytes/sec for 16-bit PCM = sampleRate * channels * 2
    const mono = targetBps < 700_000; // below roughly a 44.1kHz/16-bit stereo rate, drop to mono first
    const channels = mono ? 1 : 2;
    let sampleRate = Math.round(targetBps / 8 / (channels * 2));
    sampleRate = Math.max(8000, Math.min(48000, sampleRate));
    args = ['-i', inputName, '-map_metadata', '-1', '-c:a', 'pcm_s16le', '-ar', String(sampleRate), '-ac', String(channels), '-y', outputName];
    detail = `${sampleRate.toLocaleString()} Hz, ${channels === 1 ? 'mono' : 'stereo'}`;
  } else {
    const codecInfo = CODEC_BY_FORMAT[format] || CODEC_BY_FORMAT.mp3;
    const bps = Math.max(codecInfo.min, Math.min(codecInfo.max, targetBps));
    args = ['-i', inputName, '-map_metadata', '-1', '-c:a', codecInfo.codec, '-b:a', bpsToKFlag(bps), '-y', outputName];
    detail = `${Math.round(bps / 1000)} kbps`;
  }

  let data;
  try {
    const ret = await ffmpeg.exec(args);
    if (ret !== 0) throw new Error(`FFmpeg exited with code ${ret}`);
    data = await ffmpeg.readFile(outputName);
  } catch (err) {
    await cleanupFiles(ffmpeg, [inputName, outputName]);
    throw new Error('FFmpeg could not compress this audio file: ' + err.message);
  }
  await cleanupFiles(ffmpeg, [inputName, outputName]);

  const mime = format === 'wav' ? 'audio/wav' : (CODEC_BY_FORMAT[format] || CODEC_BY_FORMAT.mp3).mime;
  const blob = new Blob([data.buffer], { type: mime });

  if (blob.size >= file.size) {
    return {
      useOriginal: false, blob, filename: `${baseName(file.name)}-compressed.${format}`,
      hitTarget: false, detail,
      status: 'This audio is already efficiently encoded — the recompressed version came out larger, so this is the smallest we could get without noticeably worse quality.',
    };
  }

  return {
    useOriginal: false,
    blob,
    filename: `${baseName(file.name)}-compressed.${format}`,
    hitTarget: blob.size <= targetBytes,
    detail,
    status: blob.size <= targetBytes ? 'Target reached.' : 'Landed close to target but slightly above it — showing the actual result rather than a false guarantee.',
  };
}