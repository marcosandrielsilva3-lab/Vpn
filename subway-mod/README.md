# Subway Surfers animation patch (ARM64 v1.105.0)

This folder contains a reproducible patch script for the APK supplied by the user.
It does **not** include or redistribute the proprietary game APK.

## What the patch changes

Six 32-bit ARM64 instructions in `lib/arm64-v8a/libil2cpp.so` supply a
0.125-second blend time to the identified jump/roll CrossFade paths. The earlier
experimental build forced these inputs to zero and the user reported that the
game closed on launch. This safer build uses a short nonzero value instead.
It is not literal zero latency, and it does not remove frame-time, input, or
animation-controller delays elsewhere in the game.

The application ID changes from `com.random.zurich` to
`com.random.runner` (same-length manifest string) so the test build can be
installed alongside the original and previous test package.

## Rebuild and sign

1. Use the exact ARM64 game APK v1.105.0:
   `python patch_animation_delays.py original.apk output-unsigned.apk`
2. Align the resource table:
   `zipalign -f -p 4 output-unsigned.apk output-aligned.apk`
3. Sign with V1 and V2 (V3 can also be enabled):
   `apksigner sign --ks your-key.jks --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out output-signed.apk output-aligned.apk`
4. Verify:
   `apksigner verify --verbose --print-certs output-signed.apk`

The supplied APK targets Android API 36. Android 11 and newer require APK
Signature Scheme v2 or newer for apps targeting API 30+, and the resource table
must be uncompressed and 4-byte aligned. See the official Android documentation:
https://developer.android.com/about/versions/11/behavior-changes-11

## Compatibility

The patch is tied to the exact supplied ARM64 v1.105.0 native library. Other
versions or CPU architectures require fresh analysis and verified offsets.
