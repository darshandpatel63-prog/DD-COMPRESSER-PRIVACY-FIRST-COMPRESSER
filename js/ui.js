import { formatBytes } from './utils.js';

const ICONS = {
  image: '<path d="M4 5h16v14H4z"/><circle cx="9" cy="10" r="1.6"/><path d="M4 16l5-5 4 4 3-3 4 4"/>',
  video: '<path d="M4 6h12v12H4z"/><path d="M16 10l4-2.5v9L16 14"/>',
  audio: '<path d="M9 18V6l10-2v12"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="16.5" cy="16" r="2.5"/>',
  pdf: '<path d="M6 3h9l5 5v13H6z"/><path d="M15 3v5h5"/><path d="M8.5 12.5h1.2a1.3 1.3 0 0 1 0 2.6H8.5zM12.3 12.5v4.6M12.3 12.5h1a2.3 2.3 0 0 1 0 4.6h-1zM17 12.5h-2v4.6M15 14.8h1.6"/>',
  other: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/>',
  trash: '<path d="M4 6h16"/><path d="M9 6V4h6v2"/><path d="M6 6l1 14h10l1-14"/>',
  download: '<path d="M12 4v11"/><path d="M7 11l5 5 5-5"/><path d="M5 20h14"/>',
};

function icon(name, cls = '') {
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" class="${cls}" aria-hidden="true">${ICONS[name] || ICONS.other}</svg>`;
}

const FORMAT_OPTIONS = {
  image: [['auto', 'Auto'], ['jpeg', 'JPG'], ['png', 'PNG'], ['webp', 'WebP']],
  video: [['mp4', 'MP4 · H.264'], ['webm', 'WebM · VP9 (slower)']],
  audio: [['mp3', 'MP3'], ['aac', 'AAC / M4A'], ['ogg', 'OGG · Opus'], ['wav', 'WAV']],
};

export function createFileCard(state) {
  const el = document.createElement('div');
  el.className = 'file-card';
  el.dataset.id = state.id;

  const top = document.createElement('div');
  top.className = 'file-card-top';

  const typeIcon = document.createElement('div');
  typeIcon.className = 'file-type-icon';
  typeIcon.innerHTML = icon(state.category === 'pdf' ? 'pdf' : state.category);
  top.appendChild(typeIcon);

  const meta = document.createElement('div');
  meta.className = 'file-meta';
  const nameEl = document.createElement('div');
  nameEl.className = 'file-name';
  nameEl.textContent = state.file.name; // textContent only — never innerHTML with a user-controlled filename
  const subEl = document.createElement('div');
  subEl.className = 'file-sub';
  subEl.textContent = `${formatBytes(state.file.size)} · ${state.category}`;
  meta.appendChild(nameEl);
  meta.appendChild(subEl);
  top.appendChild(meta);

  const actions = document.createElement('div');
  actions.className = 'file-card-actions';
  const removeBtn = document.createElement('button');
  removeBtn.className = 'btn-icon';
  removeBtn.type = 'button';
  removeBtn.setAttribute('aria-label', 'Remove file');
  removeBtn.dataset.action = 'remove';
  removeBtn.innerHTML = icon('trash');
  actions.appendChild(removeBtn);
  top.appendChild(actions);

  el.appendChild(top);

  if (state.category !== 'other') {
    const settings = document.createElement('div');
    settings.className = 'file-card-settings';

    const targetField = document.createElement('div');
    targetField.className = 'field';
    targetField.innerHTML = `
      <label>Target</label>
      <input type="number" min="1" step="1" class="js-target-value" value="${state.targetValue}">
      <select class="js-target-unit" aria-label="Target unit">
        <option value="KB" ${state.targetUnit === 'KB' ? 'selected' : ''}>KB</option>
        <option value="MB" ${state.targetUnit === 'MB' ? 'selected' : ''}>MB</option>
        <option value="GB" ${state.targetUnit === 'GB' ? 'selected' : ''}>GB</option>
      </select>`;
    settings.appendChild(targetField);

    if (FORMAT_OPTIONS[state.category]) {
      const formatField = document.createElement('div');
      formatField.className = 'field';
      const options = FORMAT_OPTIONS[state.category]
        .map(([val, label]) => `<option value="${val}" ${state.format === val ? 'selected' : ''}>${label}</option>`)
        .join('');
      formatField.innerHTML = `<label>Format</label><select class="js-format">${options}</select>`;
      settings.appendChild(formatField);
    }

    const compressBtn = document.createElement('button');
    compressBtn.type = 'button';
    compressBtn.className = 'btn btn-primary btn-sm';
    compressBtn.dataset.action = 'compress';
    compressBtn.style.marginLeft = 'auto';
    compressBtn.textContent = 'Compress';
    settings.appendChild(compressBtn);

    el.appendChild(settings);
  } else {
    const settings = document.createElement('div');
    settings.className = 'file-card-settings';
    const note = document.createElement('div');
    note.className = 'field';
    note.textContent = "No preview format for this file type — DD Compressor will gzip it losslessly.";
    settings.appendChild(note);
    const compressBtn = document.createElement('button');
    compressBtn.type = 'button';
    compressBtn.className = 'btn btn-primary btn-sm';
    compressBtn.dataset.action = 'compress';
    compressBtn.style.marginLeft = 'auto';
    compressBtn.textContent = 'Compress';
    settings.appendChild(compressBtn);
    el.appendChild(settings);
  }

  const progressTrack = document.createElement('div');
  progressTrack.className = 'progress-track';
  progressTrack.innerHTML = '<div class="progress-fill"></div>';
  el.appendChild(progressTrack);

  const statusLine = document.createElement('div');
  statusLine.className = 'status-line';
  statusLine.innerHTML = '<span class="spinner"></span><span class="status-text"></span>';
  el.appendChild(statusLine);

  const errorLine = document.createElement('div');
  errorLine.className = 'error-line';
  el.appendChild(errorLine);

  const result = document.createElement('div');
  result.className = 'result';
  el.appendChild(result);

  return el;
}

