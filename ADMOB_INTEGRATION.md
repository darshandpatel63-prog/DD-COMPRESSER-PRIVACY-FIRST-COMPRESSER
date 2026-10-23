# DD-Compressor — AdMob integration note

## Replit HSDP dependency fix (isolated branch)

The Replit-provided source ZIP explains this change as a likely fix for the startup crash:

- The project uses Google Mobile Ads SDK `play-services-ads:25.5.0`.
- The supplied analysis says that dependency metadata resolves HSDP `2.0.1` transitively.
- The cited Google Android release notes reportedly include an `HsdpShimActivity` crash fix in HSDP `2.2.0`.
- The supplied APK reportedly contains `HsdpShimActivity`.

Accordingly, `android-native/app/build.gradle` explicitly adds:

```gradle
implementation 'com.google.android.play:hsdp:2.2.0'
```

This is a plausible dependency-level fix, not a confirmed diagnosis: a device crash log and a successful build/runtime test are still needed to establish the cause and verify the result.

This branch is based on the restored main baseline and contains the targeted HSDP change. Do not merge into `main` unless the user explicitly asks after testing.
