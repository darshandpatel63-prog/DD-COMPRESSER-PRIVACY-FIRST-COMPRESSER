# Third-party licenses

This project vendors a small number of open-source libraries locally under
`vendor/` (see README "Local-first dependencies" for why they're local
instead of CDN-loaded). Each keeps its own original license, listed below.
None of them are modified — they're the unmodified, real distribution
files published by their respective projects.

## @ffmpeg/ffmpeg (vendor/ffmpeg/ffmpeg.js, 814.ffmpeg.js)
Version 0.12.15 — MIT License
https://github.com/ffmpegwasm/ffmpeg.wasm

## @ffmpeg/core (vendor/ffmpeg/core/ffmpeg-core.js, ffmpeg-core.wasm)
Version 0.12.10 — GPL-2.0-or-later
(This build includes libx264, which is itself GPL-licensed — this is why
the core package as a whole is GPL rather than the ffmpeg.wasm project's
own MIT/LGPL default, and is standard for any FFmpeg build that includes
libx264.)
https://github.com/ffmpegwasm/ffmpeg.wasm/tree/main/packages/core

## pdf-lib (vendor/pdf-lib/pdf-lib.min.js)
Version 1.17.1 — MIT License
https://github.com/Hopding/pdf-lib

## Inter (vendor/fonts/)
Version 5.3.0 packaging (fonts.google.com/specimen/Inter) — SIL Open Font
License 1.1
Distributed via the @fontsource/inter npm package; only the Latin-subset
woff2 files (weights 400/600/700) are included, to keep this repository
small — the full character set is available from the sources above if
needed.

---

If you redistribute this project, keep this file and the individual
library headers (where present in the minified files) intact.
