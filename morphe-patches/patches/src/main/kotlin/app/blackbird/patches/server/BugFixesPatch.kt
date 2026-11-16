package app.blackbird.patches.server

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import java.util.logging.Logger

private val logger = Logger.getLogger("BugFixesPatch")

// Story camera edge. CameraObjectWithFollowCurrentActor.Clamp (0x3808E00) keeps the story camera inside the room with a
// half width of orthographicSize * Screen.width / Screen.height. The screen-edge walls (ScreenEdgeWallController) and the
// black bars use the game's own capped ratio, CalculatorCamera.GetCurrentAspectRatioWithoutBlackBelt (0x2771DD0, 19.5:9 at
// most). On a screen wider than 19.5:9 the camera therefore stops short of the room's end, the wall stops the player the
// same distance short, and a story trigger in that strip can't be reached. The three words below make Clamp use the
// capped ratio too (s9 holds orthographicSize, and the float result of the call comes back in s0):
//
//   0x3808EDC  bl Screen.get_width       ->  bl GetCurrentAspectRatioWithoutBlackBelt
//   0x3808EE0  mov w20, w0               ->  fmul s0, s9, s0
//   0x3808EE4  mov x0, xzr               ->  b 0x3808EFC (the clamp, past the get_height / divide code)
private val storyCameraEdgePatches = listOf(
    Il2CppPatch(
        "CameraObjectWithFollowCurrentActor.Clamp (half width)",
        "Story rooms: the camera stops where the screen-edge walls do on screens wider than 19.5:9",
        0x3808EDC,
        insn(0x97BDA3BD.toInt(), 0x1E200920, 0x14000006),
        insn(0x9455BBA7.toInt(), 0x2A0003F4, 0xAA1F03E0.toInt()),
    ),
)

/**
 * Fixes for bugs of the original game that matter on a private server. Each fix is an option, on by default, so
 * further fixes can be added here and switched off one by one.
 */
@Suppress("unused")
val bugFixesPatch = resourcePatch(
    name = "Bug fixes",
    description = "Fixes for bugs of the original game. Each fix can be switched off in the options.",
) {
    compatibleWith(*nierCompatibility)

    val storyCameraEdge = booleanOption(
        key = "storyCameraEdge",
        default = true,
        title = "Story camera edge",
        description = "On screens wider than 19.5:9 the story-room camera stopped short of the room's end, so a " +
            "story trigger at the end of the room could not be reached.",
        required = true,
    )

    execute {
        val libFile = get(LIBIL2CPP_PATH)
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        val selected = if (storyCameraEdge.value == true) storyCameraEdgePatches else emptyList()
        val patched = patchLibIl2Cpp(libFile, selected, logger)
        logger.info("$patched/${selected.size} libil2cpp.so bug fix methods patched")
    }
}