export function setBusy(cardEl, busy, label) {
  cardEl.querySelector('.progress-track').classList.toggle('active', busy);
  const statusLine = cardEl.querySelector('.status-line');
  statusLine.classList.toggle('active', busy);
  if (label) statusLine.querySelector('.status-text').textContent = label;
  const btn = cardEl.querySelector('[data-action="compress"]');
  if (btn) btn.disabled = busy;
  cardEl.querySelector('.error-line').classList.remove('active');
}

export function setProgress(cardEl, pct, label) {
  cardEl.querySelector('.progress-fill').style.width = `${Math.max(0, Math.min(100, pct))}%`;
  if (label) cardEl.querySelector('.status-text').textContent = label;
}

export function showError(cardEl, message) {
  setBusy(cardEl, false);
  cardEl.classList.add('error');
  const errLine = cardEl.querySelector('.error-line');
  errLine.textContent = message; // textContent — message may echo back parts of a filename
  errLine.classList.add('active');
}

export function showResult(cardEl, { originalBytes, targetBytes, outputBytes, status, detail, hitTarget, filename, blob, kept }) {
  setBusy(cardEl, false);
  cardEl.classList.remove('error');
  const result = cardEl.querySelector('.result');
  result.innerHTML = '';
  result.classList.add('active');

  const rows = [
    ['Original', formatBytes(originalBytes), false],
    ['Target', formatBytes(targetBytes), false],
    [kept ? 'Output' : 'Output', formatBytes(outputBytes), outputBytes < originalBytes],
  ];
  if (originalBytes > 0) {
    const reduction = Math.max(0, Math.round((1 - outputBytes / originalBytes) * 1000) / 10);
    rows.push(['Reduction', `${reduction}%`, reduction > 0]);
  }

  for (const [k, v, win] of rows) {
    const row = document.createElement('div');
    row.className = 'result-row' + (win ? ' win' : '');
    row.innerHTML = `<span class="k"></span><span class="v"></span>`;
    row.querySelector('.k').textContent = k;
    row.querySelector('.v').textContent = v;
    result.appendChild(row);
  }

  const statusRow = document.createElement('div');
  statusRow.className = 'result-row status';
  statusRow.innerHTML = `<span class="k"></span><span class="v"></span>`;
  statusRow.querySelector('.k').textContent = 'Status';
  statusRow.querySelector('.v').textContent = detail ? `${status} (${detail})` : status;
  result.appendChild(statusRow);

  if (blob && filename) {
    const lastDot = filename.lastIndexOf('.');
    const baseName = lastDot > 0 ? filename.slice(0, lastDot) : filename;
    const ext = lastDot > 0 ? filename.slice(lastDot) : '';

    const renameRow = document.createElement('div');
    renameRow.className = 'result-rename';
    const nameInput = document.createElement('input');
    nameInput.type = 'text';
    nameInput.className = 'js-filename';
    nameInput.value = baseName;
    nameInput.setAttribute('aria-label', 'File name before downloading');
    nameInput.spellcheck = false;
    const extSpan = document.createElement('span');
    extSpan.className = 'result-rename-ext';
    extSpan.textContent = ext;
    renameRow.appendChild(nameInput);
    renameRow.appendChild(extSpan);
    result.appendChild(renameRow);

    const actionsRow = document.createElement('div');
    actionsRow.className = 'result-actions';
    const dlBtn = document.createElement('button');
    dlBtn.type = 'button';
    dlBtn.className = 'btn btn-primary btn-sm';
    dlBtn.dataset.action = 'download';
    dlBtn.dataset.ext = ext;
    dlBtn.innerHTML = icon('download') + ' Download';
    actionsRow.appendChild(dlBtn);
    result.appendChild(actionsRow);
  }
}

export function showToast(message, kind = 'info') {
  const region = document.getElementById('toast-region');
  const toast = document.createElement('div');
  toast.className = 'toast' + (kind === 'danger' ? ' danger' : '');
  toast.textContent = message;
  region.appendChild(toast);
  setTimeout(() => toast.remove(), 6000);
}
