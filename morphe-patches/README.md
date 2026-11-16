# BlackBird Morphe patches

A [Morphe](https://github.com/MorpheApp) patch bundle for **NieR Re[in]carnation**. It does the same job as the [Colab notebook](../colab/patcher.ipynb), but inside Morphe Manager (on the phone) or Morphe Desktop. You don't need Colab, apktool or manual signing.

## Patches by category

Morphe Manager groups the patches by these categories.

| Category | Patch | Default |
|----------|-------|---------|
| Private server | [Redirect to private server](#patch-redirect-to-private-server) | On |
| Private server | [Purchases through private server](#patch-purchases-through-private-server) | On |
| Privacy | [Disable tracking and analytics](#patch-disable-tracking-and-analytics) | On |
| Bug fixes | [Bug fixes](#patch-bug-fixes) | On |
| Bug fixes | [Start without Google Play services](#patch-start-without-google-play-services) | On |
| Feature improvements | [Roulette without ads](#patch-roulette-without-ads) | On |
| Feature improvements | [Frame rate](#patch-frame-rate) | On, 30 FPS (the game's own) |
| Feature improvements | [Battle speed](#patch-battle-speed) | On, 2x (the game's own) |

## Patch: "Redirect to private server"

It does the same three things as the notebook:

| Step | File | What changes |
|------|------|--------------|
| 1 | `assets/bin/Data/Managed/Metadata/global-metadata.dat` | Rewrites the IL2CPP URL and host string literals |
| 2 | `lib/arm64-v8a/libil2cpp.so` | ARM64 patches: SSL bypass (HTTP only), plain Octo list, year 9999, gRPC port |
| 2b | `assets/bin/Data/boot.config` | New `buildDate`, so Unity extracts the patched metadata again instead of reusing the copy an earlier install left in `files/il2cpp` |
| 3 | `AndroidManifest.xml` + `res/xml/network_security_config.xml` | Allows cleartext traffic (HTTP only) |

### Options

| Option | Notebook equivalent | Notes |
|--------|---------------------|-------|
| Protocol | `protocol` | `http` or `https`. `http` also turns the game's TLS off for gRPC; `https` keeps it, so the server needs a certificate the game trusts (for example one served through Cloudflare) |
| Server host | `server_host` | Hostname or IP, with no protocol or port |
| HTTP port | `http_port` | Leave empty to use the default port |
| gRPC port | `grpc_port` | Defaults to 443 |

The patch targets the Global (`com.square_enix.android_googleplay.nierspww`) and Japan (`com.square_enix.android_googleplay.nierspjp`) releases, so Morphe Manager only offers it for those two apps. It targets version **3.7.1**, the latest APK release, and Manager warns if you pick a different version.

**Use the Global version.** Get the 3.7.1 **APK** variant from [APKMirror (Global)](https://www.apkmirror.com/apk/square-enix-co-ltd/nier-reincarnation/). The [Japanese release](https://www.apkmirror.com/apk/square-enix-co-ltd/nier-reincarnation-2/) is marked experimental: it hasn't been tested, and its offsets haven't been verified, so it may not work.

See the [main README](../README.md) for why the game is patched, including the in-app purchase changes.

The same rules as the notebook still apply. A replacement string can't be longer than the original. The `libil2cpp.so` offsets are hard-coded for version 3.7.1, so any other version will be corrupted silently.

## Patch: "Purchases through private server"

Enabled by default. Use it together with "Redirect to private server". It patches `lib/arm64-v8a/libil2cpp.so` so purchase flows reach the private server instead of Google Play billing, which no longer exists for this game: the store check is skipped, products always count as existing, the Google Play store controller stays null, purchases Google Play still holds are ignored, and the purchase confirmation dialog and the slow Google Play start-up wait are skipped.

## Patch: "Start without Google Play services"

Enabled by default. Makes Firebase's Play services check (`google_play_services::CheckAvailability` in `lib/arm64-v8a/libFirebaseCppApp-9_6_0.so`) answer "available", so the game starts on phones and emulators without Play services instead of staying black after the logo. The original bytes are checked first. It makes no difference on a phone that has Play services.

## Patch: "Disable tracking and analytics"

Enabled by default, and separate so you can turn it off. It matches the notebook's `disable_tracking` setting.

| File | What changes |
|------|--------------|
| `lib/arm64-v8a/libil2cpp.so` | Early `ret` in `Adjust.start`, `Adjust.Awake`, `Adjust.trackEvent`, `Adjust.gdprForgetMe`, `Crashlytics.Initialize`, `Crashlytics.Log`, `Crashlytics.SetUserId`, `Crashlytics.LogException`, `FirebaseAnalytics.SetAnalyticsCollectionEnabled`, `Unity.Services.Analytics.ContainerObject.Initialize`, `AnalyticsServiceInstance.Startup`, the three `AndroidFacebook` event methods and both `IronSource.init` overloads |
| `AndroidManifest.xml` | Firebase and Facebook collection flags set to off; startup providers and services of Facebook, AdMob, AppLovin, IronSource and Audience Network removed, plus the Adjust referrer receiver; `AD_ID` and install-referrer (`BIND_GET_INSTALL_REFERRER_SERVICE`) permissions removed |
| `assets/bin/Data/globalgamemanagers` | `UnityConnectSettings.m_Enabled` cleared, which stops the engine's calls to `cdp.cloud.unity3d.com` and `config.uca.cloud.unity3d.com`. Skipped with a warning unless the settings block is found exactly once (3.7.1: offset `0x138BAB8`) |

Unlike the server patch, these `libil2cpp.so` patches check the original bytes first and skip (with a warning) when they don't match, so a different game version isn't corrupted. See the [main README](../README.md#disabling-tracking-optional-on-by-default) for what each change does and its limitations.

## Patch: "Roulette without ads"

Enabled by default. It matches the notebook's `roulette_without_ads` setting. It's a bytecode patch on `com.ironsource.unity.androidbridge.AndroidBridge` (in `classes2.dex`):

| Method | New behaviour |
|--------|---------------|
| `showRewardedVideo()` and `showRewardedVideo(String)` | Call the game's listener with "rewarded" (a dummy placement), then "closed", without showing an ad |
| `init(String)` and `init(String, String[])` | Return straight away, so the IronSource SDK never starts |

The roulette only appears once the server lifts its maintenance and pays out gems; see the [main README](../README.md#roulette-without-ads-optional-on-by-default).

## Patch: "Bug fixes"

Enabled by default. Morphe only (no notebook setting). Fixes for bugs of the original game, each one an option that is on by default.

| Option | What changes |
|--------|--------------|
| Story camera edge | On a screen wider than 19.5:9 the story-room camera stopped short of the room's end while the invisible walls stop the player at the same distance, so a story trigger at the end of the room could not be reached. `libil2cpp.so` changes three instructions in `CameraObjectWithFollowCurrentActor.Clamp` (`0x3808EDC`) so the camera uses the game's own capped 19.5:9 ratio, like the walls and black bars. The original bytes are checked first; a different game version is skipped with a warning |

## Patch: "Frame rate"

Enabled by default, with the game's own 30 FPS selected, so it changes nothing until you pick a higher one. The option offers 30, 60, 90 or 120 FPS and patches three frame rate caps in `libil2cpp.so` (the startup cap, the renderer setup cap and every quality preset except power saving, which stays at 10).

## Patch: "Battle speed"

Enabled by default, with the game's own 2x selected. The option offers 2x, 3x or 4x for the fast setting of the battle speed button, which switches between 1x and that speed. It patches `SpeedController..cctor` (`kHighSpeed`) in `libil2cpp.so`.

## Building

You need JDK 17 or newer (Android Studio's bundled JBR works) and the GitHub CLI logged in, because Morphe's Gradle plugin comes from GitHub Packages.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

The bundle is written to `patches/build/libs/patches-1.0.0.mpp`.

## Using it

- **Morphe Manager (phone):** open **[Add to Morphe](https://morphe.software/add-source?github=BillyCool/BlackBirdTools/morphe-patches&name=BlackBird%20Patches)** on your phone to add this repo as a patch source and get updates automatically. You can also copy a locally built `.mpp` to the phone and add it as a local file. Then choose the NieR Re[in]carnation APK, make sure "Redirect to private server" and "Purchases through private server" are enabled, set the options, choose whether to keep "Disable tracking and analytics" and "Roulette without ads" on, then patch.
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
