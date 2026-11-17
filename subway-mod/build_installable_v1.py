#!/usr/bin/env python3
"""Build an installable ARM64 patch APK for the exact Subway Zurich 1.105.0 input.

This script does not include the proprietary original APK. Put the user's own
subwayZurich.apk beside the script or change SOURCE below. It intentionally
uses JAR/V1 signing with targetSdk 29 rather than a hand-built V2 signing block:
Android 11+ requires V2 only for apps targeting API 30+, and Android 13 still
accepts V1 for targetSdk 29. resources.arsc is stored and 4-byte aligned.
"""
from pathlib import Path
import zipfile, struct, hashlib, zlib, subprocess

SOURCE = Path("subwayZurich.apk")
OUTPUT = Path("SubwaySurfers_InstalacaoCorrigida.apk")
WORK = Path("subway_build")
WORK.mkdir(exist_ok=True)
UNSIGNED = WORK / "unsigned.apk"
ALIGNED_UNSIGNED = WORK / "aligned-unsigned.apk"
SIGNED_BEFORE_ALIGN = WORK / "signed-before-align.apk"
KEYSTORE = WORK / "test-signing-key.jks"

OLD_PACKAGE = "com.random.zurich"
NEW_PACKAGE = "com.random.speedy"  # unique test package; same string length
TARGET_SDK = 29
assert len(OLD_PACKAGE) == len(NEW_PACKAGE)

SEGMENTS = [
    (0x00000000, 0x00000000, 0x10FCF6C),
    (0x10FCF6C, 0x1100F6C, 0x1B50434),
    (0x2C4D3A0, 0x2C553A0, 0x1B8390),
    (0x2E05730, 0x2E11730, 0x1CDEE8),
]
PATCHES = [
    (0x1647ADC, bytes.fromhex("08 10 28 1e"), bytes.fromhex("08 71 40 bd"), "blend 0.20s -> 0.125s"),
    (0x1647B40, bytes.fromhex("00 10 28 1e"), bytes.fromhex("00 91 4e bd"), "blend 0.10s -> 0.125s"),
    (0x1647E08, bytes.fromhex("08 10 28 1e"), bytes.fromhex("28 89 4d bd"), "blend 0.15s -> 0.125s"),
    (0x1647E14, bytes.fromhex("00 10 28 1e"), bytes.fromhex("00 10 2e 1e"), "fallback blend 1.00s -> 0.125s"),
    (0x164DFDC, bytes.fromhex("08 10 28 1e"), bytes.fromhex("08 91 4e bd"), "blend 0.10s -> 0.125s"),
    (0x164E00C, bytes.fromhex("09 10 28 1e"), bytes.fromhex("09 71 40 bd"), "blend 0.20s -> 0.125s"),
]

def va_to_offset(va):
    for file_off, vaddr, size in SEGMENTS:
        if vaddr <= va < vaddr + size:
            return file_off + va - vaddr
    raise ValueError(f"Unmapped native address: {va:#x}")

def read_len16(buf, pos):
    n = struct.unpack_from("<H", buf, pos)[0]
    pos += 2
    if n & 0x8000:
        n = ((n & 0x7fff) << 16) | struct.unpack_from("<H", buf, pos)[0]
        pos += 2
    return n, pos

def string_pool(buf, chunk_pos):
    count, _, flags, start, _ = struct.unpack_from("<IIIII", buf, chunk_pos + 8)
    if flags & 0x100:
        raise ValueError("Unexpected UTF-8 binary XML string pool")
    offsets = struct.unpack_from("<" + "I" * count, buf, chunk_pos + 28) if count else []
    result = []
    for rel in offsets:
        p = chunk_pos + start + rel
        n, q = read_len16(buf, p)
        result.append(bytes(buf[q:q + n * 2]).decode("utf-16le"))
    return result

