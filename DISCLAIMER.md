# Disclaimer — DD Compressor

**Last updated:** September 2026

## No warranty

DD Compressor is provided **"as is" and "as available,"** without warranty of any kind, express or implied — including, without limitation, any warranty of merchantability, fitness for a particular purpose, accuracy, or non-infringement. Use of this app is entirely at your own risk.

## Always keep your original file

This app is built with real, tested safeguards (see the project's README for specifics — automatic checks that compare a compressed result against the original and refuse to hand back a result that looks wrong, for example). No software, including this one, can guarantee a perfect outcome for every possible file, on every device, in every situation. **Do not delete an original file until you have checked the compressed result and confirmed you're happy with it.** This matters most for irreplaceable files — a scanned document you can't rescan, the only copy of a video, and similar.

## Target sizes are estimates, not guarantees

Where this app asks for a target size, that's a goal it works toward, not a mathematical promise. Some files — especially ones that are already efficiently compressed — cannot reach an arbitrary target without unacceptable quality loss, and the app is designed to say so honestly rather than force a result. Video and audio target sizes in particular are estimates based on bitrate math applied to real, variable footage, so the actual output may land close to, rather than exactly at, your requested size.

## Known limitations

Stated plainly, and kept up to date as the app changes (see the project README for the current, detailed list):

- Very large files (specifically, video/audio above a size documented in the app and its README) cannot be processed reliably by this app's in-device engine, and are refused outright rather than risk a stuck or crashed session.
- Certain color formats (CMYK images, common in print-sourced scans) are intentionally left uncompressed rather than risk incorrect colors, based on a real issue that was found, diagnosed, and fixed this way.
- PDF compression only recompresses certain kinds of embedded images; other content in a PDF (text, vector graphics, forms) is left untouched by design, and a PDF without recompressible images may see little or no size reduction.

## Not professional advice

Nothing in this app constitutes legal, medical, financial, or other professional advice about the files you choose to process or how you use them.

## Limitation of liability

To the fullest extent permitted by applicable law, the developer of DD Compressor is not liable for any direct, indirect, incidental, special, or consequential damages resulting from the use or inability to use this app, including but not limited to loss of data, loss of files, or loss of time — even where the app has been informed of the possibility of such damages.

## Your responsibility

You are responsible for the files you choose to process with this app, for keeping appropriate backups, and for verifying that a compressed result meets your needs before relying on it or deleting the original.

## Contact

https://github.com/darshandpatel63-prog/DD-COMPRESSER-PRIVACY-FIRST-COMPRESSER
