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

## Advertising and consent

The current store-first Android build is ad-free and does not include the AdMob SDK. A later monetized Android build may include Google AdMob. When that monetized build is enabled, and where Google's User Messaging Platform requires consent, the app presents the applicable consent flow before requesting ads. AdMob network traffic is separate from local file compression; selected files remain on-device.

## What this app does NOT do

- It does not upload your files to any server, cloud storage, or third party.
- The Android app uses Google AdMob for advertising. The compression engine does not send your selected files or their contents to AdMob.
- It does not require you to create an account or sign in.
- It does not read your files for any purpose other than compressing the exact one you chose, at the exact moment you asked it to.
- It does not share, sell, or otherwise transmit anything about you or your files, because it has no mechanism to do so.

## Permissions (Android app)

The installed Android app may request the following, and only for the stated reason:

- **Storage / file saving** — to save your compressed file where you choose (e.g. your Downloads folder), using Android's standard file-saving system. This is used only at the moment you tap "Download" or "Save," for the file you just compressed.
- **Internet** — required by the Android ad SDK to load ads. Compression itself remains local and can continue without internet; ads simply cannot load while offline. You can verify this yourself: with your device's Wi-Fi and mobile data both off, every compression feature still works.

No permission is used to read files you haven't explicitly chosen to compress, and no permission is used to send anything off your device.

## Data and advertising SDKs

The app itself does not maintain an account, server-side file storage, or an analytics database. The monetized Android build includes Google AdMob/Google Mobile Ads. The current store-first build does not include that SDK. When present, the third-party SDK can process advertising-related information (for example, advertising/device identifiers, diagnostics, and ad interaction or measurement data) according to Google's services and the user's consent/device settings. This data flow is separate from the compression engine: the contents, names, and bytes of files selected for compression are not sent to AdMob.

## Third-party components

This app includes some open-source software components (an image/video/audio engine and a PDF library) that run entirely on your device alongside this app's own code. They do not introduce any additional data collection — they are libraries, not services, and none of them make network requests. See `THIRD_PARTY_LICENSES.md` in the source repository for the full list and their individual licenses.

## Children's privacy

This app does not knowingly collect information from anyone, of any age, because it does not collect information at all.

## Changes to this policy

If this policy ever changes, the updated version will be published at the same location, with a new "Last updated" date at the top. Given the app's design (no server, no accounts, no data collection), changes here would only ever be to clarify wording, not to introduce data collection that the app's architecture doesn't support.

## Contact

Questions about this policy can be raised via the project's GitHub page:
https://github.com/darshandpatel63-prog/DD-COMPRESSER-PRIVACY-FIRST-COMPRESSER