def patch_manifest(data):
    out = bytearray(data)
    if struct.unpack_from("<H", out, 0)[0] != 0x0003:
        raise ValueError("AndroidManifest.xml is not binary XML")
    pos = 8
    strings = []
    package_hits = 0
    target_hits = 0
    while pos + 8 <= len(out):
        typ, _, size = struct.unpack_from("<HHI", out, pos)
        if size < 8 or pos + size > len(out):
            raise ValueError(f"Malformed XML chunk at {pos:#x}")
        if typ == 0x0001:
            strings = string_pool(out, pos)
            count = struct.unpack_from("<I", out, pos + 8)[0]
            start = struct.unpack_from("<I", out, pos + 20)[0]
            offsets = struct.unpack_from("<" + "I" * count, out, pos + 28) if count else []
            for rel in offsets:
                p = pos + start + rel
                n, q = read_len16(out, p)
                value = bytes(out[q:q + n * 2]).decode("utf-16le")
                if value == OLD_PACKAGE:
                    if n != len(NEW_PACKAGE):
                        raise ValueError("Package ID replacement changes string length")
                    out[q:q + n * 2] = NEW_PACKAGE.encode("utf-16le")
                    package_hits += 1
        elif typ == 0x0102:
            name_idx = struct.unpack_from("<I", out, pos + 20)[0]
            tag = strings[name_idx] if name_idx < len(strings) else ""
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", out, pos + 24)
            if tag == "uses-sdk":
                base = pos + 16 + attr_start
                for i in range(attr_count):
                    q = base + i * attr_size
                    attr_name_idx = struct.unpack_from("<I", out, q + 4)[0]
                    attr_name = strings[attr_name_idx] if attr_name_idx < len(strings) else ""
                    if attr_name == "targetSdkVersion":
                        _, _, data_type, old_value = struct.unpack_from("<HBBI", out, q + 12)
                        if data_type != 0x10 or old_value != 36:
                            raise ValueError(f"Unexpected targetSdkVersion: type={data_type}, value={old_value}")
                        struct.pack_into("<I", out, q + 16, TARGET_SDK)
                        target_hits += 1
        pos += size
    if package_hits != 1 or target_hits != 1:
        raise ValueError(f"Manifest edits not unique: package={package_hits}, targetSdk={target_hits}")
    return bytes(out)

def patch_dex(data):
    old, new = OLD_PACKAGE.encode(), NEW_PACKAGE.encode()
    if data.count(old) != 1:
        raise ValueError("Expected one application ID string in classes.dex")
    out = bytearray(data.replace(old, new))
    if not out.startswith(b"dex\\n"):
        raise ValueError("Invalid DEX header")
    out[12:32] = hashlib.sha1(out[32:]).digest()
    struct.pack_into("<I", out, 8, zlib.adler32(out[12:]) & 0xffffffff)
    return bytes(out)

def patch_native(data):
    out = bytearray(data)
    audit = []
    for va, replacement, expected, label in PATCHES:
        off = va_to_offset(va)
        found = bytes(out[off:off + 4])
        if found != expected:
            raise ValueError(f"Unexpected instruction at {va:#x}: {found.hex()}")
        out[off:off + 4] = replacement
        audit.append((va, found.hex(), replacement.hex(), label))
    return bytes(out), audit

def make_info(info, extra):
    ni = zipfile.ZipInfo(info.filename, info.date_time)
    ni.compress_type = info.compress_type
    ni.comment = info.comment
    ni.create_system = info.create_system
    ni.create_version = info.create_version
    ni.extract_version = info.extract_version
    ni.internal_attr = info.internal_attr
    ni.external_attr = info.external_attr
    ni.extra = extra
    return ni

def align_stored(src, dst):
    with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(dst, "w", allowZip64=True) as zout:
        zout.comment = zin.comment
        for info in zin.infolist():
            payload = zin.read(info.filename)
            extra = bytearray(info.extra)
            p = 0
            kept = bytearray()
            while p + 4 <= len(extra):
                fid, flen = struct.unpack_from("<HH", extra, p)
                end = p + 4 + flen
                if end > len(extra):
                    raise ValueError("Malformed ZIP extra data")
                if fid != 0xD935:
                    kept.extend(extra[p:end])
                p = end
            if p != len(extra):
                raise ValueError("Trailing ZIP extra data")
            ni = make_info(info, bytes(kept))
            if ni.compress_type == zipfile.ZIP_STORED:
                data_off = zout.fp.tell() + 30 + len(ni.filename.encode("utf-8")) + len(ni.extra)
                if data_off % 4:
                    pad = (-(data_off + 4)) % 4
                    ni.extra += struct.pack("<HH", 0xD935, pad) + bytes(pad)
            zout.writestr(ni, payload, compress_type=ni.compress_type)

