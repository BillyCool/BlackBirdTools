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

**Use the Global version.** The Japanese release is marked experimental: it hasn't been tested, and its offsets haven't been verified, so it may not work.

See the [main README](../README.md) for why the game is patched, including the in-app purchase changes.

The same rules as the notebook still apply. A replacement string can't be longer than the original. The `libil2cpp.so` offsets are hard-coded for version 3.7.1, so any other version will be corrupted silently.

## Building

You need JDK 17 or newer (Android Studio's bundled JBR works) and the GitHub CLI logged in, because Morphe's Gradle plugin comes from GitHub Packages.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

The bundle is written to `patches/build/libs/patches-1.0.0.mpp`.

## Using it

- **Morphe Manager (phone):** copy the `.mpp` to the phone and add it as a patch source from a local file. Choose the NieR Re[in]carnation APK, make sure "Redirect to private server" is enabled, set the options, then patch.
- **Morphe Desktop:** add the `.mpp` as a patch bundle and patch the same way.

The notebook expects a single APK. If you start from a split bundle (APKM or XAPK), `libil2cpp.so` must come from the arm64 split. Morphe merges splits, but this hasn't been tested.

## Publishing as a Manager source (optional)

To get auto-updates in Manager, this folder needs to be its own GitHub repo built from [morphe-patches-template](https://github.com/MorpheApp/morphe-patches-template), which includes the release workflow. It's left out here because that workflow assumes the patches sit at the repo root.
