# Receipt OCR third-party components

- Tesseract4Android 4.9.0 (Apache-2.0), https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0
- Tesseract OCR 5.5.1 (Apache-2.0), Leptonica and image codecs bundled by the above dependency.
- Tesseract tessdata_fast Greek (ell) and English (eng) version 4.1.0, Apache-2.0, https://github.com/tesseract-ocr/tessdata_fast/tree/65727574dfcd264acbb0c3e07860e4e9e9b22185
- Model files are downloaded only during the build, pinned by upstream commit, exact length and Git blob ID, validated again when installed in the application's no-backup data directory. No receipt pixels or OCR text are sent to a server. The OCR engine and both models are packaged for offline use.

See LICENSE-Apache-2.0.txt and the upstream dependencies for license and copyright notices.
