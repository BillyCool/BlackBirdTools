# BlackBird Morphe patches

A [Morphe](https://github.com/MorpheApp) patch bundle for **NieR Re[in]carnation**. It does the same job as the [Colab notebook](../colab/patcher.ipynb), but inside Morphe Manager (on the phone) or Morphe Desktop. You don't need Colab, apktool or manual signing.

## Patch: "Redirect to private server"

It does the same three things as the notebook:

| Step | File | What changes |
|------|------|--------------|
| 1 | `assets/bin/Data/Managed/Metadata/global-metadata.dat` | Rewrites the IL2CPP URL and host string literals |
| 2 | `lib/arm64-v8a/libil2cpp.so` | ARM64 patches: SSL bypass, encryption passthrough, plain Octo list, purchases routed to the private server, year 9999, gRPC port |
| 3 | `AndroidManifest.xml` + `res/xml/network_security_config.xml` | Allows cleartext traffic (HTTP only) |

### Options

| Option | Notebook equivalent | Notes |
|--------|---------------------|-------|
| Protocol | `protocol` | `http` or `https` |
| Server host | `server_host` | Hostname or IP, with no protocol or port |
| HTTP port | `http_port` | Leave empty to use the default port |
| gRPC port | `grpc_port` | Defaults to 443 |

The patch targets the Global (`com.square_enix.android_googleplay.nierspww`) and Japan (`com.square_enix.android_googleplay.nierspjp`) releases, so Morphe Manager only offers it for those two apps. It targets version **3.7.1**, the latest APK release, and Manager warns if you pick a different version.

**Use the Global version.** Get the 3.7.1 **APK** variant from [APKMirror (Global)](https://www.apkmirror.com/apk/square-enix-co-ltd/nier-reincarnation/). The [Japanese release](https://www.apkmirror.com/apk/square-enix-co-ltd/nier-reincarnation-2/) is marked experimental: it hasn't been tested, and its offsets haven't been verified, so it may not work.

See the [main README](../README.md) for why the game is patched, including the in-app purchase changes.

The same rules as the notebook still apply. A replacement string can't be longer than the original. The `libil2cpp.so` offsets are hard-coded for version 3.7.1, so any other version will be corrupted silently.

## Patch: "Disable tracking and analytics"

Enabled by default, and separate so you can turn it off. It matches the notebook's `disable_tracking` setting.

| File | What changes |
|------|--------------|
| `lib/arm64-v8a/libil2cpp.so` | Early `ret` in `Adjust.start`, `Adjust.trackEvent`, `Adjust.gdprForgetMe`, `Crashlytics.Log`, `Crashlytics.SetUserId` and `FirebaseAnalytics.SetAnalyticsCollectionEnabled` |
| `AndroidManifest.xml` | Firebase and Facebook collection flags set to off; startup providers and services of Facebook, AdMob, AppLovin, IronSource and Audience Network removed, plus the Adjust referrer receiver; `AD_ID` permission removed |

Unlike the server patch, these `libil2cpp.so` patches check the original bytes first and skip (with a warning) when they don't match, so a different game version isn't corrupted. See the [main README](../README.md#disabling-tracking-optional-on-by-default) for what each change does and its limitations.

## Patch: "Roulette without ads"

Enabled by default. It matches the notebook's `roulette_without_ads` setting. It's a bytecode patch on `com.ironsource.unity.androidbridge.AndroidBridge` (in `classes2.dex`):

| Method | New behaviour |
|--------|---------------|
| `showRewardedVideo()` and `showRewardedVideo(String)` | Call the game's listener with "rewarded" (a dummy placement), then "closed", without showing an ad |
| `init(String)` and `init(String, String[])` | Return straight away, so the IronSource SDK never starts |

The roulette only appears once the server lifts its maintenance and pays out gems; see the [main README](../README.md#roulette-without-ads-optional-on-by-default).

## Building

You need JDK 17 or newer (Android Studio's bundled JBR works) and the GitHub CLI logged in, because Morphe's Gradle plugin comes from GitHub Packages.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

The bundle is written to `patches/build/libs/patches-1.0.0.mpp`.

## Using it

- **Morphe Manager (phone):** open **[Add to Morphe](https://morphe.software/add-source?github=BillyCool/BlackBirdTools/morphe-patches&name=BlackBird%20Patches)** on your phone to add this repo as a patch source and get updates automatically. You can also copy a locally built `.mpp` to the phone and add it as a local file. Then choose the NieR Re[in]carnation APK, make sure "Redirect to private server" is enabled, set the options, choose whether to keep "Disable tracking and analytics" and "Roulette without ads" on, then patch.
- **Morphe Desktop:** add the `.mpp` as a patch bundle and patch the same way.

The notebook expects a single APK. If you start from a split bundle (APKM or XAPK), `libil2cpp.so` must come from the arm64 split. Morphe merges splits, but this hasn't been tested.

## Releasing

Releases are built by [`.github/workflows/release-morphe-patches.yml`](../.github/workflows/release-morphe-patches.yml). To publish one, push a version tag:

```bash
git tag v1.0.1
git push origin v1.0.1
```

The workflow:
1. builds `patches-<version>.mpp` from the tagged commit;
2. publishes it as a GitHub release;
3. commits an updated [`patches-bundle.json`](./patches-bundle.json) to `main`.

Morphe Manager reads that file through the add-source link (`github=BillyCool/BlackBirdTools/morphe-patches`). Don't edit it by hand.
