# Subway Surfers animation patch (ARM64 v1.105.0)

This folder contains a reproducible patch script for the APK supplied for this
conversation. It does **not** include the proprietary game APK.

## What the patch changes

Six 32-bit ARM64 instructions in `lib/arm64-v8a/libil2cpp.so` replace the
identified animation CrossFade blend-time inputs for jump/roll paths with
zero. This removes those transition blend durations; it does not remove every
possible source of input or frame latency and cannot guarantee literal zero
latency.

The Android application ID is changed from `com.random.zurich` to
`com.random.surfer` (same-length manifest string) so the modified build can
be installed side-by-side with the original rather than colliding with its
package signature.

## Rebuild

1. Use the exact ARM64 APK version 1.105.0 that matches the offsets.
2. Run:
   `python patch_animation_delays.py original.apk output-unsigned.apk`
3. Sign the output APK with a local signing key (for example, using
   `keytool` and `jarsigner`).
4. Verify the APK signature before installing.

Different game versions or ARM architectures need a fresh disassembly and
newly verified offsets. Do not use this patch on another build.
