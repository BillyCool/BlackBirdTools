package app.blackbird.patches.tracking

import app.blackbird.patches.shared.Il2CppPatch
import app.blackbird.patches.shared.LIBIL2CPP_PATH
import app.blackbird.patches.shared.RET
import app.blackbird.patches.shared.insn
import app.blackbird.patches.shared.nierCompatibility
import app.blackbird.patches.shared.patchLibIl2Cpp
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("DisableTrackingPatch")

private const val ANDROID_NAME = "android:name"
private const val ANDROID_VALUE = "android:value"

/** Meta-data flags that switch off SDKs which start from the manifest, before any game code runs. */
private val TRACKING_META_DATA = listOf(
    // Firebase. "deactivated" is permanent and overrides the game's own SetAnalyticsCollectionEnabled call.
    "firebase_analytics_collection_deactivated" to "true",
    "firebase_data_collection_default_enabled" to "false",
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
    "com.facebook.FacebookContentProvider",
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

private const val INSTALL_REFERRER_PERMISSION = "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE"

private val REMOVED_PERMISSIONS = setOf(AD_ID_PERMISSION, INSTALL_REFERRER_PERMISSION)

private const val GLOBAL_GAME_MANAGERS_PATH = "assets/bin/Data/globalgamemanagers"

private const val CONNECT_URL = "https://api.uca.cloud.unity3d.com/v1/events"

// UnityConnectSettings: m_Enabled = 1, m_TestMode = 0, two bytes of padding, then the length-prefixed event URL.
private val UNITY_CONNECT_SETTINGS: ByteArray =
    byteArrayOf(1, 0, 0, 0) + intLe(CONNECT_URL.length) + CONNECT_URL.toByteArray(Charsets.US_ASCII)

private fun intLe(value: Int) = ByteArray(4) { (value ushr (8 * it)).toByte() }

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
    Il2CppPatch(
        "AnalyticsServiceInstance.Startup", "Unity Analytics: never start collecting",
        0x52B0924, insn(RET), insn(0xD10283FF.toInt(), 0xA9046FFC.toInt(), 0xA90567FA.toInt(), 0xA9065FF8.toInt()),
    ),
    Il2CppPatch(
        "Crashlytics.Initialize", "Crashlytics: never initialise",
        0x538D0D8, insn(RET), insn(0xF81E0FF3.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0xD00130B3.toInt()),
    ),
    Il2CppPatch(
        "Crashlytics.LogException", "Crashlytics: drop exceptions",
        0x538D28C, insn(RET), insn(0xA9BE4FF4.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0xD00130B4.toInt()),
    ),
    Il2CppPatch(
        "AndroidFacebook.AppEventsLogEvent", "Facebook: drop app events",
        0x4BF983C, insn(RET), insn(0xF81C0FF7.toInt(), 0xA90157F6.toInt(), 0xA9024FF4.toInt(), 0xA9037BFD.toInt()),
    ),
    Il2CppPatch(
        "AndroidFacebook.AppEventsLogPurchase", "Facebook: drop purchase events",
        0x4BF99A0, insn(RET), insn(0xD10103FF.toInt(), 0xA90157F6.toInt(), 0xA9024FF4.toInt(), 0xA9037BFD.toInt()),
    ),
    Il2CppPatch(
        "AndroidFacebook.ActivateApp", "Facebook: skip app activation",
        0x4BF9BC8, insn(RET), insn(0xA9BE4FF4.toInt(), 0xA9017BFD.toInt(), 0x910043FD.toInt(), 0xD0016D34.toInt()),
    ),
    Il2CppPatch(
        "Adjust.Awake", "Adjust: skip automatic start-up",
        0x3FE1FC0, insn(RET), insn(0xF81C0FF7.toInt(), 0xA90157F6.toInt(), 0xA9024FF4.toInt(), 0xA9037BFD.toInt()),
    ),
    Il2CppPatch(
        "IronSource.init", "ironSource: never initialise",
        0x3291E24, insn(RET), insn(0xF81D0FF5.toInt(), 0xA9014FF4.toInt(), 0xA9027BFD.toInt(), 0x910083FD.toInt()),
    ),
    Il2CppPatch(
        "IronSource.init(adUnits)", "ironSource: never initialise (ad units)",
        0x3291EE8, insn(RET), insn(0xA9BD57F6.toInt(), 0xA9014FF4.toInt(), 0xA9027BFD.toInt(), 0x910083FD.toInt()),
    ),
)

@Suppress("unused")
val disableTrackingPatch = resourcePatch(
    name = "Disable tracking and analytics",
    description = "Turns off Adjust, Firebase Analytics and Crashlytics, Facebook app events, the " +
        "unused ad SDKs and the engine's Unity Connect contact. None of them are useful on a private server.",
) {
    category("Privacy")
    compatibleWith(*nierCompatibility)

    execute {
        val libFile = get(LIBIL2CPP_PATH)
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        val patched = patchLibIl2Cpp(libFile, trackingIl2CppPatches, logger)
        logger.info("$patched/${trackingIl2CppPatches.size} tracking methods patched in libil2cpp.so")

        document("AndroidManifest.xml").use { disableTrackingInManifest(it, logger) }

        val settingsFile = get(GLOBAL_GAME_MANAGERS_PATH)
        if (!settingsFile.isFile) throw PatchException("Not found: $GLOBAL_GAME_MANAGERS_PATH")
        disableUnityConnect(settingsFile, logger)
    }
}

/** Sets the tracking meta-data flags and removes tracking components and the AD_ID and install-referrer permissions. */
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
        if (permission.getAttribute(ANDROID_NAME) in REMOVED_PERMISSIONS) {
            manifest.removeChild(permission)
            logger.info("removed <uses-permission> ${permission.getAttribute(ANDROID_NAME)}")
        }
    }
}

/** Direct child elements with [tag], copied to a list so callers can remove them while iterating. */
private fun Element.childElements(tag: String): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }
}

/** Clears UnityConnectSettings.m_Enabled. Skips with a warning unless the settings block is found exactly once. */
internal fun disableUnityConnect(file: File, logger: Logger): Boolean {
    val data = file.readBytes()
    val first = indexOfBytes(data, UNITY_CONNECT_SETTINGS, 0)
    if (first < 0 || indexOfBytes(data, UNITY_CONNECT_SETTINGS, first + 1) >= 0) {
        logger.warning("SKIP Unity Connect: settings block not found exactly once (different game version?)")
        return false
    }
    data[first] = 0
    file.writeBytes(data)
    logger.info("Unity Connect disabled (m_Enabled at 0x${first.toString(16)})")
    return true
}

private fun indexOfBytes(haystack: ByteArray, needle: ByteArray, from: Int): Int {
    val last = haystack.size - needle.size
    outer@ for (i in from..last) {
        for (j in needle.indices) {
            if (haystack[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}
