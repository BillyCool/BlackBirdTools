# BlackBird Tools

Tools for patching **NieR Re[in]carnation** for Android so it connects to a community-run private server.

| Region | Package | Download | Status |
|--------|---------|----------|--------|
| Global | `com.square_enix.android_googleplay.nierspww` | [APKMirror](https://www.apkmirror.com/apk/square-enix-co-ltd/nier-reincarnation/) | **Recommended** |
| Japan  | `com.square_enix.android_googleplay.nierspjp` | [APKMirror](https://www.apkmirror.com/apk/square-enix-co-ltd/nier-reincarnation-2/) | Untested: offsets not verified, may not work |

All patches are based on version **3.7.1**, the latest APK release. Use the Global version, and download the **APK** variant (not the BUNDLE) of 3.7.1.

## Why this exists

NieR Re[in]carnation shut down in 2024. Its official servers are offline and the game can't be played officially in any way. A client patched with these tools talks to an independently developed private server instead, so the game can be preserved and played again.

### About the in-app purchase patches

Some patches touch the game's in-app purchase code. **They are not for piracy.** There is nothing left to buy: the official store and its Google Play billing backend no longer exist. Without these patches the game blocks or crashes on any purchase screen, because Google Play billing never starts up for a shut-down title. The patches skip that check so purchase flows reach the private server, which handles them however it chooses. No real-money transaction is involved or bypassed.

## What gets patched

- `global-metadata.dat`: rewrites the game's server URLs and hostnames to point at your server
- `libil2cpp.so` (ARM64):
  - SSL bypass
  - encryption passthrough
  - plain asset list
  - routing purchases to the private server
  - end-of-service date removal
  - gRPC port override
- `AndroidManifest.xml` / `network_security_config.xml`: allows cleartext traffic (HTTP only)

### Disabling tracking (optional, on by default)

The game ships with tracking and analytics SDKs that would still report to their vendors from a patched client. A separate, toggleable patch turns them off:

- **Adjust**: never started, so no install, progress or purchase events (each tagged with your game user ID) are sent
- **Firebase**: Analytics and Crashlytics collection off, game user ID and breadcrumbs no longer attached to crash reports, push token auto-registration off
- **Facebook**: app event auto-logging and ad ID collection off, SDK no longer starts at launch
- **Ad SDKs** (AdMob, AppLovin, IronSource, Facebook Audience Network): startup hooks removed. The game never shows ads, these only ran in the background
- **Advertising ID**: permission removed, so Android 13 and newer give the app an all-zero ID

Limitations:

- Firebase itself still starts, because the game calls it on boot and would crash without it. It may still contact Google once to register an install ID, but no analytics or crash data is sent.
- On Android 12 and older, apps can still read the real advertising ID. The only SDK left that would send it (Firebase Analytics) is off.
- Login still makes its Google Play Integrity and SafetyNet checks, because login waits on them. SafetyNet has been shut down by Google, and the private server sends a dummy nonce, so neither check succeeds.
- To block anything these patches miss, block the vendor hosts with DNS on your network (Adjust, `app-measurement.com`, Crashlytics, `graph.facebook.com`). Avoid blanket `googleapis.com` rules: Google Play services uses them too.

The `libil2cpp.so` patches use fixed offsets taken from the latest APK release, version 3.7.1 (Global). Other versions will silently corrupt the game library. The Japanese release hasn't been tested and its offsets haven't been verified.

## Choose a tool

| Folder | Tool | Best for |
|--------|------|----------|
| [`morphe-patches/`](./morphe-patches) | [Morphe](https://github.com/MorpheApp) patch bundle | Patching on your phone with Morphe Manager, or on PC with Morphe Desktop |
| [`colab/`](./colab) | Google Colab notebook | Patching in the browser, with nothing installed |

**[➕ Add BlackBird Patches to Morphe](https://morphe.software/add-source?github=BillyCool/BlackBirdTools/morphe-patches&name=BlackBird%20Patches)**: open this link on your phone with Morphe Manager installed to add the patches as a source. Manager then keeps them up to date.

Both patchers apply the same patches and take the same settings: protocol, server host, HTTP port and gRPC port. Tracking is turned off by default: untick "Disable tracking and analytics" in Morphe, or set `disable_tracking` to false in the notebook, to keep it.

> **Note:** a replacement URL can't be longer than the original. Use a short hostname, or leave the port out, if you hit length errors.

## Disclaimer

See [DISCLAIMER.md](./DISCLAIMER.md).
