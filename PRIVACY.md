# Privacy Policy — DD Compressor

**Last updated:** September 2026

DD Compressor ("the app", "this site") is built around one rule: **your files never leave your device.** This document explains exactly what that means in practice, so you don't have to take the slogan on faith.

## What this app does with your files

When you add a photo, video, audio file, or PDF to compress:

- It is read directly into your device's memory by your browser or the app.
- It is processed entirely there — using your device's own processor, not a server.
- The result is handed back to you as a normal download or a saved file.
- Nothing about the file's content, name, size, or any other detail is sent anywhere.

There is no upload step anywhere in this app's code, because there is no server for a file to be uploaded to. This isn't a policy promise on top of the software — it's an architectural fact you (or anyone) can verify by inspecting the source code, which is public.

## What this app does NOT do

- It does not upload your files to any server, cloud storage, or third party.
- It does not use any analytics, tracking, or advertising service.
- It does not require you to create an account or sign in.
- It does not read your files for any purpose other than compressing the exact one you chose, at the exact moment you asked it to.
- It does not share, sell, or otherwise transmit anything about you or your files, because it has no mechanism to do so.

## Permissions (Android app)

The installed Android app may request the following, and only for the stated reason:

- **Storage / file saving** — to save your compressed file where you choose (e.g. your Downloads folder), using Android's standard file-saving system. This is used only at the moment you tap "Download" or "Save," for the file you just compressed.
- **Internet** — included because the underlying app framework (Capacitor/Android WebView) requires it to function as a component, but this app makes no network requests with your files. You can verify this yourself: with your device's Wi-Fi and mobile data both off, every compression feature still works.

No permission is used to read files you haven't explicitly chosen to compress, and no permission is used to send anything off your device.

## Data this app does collect

None. There is no account system, no server-side storage, and no analytics SDK in this app. The people who built this app have no visibility into what files you compress, how often you use the app, or any other usage information, because nothing is ever reported back to them.

## Third-party components

This app includes some open-source software components (an image/video/audio engine and a PDF library) that run entirely on your device alongside this app's own code. They do not introduce any additional data collection — they are libraries, not services, and none of them make network requests. See `THIRD_PARTY_LICENSES.md` in the source repository for the full list and their individual licenses.

## Children's privacy

This app does not knowingly collect information from anyone, of any age, because it does not collect information at all.

## Changes to this policy

If this policy ever changes, the updated version will be published at the same location, with a new "Last updated" date at the top. Given the app's design (no server, no accounts, no data collection), changes here would only ever be to clarify wording, not to introduce data collection that the app's architecture doesn't support.

## Contact

Questions about this policy can be raised via the project's GitHub page:
https://github.com/darshandpatel63-prog/DD-COMPRESSER-PRIVACY-FIRST-COMPRESSER