# Colab APK patcher

[`patcher.ipynb`](./patcher.ipynb) is a self-contained Google Colab notebook that patches a NieR Re[in]carnation APK to use a private server. It installs its own tools (apktool and the Android build tools), patches the APK, re-signs it, and downloads the result. You don't need to install anything locally.

## Usage

1. Open [Google Colab](https://colab.research.google.com) and upload `patcher.ipynb`.
2. Fill in the settings at the top of the code cell:
   - `protocol`: `http` for plain connections, `https` for TLS-terminated endpoints
   - `server_host`: your server's hostname or IP, without the protocol (for example `192.168.1.1`)
   - `http_port`: leave empty for the default port (80 or 443, depending on the protocol)
   - `grpc_port`: leave empty for 443; set it only if your server uses a non-standard gRPC port
   - `apk_source`: `upload` to upload your own APK, or `url` to download one
   - `apk_url`: where to download the APK from (only used when `apk_source` is `url`)
3. Run the cell. The patched APK downloads automatically when it's done.

The notebook expects a single (non-split) APK of version **3.7.1**. Use the Global release (`com.square_enix.android_googleplay.nierspww`). The Japanese release is untested, and its offsets haven't been verified.

See the [main README](../README.md) for what gets patched and why.
