package app.blackbird.patches.fixes

import app.blackbird.patches.shared.Il2CppPatch
import app.blackbird.patches.shared.RET
import app.blackbird.patches.shared.insn
import app.blackbird.patches.shared.nierCompatibility
import app.blackbird.patches.shared.patchLibIl2Cpp
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.util.logging.Logger

private val logger = Logger.getLogger("PlayServicesPatch")

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

@Suppress("unused")
val playServicesPatch = resourcePatch(
    name = "Start without Google Play services",
    description = "Makes Firebase's Google Play services check pass, so the game starts on phones and emulators " +
        "without them (otherwise it stays black after the logo). Makes no difference on a phone that has them.",
) {
    category("Bug fixes")
    compatibleWith(*nierCompatibility)

    execute {
        val firebaseFile = get(FIREBASE_APP_PATH)
        if (!firebaseFile.isFile) throw PatchException("Not found: $FIREBASE_APP_PATH")

        val patched = patchLibIl2Cpp(firebaseFile, playServicesPatches, logger)
        logger.info("$patched/${playServicesPatches.size} Firebase library checks patched")
    }
}
