package app.blackbird.patches.server

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.intOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element
import java.util.logging.Logger

private const val APP_NAME = "NieR Re[in]carnation"

// Latest APK release. The libil2cpp.so offsets in LibIl2Cpp.kt are taken from it.
private const val APP_VERSION = "3.7.1"

// Background of the launcher icon, used by Manager for the placeholder icon and APKMirror button.
private const val APP_ICON_COLOR = 0xF0ECE5

private const val METADATA_PATH = "assets/bin/Data/Managed/Metadata/global-metadata.dat"
private const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"
private const val NETWORK_SECURITY_CONFIG_ATTRIBUTE = "android:networkSecurityConfig"

private val logger = Logger.getLogger("PrivateServerPatch")

/**
 * Port of colab/patcher.ipynb. Patches the IL2CPP metadata strings, the ARM64 libil2cpp.so
 * and, for plain HTTP, the manifest network security config.
 */
@Suppress("unused")
val privateServerPatch = resourcePatch(
    name = "Redirect to private server",
    description = "Redirects all network traffic to a private server and routes in-app " +
        "purchases to it, since the official servers and store shut down in 2024.",
) {
    compatibleWith(
        Compatibility(
            packageName = "com.square_enix.android_googleplay.nierspww",
            name = "$APP_NAME (Global)",
            appIconColor = APP_ICON_COLOR,
            targets = listOf(AppTarget(version = APP_VERSION)),
        ),
        // Offsets were found on the Global build and have not been verified against Japan.
        Compatibility(
            packageName = "com.square_enix.android_googleplay.nierspjp",
            name = "$APP_NAME (Japan)",
            appIconColor = APP_ICON_COLOR,
            targets = listOf(
                AppTarget(
                    version = APP_VERSION,
                    // Explicit so the primary constructor is used; a legacy overload drops description.
                    versionCodes = null,
                    isExperimental = true,
                    description = "Untested: offsets are not verified for this release. Use the Global version.",
                ),
            ),
        ),
    )

    val protocol = stringOption(
        key = "protocol",
        default = "http",
        values = mapOf("HTTP (cleartext)" to "http", "HTTPS" to "https"),
        title = "Protocol",
        description = "http also enables cleartext traffic in the manifest.",
        required = true,
    ) { it == "http" || it == "https" }

    val serverHost = stringOption(
        key = "serverHost",
        default = "192.168.1.1",
        title = "Server host",
        description = "Hostname or IP of your server, without protocol or port.",
        required = true,
    ) { !it.isNullOrBlank() && !it.contains("://") && !it.contains(' ') }

    val httpPort = intOption(
        key = "httpPort",
        title = "HTTP port",
        description = "Leave empty for the default port (80 or 443 depending on protocol).",
    ) { it == null || it in 1..65535 }

    val grpcPort = intOption(
        key = "grpcPort",
        default = 443,
        title = "gRPC port",
        description = "Only change this if your server uses a non-standard gRPC port.",
        required = true,
    ) { it != null && it in 1..65535 }

    execute {
        val cfg = ServerConfig(
            protocol = protocol.value!!,
            host = serverHost.value!!.trim(),
            httpPort = httpPort.value,
            grpcPort = grpcPort.value!!,
        )
        logger.info("Patching for server ${cfg.host} (web=${cfg.webBaseUrl}, gRPC=${cfg.host}:${cfg.grpcPort})")

        val metadataFile = get(METADATA_PATH)
        val libFile = get(LIBIL2CPP_PATH)
        if (!metadataFile.isFile) throw PatchException("Not found: $METADATA_PATH")
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        // [1] global-metadata.dat string literals.
        val replacements = buildReplacements(cfg)
        val patchedStrings = patchMetadataStrings(metadataFile, replacements, logger)
        logger.info("$patchedStrings/${replacements.size} metadata strings patched")

        // [2] libil2cpp.so binary patches.
        val il2cppPatches = baseIl2CppPatches + portPatches(cfg.grpcPort)
        val patchedMethods = patchLibIl2Cpp(libFile, il2cppPatches, logger)
        logger.info("$patchedMethods/${il2cppPatches.size} libil2cpp.so methods patched")

        // [3] + [4] Cleartext traffic, only needed for plain HTTP.
        if (cfg.protocol == "http") {
            var configName = "network_security_config"
            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application").item(0) as? Element
                    ?: throw PatchException("Could not find <application> tag in AndroidManifest.xml")

                if (application.hasAttribute(NETWORK_SECURITY_CONFIG_ATTRIBUTE)) {
                    // Overwrite the config the app already references instead of adding a second one.
                    configName = application.getAttribute(NETWORK_SECURITY_CONFIG_ATTRIBUTE).substringAfter('/')
                } else {
                    application.setAttribute(NETWORK_SECURITY_CONFIG_ATTRIBUTE, "@xml/$configName")
                }
            }

            get("res/xml").resolve("$configName.xml").apply {
                parentFile.mkdirs()
                writeText(NETWORK_SECURITY_CONFIG)
            }
        } else {
            logger.info("HTTPS selected; skipping cleartext network security changes")
        }
    }
}

private val NETWORK_SECURITY_CONFIG = """
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
""".trimStart()
