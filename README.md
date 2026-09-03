https://darshandpatel63-prog.github.io/DD-COMPRESSER-PRIVACY-FIRST-COMPRESSER/


⚡ DD Compressor — Privacy-First File Compressor
DD Compressor is a free, browser-based file compression tool designed with a privacy-first approach.
It can process images, PDFs, audio, video, and selected text-based files directly inside the user's browser.
🔒 Your selected files are processed locally in the browser. The application does not require uploading them to a custom compression server.
🌐 Live Website
https://darshandpatel63-prog.github.io/DD-COMPRESSER-PRIVACY-FIRST-COMPRESSER/
✨ Features
🖼️ Image Compression
Common supported formats:
JPG / JPEG
PNG
WebP
BMP
AVIF
The image compressor can:
Adjust image quality
Reduce dimensions when necessary
Perform multiple compression passes
Convert to WebP or JPEG
Keep PNG output when practical
Attempt to reach the selected target size
Show original size, compressed size, and savings
📄 PDF Compression
PDF files can be processed in the browser.
The current approach renders PDF pages locally and creates a new optimized PDF.
Important: This is primarily an image-based PDF optimization method. Complex PDFs containing selectable text, forms, annotations, embedded files, or other special objects may not preserve every original PDF feature.
🎵 Audio Compression
Common formats include:
MP3
WAV
M4A
AAC
OGG
Opus
FLAC
WMA
Output options include MP3, OGG/Opus, and WAV.
🎬 Video Compression
Common formats include:
MP4
MOV
WebM
AVI
MKV
M4V
MPEG / MPG
3GP
OGV
Output options include:
MP4 — H.264 + AAC
WebM — VP9 + Opus
📦 Text / Data Compression
Supported text/data formats can be compressed with browser GZIP support:
TXT
CSV
JSON
XML
HTML
CSS
JS
SVG
🔒 Privacy First
Privacy is a core goal of DD Compressor.
The application's compression workflow is designed to process selected files locally in the user's browser.
There is:
❌ No account required
❌ No custom file-storage server
❌ No application database for uploaded files
❌ No required server-side upload for compression
The compressed file is generated in the browser and can be downloaded directly to the user's device.
Third-party resources
The project uses browser-side libraries such as FFmpeg/ffmpeg.wasm, PDF.js, and jsPDF. Some library resources may be loaded from public CDNs.
The application's intended compression workflow does not upload the selected file to those CDN services.
For highly confidential files, users should still consider their browser, device, extensions, network, and third-party dependencies.
🎯 Target Size
Users can choose:
KB
MB
GB
TB
For example:
Target: 700 KB
The compressor treats the target as a goal and tries to get the output as close as practical.
Important limitation
A target size is not a mathematical guarantee.
An already-compressed file may have very little additional compression available.
Examples:
JPG → JPG
WebP → WebP
MP3 → MP3
MP4 → MP4
Trying to force an extremely small size can require substantial quality loss.
The application therefore attempts to balance:
File Size ↔ Quality
🖼️ Image Compression Logic
The compressor first tries quality-based compression.
If that is not enough to reach the target, it can progressively reduce image dimensions.
Typical process:
Original Image
      ↓
Quality adjustment
      ↓
Multiple encoding attempts
      ↓
Dimension reduction if required
      ↓
Target-size attempt
      ↓
Compressed file
This helps avoid the common problem where an image stops getting smaller around a particular file size.
PNG note
PNG is lossless, so it cannot always be reduced to an arbitrary target size while preserving the same information.
For aggressive size reduction, WebP or JPEG can often produce much smaller files.
🎬 Video Compression
Video compression mainly changes:
Video bitrate
Audio bitrate
Video codec
Output container
For example:
Input Video
    ↓
H.264 / VP9 encoding
    ↓
Bitrate adjustment
    ↓
Audio optimization
    ↓
