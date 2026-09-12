import { detectCategory, parseTargetBytes, downloadBlob, uid, formatBytes, checkMediaFileSize } from './utils.js';
import { compressImage } from './compressors/image.js';
import { compressVideo } from './compressors/video.js';
import { compressAudio } from './compressors/audio.js';
import { compressPdf } from './compressors/pdf.js';
import { compressGeneric } from './compressors/generic.js';
import { createFileCard, setBusy, setProgress, showError, showResult, showToast } from './ui.js';
import { initMenu } from './menu.js';
import { isNativeApp } from './capacitor-bridge.js';
import { ANDROID_APP_DOWNLOAD_URL } from './app-config.js';

initMenu();

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

    // Catch a hopeless video/audio size upfront — right when the file is
    // added — instead of only discovering it after the user taps Compress
    // and waits. This is what should have caught the 3GB+ video that
    // previously hung the tab.
    if (category === 'video' || category === 'audio') {
      const check = checkMediaFileSize(file);
      if (!check.ok) {
        const appNote = !isNativeApp() ? ` The installed Android app has a somewhat higher limit (its own dedicated memory instead of sharing a browser tab's budget) — worth trying there, though very large files like this may still be too big for any in-device engine.` : '';
        showError(el, check.message + appNote);
      } else if (check.warning) {
        showToast(check.warning, 'danger');
      }
    }
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
// fileInput.value is cleared both before opening and (via finally) after
// handling a selection — if it were ever left set to a previous file, some
// browsers won't fire 'change' again for that same file, which looks
// exactly like "tapping the drop zone stopped doing anything."
function openPicker() {
  fileInput.value = '';
  fileInput.click();
}
dropZone.addEventListener('click', (e) => {
  // The drop zone is a <label for=fileInput>, so the browser/WebView can
  // open the native chooser without relying only on a programmatic click.
  // Keep this handler only as a safe fallback for browsers that do not
  // activate the label correctly.
  if (e.target === dropZone) {
    e.preventDefault();
    openPicker();
  }
});
dropZone.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker(); } });
fileInput.addEventListener('change', () => {
  try {
    addFiles(fileInput.files);
  } catch (err) {
    console.error(err);
    showToast('Could not read the selected file(s): ' + (err.message || err), 'danger');
  } finally {
    fileInput.value = '';
  }
});

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
    const nameInput = cardEl.querySelector('.js-filename');
    const ext = actionEl.dataset.ext || '';
    const finalName = nameInput && nameInput.value.trim() ? nameInput.value.trim() + ext : state.filename;

    // Native Android saves use device storage. Ask the user once before the
    // first native save so there is explicit consent and no hidden storage
    // use. This is an in-app consent notice, not a broad Android storage
    // permission: modern scoped Documents storage does not need that legacy
    // permission.
    if (isNativeApp()) {
      const consentKey = 'dd_compressor_native_storage_consent_v1';
      const consented = localStorage.getItem(consentKey) === 'granted';
      if (!consented) {
        const ok = window.confirm(
          `Save compressed file to your device?\n\n` +
          `DD Compressor will create a copy in the app's Documents storage. ` +
          `This uses some space on your device. Your file is not uploaded to our server.\n\n` +
          `Continue saving?`
        );
        if (!ok) {
          showToast('Save cancelled. No file was written to device storage.');
          return;
        }
        localStorage.setItem(consentKey, 'granted');
      }
    }

    try {
      const result = await downloadBlob(state.blob, finalName);
      if (result.savedNatively) {
        showToast(`Saved "${finalName}" to DD Compressor storage — the share sheet can move it elsewhere.`);
      }
    } catch (err) {
      showToast(err.message || 'Could not download the file.', 'danger');
    }
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

