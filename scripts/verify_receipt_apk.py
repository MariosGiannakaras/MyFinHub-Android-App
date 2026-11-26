#!/usr/bin/env python3
"""Verify bundled OCR model provenance and 64-bit 16 KiB native segment alignment."""
import hashlib
import struct
import sys
import zipfile

PINS = {
    "ell.traineddata": (1419514, "ed98ae1a88d84414da316e6eeab3232f2c68639b"),
    "eng.traineddata": (4113088, "bbef4675053b5b468cdb477053e28b1c698ba08e"),
}


def verify_elf(data: bytes, name: str) -> None:
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        raise ValueError(f"{name}: expected little-endian ELF64")
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phsize, phnum = struct.unpack_from("<HH", data, 54)
    if phsize < 56 or phnum == 0 or phoff + phsize * phnum > len(data):
        raise ValueError(f"{name}: invalid ELF program headers")
    count = 0
    for offset in range(phoff, phoff + phsize * phnum, phsize):
        kind = struct.unpack_from("<I", data, offset)[0]
        if kind == 1:  # PT_LOAD
            count += 1
            seg_off, vaddr = struct.unpack_from("<QQ", data, offset + 8)
            alignment = struct.unpack_from("<Q", data, offset + 48)[0]
            if alignment < 16384 or (seg_off - vaddr) % 16384:
                raise ValueError(f"{name}: incompatible 16 KiB PT_LOAD")
    if count == 0:
        raise ValueError(f"{name}: no PT_LOAD segments")


def verify(path: str) -> None:
    with zipfile.ZipFile(path) as apk:
        for name, (size, pin) in PINS.items():
            data = apk.read("assets/receipt-tessdata/" + name)
            actual = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
            if len(data) != size or actual != pin:
                raise ValueError(f"{name}: incorrect model bytes")
            print(f"{name}: sha256={hashlib.sha256(data).hexdigest()}")
        for abi in ("arm64-v8a", "x86_64"):
            libs = [name for name in apk.namelist() if name.startswith(f"lib/{abi}/") and name.endswith(".so")]
            if not any(name.endswith("/libtesseract.so") for name in libs):
                raise ValueError(f"{abi}: missing Tesseract")
            for name in libs:
                data = apk.read(name)
                verify_elf(data, name)
                print(f"{name}: sha256={hashlib.sha256(data).hexdigest()}")
    print("Bundled OCR models and 64-bit native load alignment verified.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: verify_receipt_apk.py APK")
    verify(sys.argv[1])
