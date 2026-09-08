import { detectCategory, parseTargetBytes, downloadBlob, uid, formatBytes } from './utils.js';
import { compressImage } from './compressors/image.js';
import { compressVideo } from './compressors/video.js';
import { compressAudio } from './compressors/audio.js';
import { compressPdf } from './compressors/pdf.js';
import { compressGeneric } from './compressors/generic.js';
import { createFileCard, setBusy, setProgress, showError, showResult, showToast } from './ui.js';

const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');
const fileListEl = document.getElementById('fileList');
const globalTargetValue = document.getElementById('targetSize');
const globalTargetUnit = document.getElementById('targetUnit');
const compressAllBtn = document.getElementById('compressAllBtn');
const clearAllBtn = document.getElementById('clearAllBtn');
const presetRow = document.getElementById('presetRow');

/** @type {Map<string, {id:string,file:File,category:string,el:HTMLElement,targetValue:number,targetUnit:string,format:string,blob:Blob|null,filename:string|null,busy:boolean}>} */
const files = new Map();

const RUNNERS = {
  image: compressImage,
  video: compressVideo,
  audio: compressAudio,
  pdf: compressPdf,
  other: compressGeneric,
};

const DEFAULT_FORMAT = { image: 'auto', video: 'mp4', audio: 'mp3' };

function addFiles(fileListArg) {
  for (const file of fileListArg) {
    if (file.size === 0) { showToast(`Skipped "${file.name}" — the file is empty.`, 'danger'); continue; }
    const category = detectCategory(file);
    const id = uid();
    const state = {
      id, file, category,
      targetValue: Number(globalTargetValue.value) || 500,
      targetUnit: globalTargetUnit.value,
      format: DEFAULT_FORMAT[category] || null,
      blob: null, filename: null, busy: false,
    };
    const el = createFileCard(state);
    state.el = el;
    fileListEl.appendChild(el);
    files.set(id, state);
  }
  updateCompressAllState();
}

function updateCompressAllState() {
  compressAllBtn.disabled = files.size === 0 || [...files.values()].some((f) => f.busy);
}

function readCardSettings(state) {
  const targetInput = state.el.querySelector('.js-target-value');
  const unitSelect = state.el.querySelector('.js-target-unit');
  const formatSelect = state.el.querySelector('.js-format');
  if (targetInput) state.targetValue = Number(targetInput.value) || state.targetValue;
  if (unitSelect) state.targetUnit = unitSelect.value;
  if (formatSelect) state.format = formatSelect.value;
}

async function runCompression(state) {
  readCardSettings(state);
  const targetBytes = parseTargetBytes(state.targetValue, state.targetUnit);
  if (!targetBytes) {
    showToast('Enter a target size greater than zero first.', 'danger');
    return;
  }

  const runner = RUNNERS[state.category];
  state.busy = true;
  updateCompressAllState();
  setBusy(state.el, true, 'Starting…');

  try {
    const result = await runner(state.file, { targetBytes, format: state.format }, (pct, label) => setProgress(state.el, pct, label));

    if (result.useOriginal) {
      state.blob = state.file;
      state.filename = state.file.name;
      showResult(state.el, {
        originalBytes: state.file.size, targetBytes, outputBytes: state.file.size,
        status: result.status, hitTarget: true, filename: state.filename, blob: state.blob, kept: true,
      });
    } else {
      state.blob = result.blob;
      state.filename = result.filename;
      showResult(state.el, {
        originalBytes: state.file.size, targetBytes, outputBytes: result.blob.size,
        status: result.status, detail: result.detail, hitTarget: result.hitTarget,
        filename: state.filename, blob: state.blob,
      });
    }
  } catch (err) {
    console.error(err);
    showError(state.el, err.message || 'Something went wrong compressing this file.');
  } finally {
    state.busy = false;
    updateCompressAllState();
  }
}

// ---- drag & drop / picker ----
dropZone.addEventListener('click', () => fileInput.click());
dropZone.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fileInput.click(); } });
fileInput.addEventListener('change', () => { addFiles(fileInput.files); fileInput.value = ''; });

['dragenter', 'dragover'].forEach((evt) => dropZone.addEventListener(evt, (e) => { e.preventDefault(); dropZone.classList.add('drag-over'); }));
['dragleave', 'drop'].forEach((evt) => dropZone.addEventListener(evt, (e) => { e.preventDefault(); dropZone.classList.remove('drag-over'); }));
dropZone.addEventListener('drop', (e) => { if (e.dataTransfer?.files?.length) addFiles(e.dataTransfer.files); });

// ---- presets ----
presetRow.addEventListener('click', (e) => {
  const chip = e.target.closest('.chip');
  if (!chip) return;
  const kb = Number(chip.dataset.kb);
  if (kb >= 1024) { globalTargetValue.value = Math.round(kb / 1024); globalTargetUnit.value = 'MB'; }
  else { globalTargetValue.value = kb; globalTargetUnit.value = 'KB'; }
  [...presetRow.querySelectorAll('.chip')].forEach((c) => c.classList.toggle('active', c === chip));
});

// ---- per-card actions (event delegation) ----
fileListEl.addEventListener('click', async (e) => {
  const cardEl = e.target.closest('.file-card');
  if (!cardEl) return;
  const id = cardEl.dataset.id;
  const state = files.get(id);
  if (!state) return;

  const actionEl = e.target.closest('[data-action]');
  if (!actionEl) return;
  const action = actionEl.dataset.action;

  if (action === 'remove') {
    cardEl.remove();
    files.delete(id);
    updateCompressAllState();
  } else if (action === 'compress') {
    runCompression(state);
  } else if (action === 'download') {
    downloadBlob(state.blob, state.filename);
  }
});

// ---- bulk actions ----
compressAllBtn.addEventListener('click', async () => {
  const pending = [...files.values()].filter((f) => !f.busy);
  for (const state of pending) {
    await runCompression(state); // sequential on purpose — see README "Multiple files"
  }
});

clearAllBtn.addEventListener('click', () => {
  fileListEl.innerHTML = '';
  files.clear();
  updateCompressAllState();
});

// A quiet, one-time capability check so a missing feature surfaces as a
// clear message the first time it matters, instead of a cryptic console
// error deep inside a compressor.
function checkCapabilities() {
  if (typeof OffscreenCanvas === 'undefined') {
    showToast('This browser lacks OffscreenCanvas — image compression needs a recent Chrome, Firefox, Safari, or Edge.', 'danger');
  }
  if (!window.FFmpegWASM) {
    showToast('FFmpeg failed to load (./vendor/ffmpeg/ffmpeg.js) — video/audio compression will be unavailable until that\u2019s fixed.', 'danger');
  }
  if (!window.PDFLib) {
    showToast('pdf-lib failed to load (./vendor/pdf-lib/pdf-lib.min.js) — PDF compression will be unavailable until that\u2019s fixed.', 'danger');
  }
}
checkCapabilities();