with zipfile.ZipFile(SOURCE, "r") as zin:
    if zin.testzip() is not None:
        raise ValueError("Original APK ZIP CRC error")
    names = set(zin.namelist())
    required = {"AndroidManifest.xml", "resources.arsc", "classes.dex", "lib/arm64-v8a/libil2cpp.so"}
    if not required.issubset(names):
        raise ValueError("Input is not the expected ARM64 v1.105.0 APK")
    patched = []
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.filename == "AndroidManifest.xml":
            data = patch_manifest(data)
        elif info.filename == "classes.dex":
            data = patch_dex(data)
        elif info.filename == "lib/arm64-v8a/libil2cpp.so":
            data, audit = patch_native(data)
        patched.append((info, data))
    with zipfile.ZipFile(UNSIGNED, "w", allowZip64=True) as zout:
        for info, data in patched:
            zout.writestr(make_info(info, info.extra), data, compress_type=info.compress_type)

align_stored(UNSIGNED, ALIGNED_UNSIGNED)

if KEYSTORE.exists():
    KEYSTORE.unlink()
subprocess.run([
    "keytool", "-genkeypair", "-alias", "subway-test", "-keyalg", "RSA", "-keysize", "2048",
    "-validity", "9000", "-dname", "CN=Subway Surfers Test Build, OU=Local, O=Local Build, L=SC, ST=SC, C=BR",
    "-keystore", str(KEYSTORE), "-storepass", "installpass", "-keypass", "installpass", "-storetype", "JKS", "-noprompt"
], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
subprocess.run([
    "jarsigner", "-keystore", str(KEYSTORE), "-storetype", "JKS", "-storepass", "installpass",
    "-keypass", "installpass", "-sigalg", "SHA256withRSA", "-digestalg", "SHA-256",
    "-signedjar", str(SIGNED_BEFORE_ALIGN), str(ALIGNED_UNSIGNED), "subway-test"
], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
align_stored(SIGNED_BEFORE_ALIGN, OUTPUT)

# Final verification: ZIP, V1/JAR signature, DEX checksum, package metadata, resource alignment.
with zipfile.ZipFile(OUTPUT, "r") as z:
    if z.testzip() is not None:
        raise ValueError("Output ZIP CRC check failed")
    raw = OUTPUT.read_bytes()
    for info in z.infolist():
        if info.compress_type == zipfile.ZIP_STORED:
            n, x = struct.unpack_from("<HH", raw, info.header_offset + 26)
            if (info.header_offset + 30 + n + x) % 4:
                raise ValueError(f"Unaligned stored entry: {info.filename}")
    if z.getinfo("resources.arsc").compress_type != zipfile.ZIP_STORED:
        raise ValueError("resources.arsc is compressed")
    dex = z.read("classes.dex")
    if hashlib.sha1(dex[32:]).digest() != dex[12:32]:
        raise ValueError("DEX SHA-1 failed")
    if struct.unpack_from("<I", dex, 8)[0] != (zlib.adler32(dex[12:]) & 0xffffffff):
        raise ValueError("DEX Adler32 failed")
    if b"APK Sig Block 42" in raw:
        raise ValueError("Unexpected manual APK signing block")
subprocess.run(["jarsigner", "-verify", str(OUTPUT)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
print("Output:", OUTPUT)
print("Package:", NEW_PACKAGE, "| targetSdk:", TARGET_SDK)
print("Signature: V1/JAR verified; no custom V2 block")
print("resources.arsc: stored and 4-byte aligned")
print("ZIP CRC and DEX checksums: OK")
for row in audit:
    print("Patch:", row)
print("Note: 125 ms transitions; no claim of absolute zero-latency.")
