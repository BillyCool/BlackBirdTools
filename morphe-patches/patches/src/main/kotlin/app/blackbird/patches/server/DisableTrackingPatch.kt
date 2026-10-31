package app.blackbird.patches.server

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.logging.Logger

private val logger = Logger.getLogger("DisableTrackingPatch")

private const val ANDROID_NAME = "android:name"
private const val ANDROID_VALUE = "android:value"

/** Meta-data flags that switch off SDKs which start from the manifest, before any game code runs. */
private val TRACKING_META_DATA = listOf(
    // Firebase. "deactivated" is permanent and overrides the game's own SetAnalyticsCollectionEnabled call.
    "firebase_analytics_collection_deactivated" to "true",
    "firebase_analytics_collection_enabled" to "false",
    "google_analytics_adid_collection_enabled" to "false",
    "firebase_crashlytics_collection_enabled" to "false",
    "firebase_messaging_auto_init_enabled" to "false",
    // Facebook app events.
    "com.facebook.sdk.AutoInitEnabled" to "false",
    "com.facebook.sdk.AutoLogAppEventsEnabled" to "false",
    "com.facebook.sdk.AdvertiserIDCollectionEnabled" to "false",
)

/**
 * Startup hooks of SDKs the game never calls (ads) or only needs for Facebook account transfer,
 * which can't work on a private server. Firebase providers stay: the game calls Firebase at boot.
 */
private val TRACKING_COMPONENTS = setOf(
    "com.facebook.internal.FacebookInitProvider",
    "com.facebook.ads.AudienceNetworkContentProvider",
    "com.google.android.gms.ads.MobileAdsInitProvider",
    "com.google.android.gms.ads.AdService",
    "com.applovin.sdk.AppLovinInitProvider",
    "com.applovin.impl.sdk.utils.AppKilledService",
    "com.applovin.impl.adview.activity.FullscreenAdService",
    "com.ironsource.lifecycle.IronsourceLifecycleProvider",
    "com.adjust.sdk.AdjustReferrerReceiver",
)

private val COMPONENT_TAGS = listOf("provider", "service", "receiver")

// Without it, Android 13+ returns an all-zero advertising ID to every SDK.
private const val AD_ID_PERMISSION = "com.google.android.gms.permission.AD_ID"

private val trackingIl2CppPatches = listOf(
    Il2CppPatch(
        "Adjust.start", "Adjust: never start the SDK",
        0x3FE22DC, insn(RET), insn(0xA9BE4FF4.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0xD001CDB4.toInt()),
    ),
    Il2CppPatch(
        "Adjust.trackEvent", "Adjust: drop events",
        0x3FE3810, insn(RET), insn(0xA9BE4FF4.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0xB001CDB4.toInt()),
    ),
    Il2CppPatch(
        "Adjust.gdprForgetMe", "Adjust: skip forget-me request",
        0x3FE4368, insn(RET), insn(0xF81E0FF3.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0x9001CDB3.toInt()),
    ),
    Il2CppPatch(
        "Crashlytics.Log", "Crashlytics: drop breadcrumbs",
        0x538D19C, insn(RET), insn(0xA9BE4FF4.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0xD00130B4.toInt()),
    ),
    Il2CppPatch(
        "Crashlytics.SetUserId", "Crashlytics: don't attach the game user ID",
        0x538D214, insn(RET), insn(0xA9BE4FF4.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0xD00130B4.toInt()),
    ),
    Il2CppPatch(
        "FirebaseAnalytics.SetAnalyticsCollectionEnabled", "Analytics: game can't turn collection back on",
        0x53E84A0, insn(RET), insn(0xA9BE4FF4.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0x90012DF4.toInt()),
    ),
    // Runs at startup and creates the Unity Gaming Services Analytics object, which logs "No Cloud ProjectID Found".
    Il2CppPatch(
        "Unity.Services.Analytics.ContainerObject.Initialize", "Unity Analytics: never create the analytics object",
        0x52B383C, insn(RET), insn(0xA9BE4FF4.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0xF0013773.toInt()),
    ),
)

@Suppress("unused")
val disableTrackingPatch = resourcePatch(
    name = "Disable tracking and analytics",
    description = "Turns off Adjust, Firebase Analytics and Crashlytics, Facebook app events and the " +
        "unused ad SDKs. None of them are useful on a private server.",
) {
    compatibleWith(*nierCompatibility)

    execute {
        val libFile = get(LIBIL2CPP_PATH)
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        val patched = patchLibIl2Cpp(libFile, trackingIl2CppPatches, logger)
        logger.info("$patched/${trackingIl2CppPatches.size} tracking methods patched in libil2cpp.so")

        document("AndroidManifest.xml").use { disableTrackingInManifest(it, logger) }
    }
}

/** Sets the tracking meta-data flags and removes tracking components and the AD_ID permission. */
internal fun disableTrackingInManifest(document: Document, logger: Logger) {
    val manifest = document.documentElement
    val application = document.getElementsByTagName("application").item(0) as? Element
        ?: throw PatchException("Could not find <application> tag in AndroidManifest.xml")

    for ((name, value) in TRACKING_META_DATA) {
        val existing = application.childElements("meta-data").firstOrNull { it.getAttribute(ANDROID_NAME) == name }
        val element = existing ?: document.createElement("meta-data").also {
            it.setAttribute(ANDROID_NAME, name)
            application.appendChild(it)
        }
        element.setAttribute(ANDROID_VALUE, value)
        logger.info("meta-data $name = $value" + if (existing == null) " (added)" else "")
    }

    for (tag in COMPONENT_TAGS) {
        for (component in application.childElements(tag)) {
            if (component.getAttribute(ANDROID_NAME) in TRACKING_COMPONENTS) {
                application.removeChild(component)
                logger.info("removed <$tag> ${component.getAttribute(ANDROID_NAME)}")
            }
        }
    }

    for (permission in manifest.childElements("uses-permission")) {
        if (permission.getAttribute(ANDROID_NAME) == AD_ID_PERMISSION) {
            manifest.removeChild(permission)
            logger.info("removed <uses-permission> $AD_ID_PERMISSION")
        }
    }
}

/** Direct child elements with [tag], copied to a list so callers can remove them while iterating. */
private fun Element.childElements(tag: String): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }
}
