package app.blackbird.patches.server

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.intOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element
import java.util.logging.Logger

private const val METADATA_PATH = "assets/bin/Data/Managed/Metadata/global-metadata.dat"
private const val BOOT_CONFIG_PATH = "assets/bin/Data/boot.config"
private const val NETWORK_SECURITY_CONFIG_ATTRIBUTE = "android:networkSecurityConfig"

private val logger = Logger.getLogger("PrivateServerPatch")

private const val FIREBASE_APP_PATH = "lib/arm64-v8a/libFirebaseCppApp-9_6_0.so"

/**
 * google_play_services::CheckAvailability in the Firebase C++ library answers "available" (0), as on a phone that has
 * Play services. Without it the game's Firebase start-up check never passes and the game stays black after the logo.
 * The text segment is mapped at file offset == address, so the patch writes at the symbol's address.
 */
private val playServicesPatches = listOf(
    Il2CppPatch(
        "google_play_services::CheckAvailability",
        "Firebase start-up: report Google Play services as available (mov w0, wzr; ret)",
        0x1800EC, insn(0x2A1F03E0, RET), insn(0xA9BD57F6.toInt(), 0xA9014FF4.toInt()),
    ),
)

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
    compatibleWith(*nierCompatibility)

    val protocol = stringOption(
        key = "protocol",
        default = "http",
        values = mapOf("HTTP (cleartext)" to "http", "HTTPS" to "https"),
        title = "Protocol",
        description = "http also enables cleartext traffic in the manifest and turns the game's TLS off for gRPC. " +
            "https keeps TLS on, so the server needs a certificate the game trusts (e.g. behind Cloudflare).",
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

    val noPlayServices = booleanOption(
        key = "noPlayServices",
        default = true,
        title = "Start without Google Play services",
        description = "Makes Firebase's Google Play services check pass, so the game starts on phones and emulators " +
            "without them (otherwise it stays black after the logo). Makes no difference on a phone that has them.",
        required = true,
    )

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

        // Unity copies the IL2CPP data (the metadata) out of the APK once and only copies it again when the build
        // date in boot.config differs from the one it stored (unity.ver). Without a new date a reinstalled, patched
        // APK keeps running the metadata an earlier install extracted.
        stampBuildDate(get(BOOT_CONFIG_PATH), logger)

        // [2] libil2cpp.so binary patches.
        // https keeps the game's own TLS for gRPC (checked against the roots it bundles), so the server must present a
        // certificate those roots trust, e.g. one issued through Cloudflare. Only http bypasses TLS.
        val basePatches = if (cfg.protocol == "https") {
            baseIl2CppPatches.filter { it.name != SSL_BYPASS_PATCH }
        } else {
            baseIl2CppPatches
        }
        val il2cppPatches = basePatches + portPatches(cfg.grpcPort)
        val patchedMethods = patchLibIl2Cpp(libFile, il2cppPatches, logger)
        logger.info("$patchedMethods/${il2cppPatches.size} libil2cpp.so methods patched")

        // [2b] Firebase's Play services check (a second library).
        if (noPlayServices.value == true) {
            val firebaseFile = get(FIREBASE_APP_PATH)
            if (!firebaseFile.isFile) throw PatchException("Not found: $FIREBASE_APP_PATH")
            val patchedChecks = patchLibIl2Cpp(firebaseFile, playServicesPatches, logger)
            logger.info("$patchedChecks/${playServicesPatches.size} Firebase library checks patched")
        }

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
