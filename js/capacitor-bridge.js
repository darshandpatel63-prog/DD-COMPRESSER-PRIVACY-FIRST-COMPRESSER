// Why downloads don't work in the Android app (as reported) and what this
// file does about it:
//
// A plain website's "download a file" trick — creating a blob: URL and
// clicking a hidden <a download> link — relies on the BROWSER's own
// download manager. A bare Capacitor WebView has no download manager of
// its own; there is nothing on the native side listening for that click,
// so the button does nothing. This is not a bug in this project's JS, and
// it isn't fixable from JS alone — it needs a native capability the app
// doesn't currently have.
//
// The fix has two halves:
//   1. THIS FILE (already done): if the app is running as a native
//      Capacitor app AND the Filesystem plugin is available, save the
//      compressed file through it instead of the browser download trick,
//      then offer the native Share sheet so the user can immediately move
//      it wherever they want (a specific folder, WhatsApp, email, etc).
//   2. THE CAPACITOR ANDROID PROJECT (not done yet — this lives in the
//      native project, not this web repo, so it has to happen there):
//         npm install @capacitor/filesystem @capacitor/share
//         npx cap sync android
//      then rebuild the APK. Right now assets/capacitor.plugins.json in
//      the built app is empty ([]) — confirmed by inspecting the actual
//      submitted app-debug.apk — meaning neither plugin is compiled in
//      yet, so this file's native path can't do anything until that step
//      happens. Until then, isNativeSaveAvailable() below correctly
//      reports false, and the UI shows an honest message instead of a
//      button that silently does nothing.
//
// One thing this does NOT need: the classic Android storage permission
// prompt. Both plugins write through Android's modern scoped-storage APIs
// on a current targetSdkVersion (this app targets 36), which don't require
// the old broad "Allow access to photos and files" dialog for this kind of
// save — one less thing to worry about causing "Android setting
// resistance."

export function isNativeApp() {
  return !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());
}

export function isNativeSaveAvailable() {
  return isNativeApp() && !!window.Capacitor.Plugins?.Filesystem;
}

async function blobToBase64(blob) {
  // Filesystem.writeFile() takes base64 (or UTF-8 text) data. Recent
  // Capacitor versions also accept a Blob/ArrayBuffer directly — tried
  // first below, since it avoids the ~33% size/memory overhead of base64
  // for a large compressed video or PDF. This base64 path is the
  // guaranteed-compatible fallback.
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
    reader.onerror = () => reject(reader.error || new Error('Could not read the file for saving'));
    reader.readAsDataURL(blob);
  });
}

// Saves through Capacitor Filesystem + offers the native Share sheet.
// Returns { ok: true, uri } on success, or throws with a message the UI
// can show directly.
export async function saveNative(blob, filename) {
  const { Filesystem, Directory } = window.Capacitor.Plugins;
  if (!Filesystem) throw new Error('The Filesystem plugin isn\u2019t included in this app build yet.');

  // Keep every exported file inside one app-owned Documents subfolder.
  // The folder is created explicitly before writeFile(). This fixes Android
  // builds where Documents exists but the requested child path does not.
  const SAVE_FOLDER = 'DD Compressor';
  const BASE64_FALLBACK_CEILING = 350 * 1024 * 1024;

  const cleanFilename = String(filename || 'compressed-file').replace(/[\\/:*?"<>|\x00-\x1F]/g, '_').trim() || 'compressed-file';
  const savePath = `${SAVE_FOLDER}/${cleanFilename}`;

  // Explicitly create the parent directory first. Do not rely on
  // writeFile({ recursive: true }) alone: Android Filesystem versions can
  // still report "Missing parent directory" when the Documents child folder
  // has not been created yet.
  try {
    await Filesystem.mkdir({
      path: SAVE_FOLDER,
      directory: Directory.Documents,
      recursive: true,
    });
  } catch (mkdirErr) {
    // "already exists" is harmless; any other error should be surfaced.
    const msg = String(mkdirErr?.message || mkdirErr || '').toLowerCase();
    if (!msg.includes('exist') && !msg.includes('already')) {
      throw new Error(`Could not prepare device storage for saving: ${mkdirErr?.message || mkdirErr}`);
    }
  }

  let writeResult;
  try {
    // Try the direct Blob path first. If the installed plugin build requires
    // base64, the catch below uses the compatible fallback.
    writeResult = await Filesystem.writeFile({
      path: savePath,
      data: blob,
      directory: Directory.Documents,
      recursive: true,
    });
  } catch (directBlobErr) {
    if (blob.size > BASE64_FALLBACK_CEILING) {
      throw new Error(`This file is too large to save on this app version (${Math.round(blob.size / 1024 / 1024)}MB). Please update the app, or use the web version in a regular browser for files this large.`);
    }
    const base64 = await blobToBase64(blob);
    writeResult = await Filesystem.writeFile({
      path: savePath,
      data: base64,
      directory: Directory.Documents,
      recursive: true,
    });
  }

  // Offer the native share sheet if available so the user can immediately
  // move the saved file wherever they actually want it. Sharing is optional
  // and never turns a successful save into a failure.
  try {
    const { Share } = window.Capacitor.Plugins;
    if (Share && writeResult?.uri) {
      await Share.share({
        title: cleanFilename,
        url: writeResult.uri,
        dialogTitle: 'Save or share your compressed file',
      });
    }
  } catch { /* share is a bonus, not a requirement */ }

  return { ok: true, uri: writeResult?.uri, path: savePath };
}
