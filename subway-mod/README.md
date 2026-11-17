# Subway Surfers animation patch (ARM64 v1.105.0)

This folder contains scripts for rebuilding a patched copy of the APK supplied
by the user. It does not contain or redistribute the proprietary game APK.

## Installable build for Android 13

Use `build_installable_v1.py` with the exact ARM64 v1.105.0 input APK. It:

- checks the original native instruction bytes before editing;
- replaces six jump/roll blend-time inputs with 0.125 seconds;
- changes the test package ID to `com.random.speedy` so it can be installed beside the original;
- changes targetSdkVersion from 36 to 29;
- stores and 4-byte aligns `resources.arsc`;
- signs using standard JAR/V1 signing and verifies the result with `jarsigner`;
- verifies ZIP CRCs and DEX checksums.

The earlier experimental packager used a hand-built APK Signature Scheme v2
block. This reproducible builder deliberately avoids that custom signature
implementation. It targets API 29 because Android 11+ requires V2 or newer
for apps that target API 30+, while V1 is still supported for targetSdk 29.
Android's official installation requirements also require `resources.arsc`
to be uncompressed and 4-byte aligned when targeting API 30+.

Official reference:
https://developer.android.com/about/versions/11/behavior-changes-11

## Build

1. Place the user's own `subwayZurich.apk` beside the script (or edit SOURCE).
2. Run `python build_installable_v1.py` on a machine with Python 3, Java,
   `keytool`, and `jarsigner`.
3. Install `SubwaySurfers_InstalacaoCorrigida.apk`.

## Limits

The six edits shorten the identified blend intervals to 125 ms, not absolute
zero. The script checks the APK structure and V1 signature, but cannot test
launching or gameplay on a physical Samsung device. Other game versions or
CPU architectures require fresh reverse engineering.
