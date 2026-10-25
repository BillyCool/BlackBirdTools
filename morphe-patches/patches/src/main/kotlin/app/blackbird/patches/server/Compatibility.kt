package app.blackbird.patches.server

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

private const val APP_NAME = "NieR Re[in]carnation"

// Latest APK release. The libil2cpp.so offsets in LibIl2Cpp.kt are taken from it.
private const val APP_VERSION = "3.7.1"

// Background of the launcher icon, used by Manager for the placeholder icon and APKMirror button.
private const val APP_ICON_COLOR = 0xF0ECE5

internal const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"

/** Apps every patch in this bundle targets. */
internal val nierCompatibility = arrayOf(
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
