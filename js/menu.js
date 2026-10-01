import { isNativeApp } from './capacitor-bridge.js';
import { ANDROID_APP_DOWNLOAD_URL } from './app-config.js';

const HOW_PANEL = `
  <h4>What happens when you compress a file</h4>
  <p>Your selected file is processed on your own phone or computer. The core compressor does not send the file to a DD Compressor compression server.</p>
  <p>1. Choose a file.<br>2. Set a target.<br>3. Compress locally.<br>4. Review and save the result.</p>
  <h4>Why it's private</h4>
  <p>The core workflow is designed around local processing. Turn off Wi-Fi and mobile data and the core compression engines can still work after the app has loaded.</p>
  <h4>Why results can vary</h4>
  <p>Some files are already highly compressed. In those cases the app may keep the original rather than pretend that an unsafe reduction is a success.</p>`;

function initTheme() {
  const root = document.documentElement;
  const button = document.getElementById('themeBtn');
  if (!button) return;

  const saved = localStorage.getItem('dd-compressor-theme');
  const preferred = window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  root.dataset.theme = saved || preferred;

  const syncButton = () => {
    const light = root.dataset.theme === 'light';
    button.textContent = light ? '☾' : '☀';
    button.setAttribute('aria-pressed', String(light));
    button.title = light ? 'Switch to dark theme' : 'Switch to light theme';
    button.setAttribute('aria-label', button.title);
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = light ? '#f4f7fc' : '#080b14';
  };

  button.addEventListener('click', () => {
    root.dataset.theme = root.dataset.theme === 'light' ? 'dark' : 'light';
    localStorage.setItem('dd-compressor-theme', root.dataset.theme);
    syncButton();
  });
  syncButton();
}

export function initMenu() {
  initTheme();

  const menuBtn = document.getElementById('menuBtn');
  const overlay = document.getElementById('menuOverlay');
  const closeBtn = document.getElementById('menuCloseBtn');
  const body = document.getElementById('menuPanelBody');
  const getAppLink = document.getElementById('menuGetAppLink');

  if (!menuBtn || !overlay) return;

  if (getAppLink) {
    getAppLink.href = ANDROID_APP_DOWNLOAD_URL;
    if (isNativeApp()) getAppLink.hidden = true;
  }

  function open() {
    overlay.hidden = false;
    menuBtn.setAttribute('aria-expanded', 'true');
  }

  function close() {
    overlay.hidden = true;
    menuBtn.setAttribute('aria-expanded', 'false');
    if (body) {
      body.hidden = true;
      body.innerHTML = '';
    }
  }

  menuBtn.addEventListener('click', open);
  closeBtn?.addEventListener('click', close);
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) close();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !overlay.hidden) close();
  });

  overlay.querySelectorAll('[data-panel]').forEach((button) => {
    button.addEventListener('click', () => {
      if (!body) return;
      body.innerHTML = HOW_PANEL;
      body.hidden = false;
      body.scrollIntoView({ block: 'nearest' });
    });
  });
}
