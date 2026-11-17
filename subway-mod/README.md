# Subway Surfers animation patch (ARM64 v1.105.0)

This folder contains a reproducible patch script for the APK supplied by the user.
It does **not** include the proprietary game APK.

## What the patch changes

Six 32-bit ARM64 instructions in `lib/arm64-v8a/libil2cpp.so` replace the
identified jump/roll animation CrossFade blend-time inputs with zero. This
removes those transition blend durations; it does not remove every possible
source of input or frame latency and cannot guarantee literal zero latency.

The application ID is changed from `com.random.zurich` to
`com.random.surfer` (same-length manifest string) so the modified build can
be installed alongside the original package.

## Important: make the APK installable on Android 11+

The supplied APK targets Android API 36. Android 11 (API 30) and newer reject
apps targeting API 30+ if either of these is true:
- the APK contains only a V1/JAR signature (V2 or newer is required);
- `resources.arsc` is compressed or its data is not aligned on a 4-byte boundary.

Do **not** use `jarsigner` alone as the final APK signer. It only creates a
V1/JAR signature. Use the Android SDK Build Tools in this order:

1. Patch the exact ARM64 v1.105.0 APK:
   `python patch_animation_delays.py original.apk output-unsigned.apk`
2. Align the APK:
   `zipalign -f -p 4 output-unsigned.apk output-aligned.apk`
3. Sign with V1 and V2 (V3 can also be enabled):
   `apksigner sign --ks your-key.jks --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out output-signed.apk output-aligned.apk`
4. Verify:
   `apksigner verify --verbose --print-certs output-signed.apk`

The official Android behavior-change documentation explains both the V2
signature requirement and the uncompressed/aligned resource-table requirement:
https://developer.android.com/about/versions/11/behavior-changes-11

## Compatibility

This patch is tied to the exact supplied ARM64 v1.105.0 native library. Other
versions or CPU architectures require fresh disassembly and new offsets.