Compressed Video
Very small target sizes may require lower resolution and/or lower bitrate.
Therefore, original video quality cannot always be preserved when the target is extremely small.
🎵 Audio Compression
Audio compression can change:
Codec
Bitrate
Output format
Higher bitrate generally gives better audio quality but creates larger files.
Lower bitrate generally creates smaller files but can reduce audio quality.
📄 PDF Compression
The current PDF method can be useful for:
Scanned documents
Image-heavy PDFs
Photo PDFs
Presentation-style PDFs
Because pages may be rasterized, the resulting PDF may differ from the original in:
Text selection
Searchability
Forms
Annotations
Embedded objects
Keep the original PDF when those features are important.
📱 Mobile Support
The interface is designed for modern:
Android browsers
iPhone/iPad browsers
Desktop browsers
Chromium-based browsers
Firefox
Safari
Large files can require significant RAM and CPU.
For very large videos or PDFs, desktop hardware may provide a faster and more stable experience.
🧩 Project Structure
For FFmpeg browser processing, keep the following files in the same GitHub Pages folder:
DD-COMPRESSER-PRIVACY-FIRST-COMPRESSER/
│
├── index.html
├── ffmpeg.min.js
├── 814.ffmpeg.js
└── README.md
Why are FFmpeg files local?
The FFmpeg JavaScript package uses a Web Worker.
Serving the worker from the same origin as the GitHub Pages application helps avoid browser cross-origin Worker restrictions.
Therefore, ffmpeg.min.js and its worker file should be hosted alongside index.html.
🌍 GitHub Pages
Recommended GitHub Pages configuration:
Repository
   ↓
Settings
   ↓
Pages
   ↓
Deploy from a branch
   ↓
main
   ↓
/ (root)
After GitHub Pages publishes the repository, open the generated Pages URL.
🛠️ Technologies
DD Compressor uses web technologies including:
HTML5
CSS3
JavaScript
File API
Blob API
Canvas API
Web Workers
Compression Streams API
FFmpeg / ffmpeg.wasm
PDF.js
jsPDF
📚 Open-Source Technologies
FFmpeg
https://ffmpeg.org/
FFmpeg is a multimedia framework used for audio and video processing.
ffmpeg.wasm
https://github.com/ffmpegwasm/ffmpeg.wasm
A WebAssembly-based FFmpeg project for running FFmpeg in web environments.
PDF.js
https://github.com/mozilla/pdf.js
A JavaScript PDF rendering library.
jsPDF
https://github.com/parallax/jsPDF
A JavaScript library for generating PDF documents.
Please review the respective projects' licenses and documentation before redistributing third-party libraries or bundled dependencies.
⚠️ Performance & Browser Limitations
Browser compression has advantages, but large files can consume substantial:
RAM
CPU
Battery
Browser resources
Video and audio transcoding can be especially CPU-intensive.
On mobile devices, the browser may stop a long-running operation if the device becomes low on memory or the browser puts the tab into the background.
🐛 Known Limitations
Some files may not become significantly smaller.
This is normal for files that are already highly compressed, such as:
JPG
WebP
MP3
MP4
HEVC/H.265 video
Optimized PDFs
ZIP
RAR
7Z
Re-compressing such files can sometimes produce a larger file or reduce quality without meaningful size savings.
The application should not claim a file is smaller when the resulting file is actually larger.
🔐 Security Disclaimer
DD Compressor is designed around local browser-side processing, but no web application can guarantee absolute security or privacy in every environment.
Browser extensions, compromised devices, browser bugs, network configuration, third-party resources, and other factors can affect privacy and security.
For extremely sensitive information, consider using trusted offline software in a controlled environment.
📜 License
Unless a separate license file is included, the original source code of this repository is considered all rights reserved by the repository owner.
Third-party libraries remain subject to their own licenses.
If you want the DD Compressor source code to be freely used, modified, and redistributed, add an appropriate open-source license such as MIT.
🤝 Contributions
Bug reports, suggestions, performance improvements, and compatibility fixes are welcome.
Before submitting changes:
Test small and large files.
Test on both desktop and mobile.
Verify the original file remains unchanged.
Verify the compressed output opens correctly.
Avoid unnecessary server-side file uploads.
Keep privacy as a core design principle.
❤️ Project Goal
The goal of DD Compressor is simple:
Make file compression easy, free, fast, and privacy-focused — directly in the user's browser.
Choose
  ↓
Compress
  ↓
Download
No unnecessary account.
No custom compression server.
No forced file upload.
👨‍💻 Author
DD Compressor
Privacy-first browser-based file compression project.
⭐ Support the Project
If you find DD Compressor useful:
⭐ Star the repository
🐛 Report bugs
💡 Suggest improvements
🔧 Contribute fixes
📢 Share the project
Thank you for supporting a privacy-focused browser tool.
