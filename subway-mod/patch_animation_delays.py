#!/usr/bin/env python3
"""Patch the supplied ARM64 Subway Zurich/Surfers APK v1.105.0 animation blend times.

Usage:
  python patch_animation_delays.py original.apk output-unsigned.apk

This patches only six AArch64 constants used by the identified jump/roll
CrossFade paths, then changes the manifest application ID to
com.random.runner so it can be installed alongside the original and previous test package.
It does not include or redistribute the game's APK.
"""
from pathlib import Path
import hashlib
import struct
import sys
import zipfile
import zlib

SEGMENTS = [
    (0x00000000, 0x00000000, 0x10FCF6C),
    (0x10FCF6C, 0x1100F6C, 0x1B50434),
    (0x2C4D3A0, 0x2C553A0, 0x1B8390),
    (0x2E05730, 0x2E11730, 0x1CDEE8),
]
PATCHES = [
    (0x1647ADC, bytes.fromhex("08 10 28 1e"), "CrossFade 0.20s -> 0.125s"),
    (0x1647B40, bytes.fromhex("00 10 28 1e"), "CrossFadeQueued 0.10s -> 0.125s"),
    (0x1647E08, bytes.fromhex("08 10 28 1e"), "CrossFade 0.15s -> 0.125s"),
    (0x1647E14, bytes.fromhex("00 10 28 1e"), "fallback 1.00s -> 0.125s"),
    (0x164DFDC, bytes.fromhex("08 10 28 1e"), "CrossFade 0.10s -> 0.125s"),
    (0x164E00C, bytes.fromhex("09 10 28 1e"), "CrossFade 0.20s -> 0.125s"),
]
OLD_PACKAGE = "com.random.zurich"
NEW_PACKAGE = "com.random.runner"


def va_to_offset(va: int) -> int:
    for file_offset, va_start, file_size in SEGMENTS:
        if va_start <= va < va_start + file_size:
            return file_offset + va - va_start
    raise ValueError(f"VA not mapped: {va:#x}")


def read_u16_length(buf: bytes, pos: int):
    length = struct.unpack_from("<H", buf, pos)[0]
    pos += 2
    if length & 0x8000:
        length = ((length & 0x7FFF) << 16) | struct.unpack_from("<H", buf, pos)[0]
        pos += 2
    return length, pos


def patch_manifest(data: bytes) -> bytes:
    buf = bytearray(data)
    pos = 8
    replacements = 0
    while pos + 8 <= len(buf):
        chunk_type, _, chunk_size = struct.unpack_from("<HHI", buf, pos)
        if chunk_size < 8 or pos + chunk_size > len(buf):
            raise ValueError("Invalid binary Android manifest")
        if chunk_type == 0x0001:  # string pool
            count, _, flags, strings_start, _ = struct.unpack_from("<IIIII", buf, pos + 8)
            if flags & 0x100:
                raise ValueError("Unexpected UTF-8 string pool")
            offsets = struct.unpack_from("<" + "I" * count, buf, pos + 28)
            for rel in offsets:
                p = pos + strings_start + rel
                length, q = read_u16_length(buf, p)
                value = bytes(buf[q:q + length * 2]).decode("utf-16le")
                if value == OLD_PACKAGE:
                    if len(NEW_PACKAGE) != length:
                        raise ValueError("Package ID must preserve string length")
                    buf[q:q + length * 2] = NEW_PACKAGE.encode("utf-16le")
                    replacements += 1
        pos += chunk_size
    if replacements != 1:
        raise ValueError(f"Expected one package ID, found {replacements}")
    return bytes(buf)


def patch_dex(data: bytes) -> bytes:
    buf = bytearray(data)
    old = OLD_PACKAGE.encode("ascii")
    new = NEW_PACKAGE.encode("ascii")
    if len(old) != len(new) or buf.count(old) != 1:
        raise ValueError("DEX application-ID string not found exactly once")
    buf = buf.replace(old, new)
    buf[12:32] = hashlib.sha1(buf[32:]).digest()
    struct.pack_into("<I", buf, 8, zlib.adler32(buf[12:]) & 0xFFFFFFFF)
    return bytes(buf)


def patch_native(data: bytes):
    buf = bytearray(data)
    audit = []
    for va, replacement, label in PATCHES:
        off = va_to_offset(va)
        original = bytes(buf[off:off + 4])
        expected = {
            0x1647ADC: bytes.fromhex("08 71 40 bd"),
            0x1647B40: bytes.fromhex("00 91 4e bd"),
            0x1647E08: bytes.fromhex("28 89 4d bd"),
            0x1647E14: bytes.fromhex("00 10 2e 1e"),
            0x164DFDC: bytes.fromhex("08 91 4e bd"),
            0x164E00C: bytes.fromhex("09 71 40 bd"),
        }[va]
        if len(original) != 4 or original != expected:
            raise ValueError(f"Unexpected native instruction at {va:#x}: {original.hex()}")
        buf[off:off + 4] = replacement
        audit.append((hex(va), original.hex(), replacement.hex(), label))
    return bytes(buf), audit


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: patch_animation_delays.py input.apk output-unsigned.apk")
    source, output = map(Path, sys.argv[1:])
    with zipfile.ZipFile(source, "r") as zin:
        names = set(zin.namelist())
        required = {"AndroidManifest.xml", "classes.dex", "lib/arm64-v8a/libil2cpp.so"}
        if not required.issubset(names):
            raise SystemExit("APK does not match the expected ARM64 v1.105.0 layout")
        manifest = patch_manifest(zin.read("AndroidManifest.xml"))
        dex = patch_dex(zin.read("classes.dex"))
        lib, audit = patch_native(zin.read("lib/arm64-v8a/libil2cpp.so"))
        with zipfile.ZipFile(output, "w", allowZip64=True) as zout:
            zout.comment = zin.comment
            for info in zin.infolist():
                if info.filename == "AndroidManifest.xml":
                    payload = manifest
                elif info.filename == "classes.dex":
                    payload = dex
                elif info.filename == "lib/arm64-v8a/libil2cpp.so":
                    payload = lib
                else:
                    payload = zin.read(info.filename)
                zout.writestr(info, payload, compress_type=info.compress_type)
    print(f"Created unsigned APK: {output}")
    print(f"Native patch count: {len(audit)}")
    for row in audit:
        print(" | ".join(row))
    print("Each targeted animation blend input is set to 0.125 seconds, not zero.")
    print("Align and sign with APK Signature Scheme v2 or newer before installing.")


if __name__ == "__main__":
    main()
