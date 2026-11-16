package app.blackbird.patches.server

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import java.util.logging.Logger

private val logger = Logger.getLogger("FrameRatePatch")

/** MOVZ W<rd>, #imm for a 16-bit immediate. */
private fun movzW(rd: Int, imm: Int) = 0x52800000 or (imm shl 5) or rd

private val ORR_W0_30 = insn(0x321F0FE0)   // orr w0, wzr, #0x1e (mov w0, #30)
private val ORR_W9_30 = insn(0x321F0FE9)   // orr w9, wzr, #0x1e (mov w9, #30)

private fun fpsPatches(fps: Int) = listOf(
    Il2CppPatch(
        "Generator.OnlyWhenStarting (targetFrameRate)",
        "Startup frame rate cap, 30 -> $fps",
        0x2E5BE0C, insn(movzW(0, fps)), ORR_W0_30,
    ),
    Il2CppPatch(
        "Generator.SetupRenderer (targetFrameRate)",
        "Renderer setup frame rate cap, 30 -> $fps",
        0x2E5C31C, insn(movzW(0, fps)), ORR_W0_30,
    ),
    Il2CppPatch(
        "PerformanceSetting.Config..cctor (UnSavePowerTargetRenderFrameRate)",
        "Frame rate of every quality preset except power saving, 30 -> $fps",
        0x2C7AC80, insn(movzW(9, fps)), ORR_W9_30,
    ),
)

@Suppress("unused")
val frameRatePatch = resourcePatch(
    name = "Frame rate",
    description = "Raises the frame rate cap above the game's 30. The power saving presets stay at 10.",
) {
    compatibleWith(*nierCompatibility)

    val frameRate = stringOption(
        key = "frameRate",
        default = "30",
        values = mapOf("30 FPS" to "30", "60 FPS" to "60", "90 FPS" to "90", "120 FPS" to "120"),
        title = "Frame rate",
        description = "The frame rate cap. 30 is the game's own.",
        required = true,
    ) { it in setOf("30", "60", "90", "120") }

    execute {
        val libFile = get(LIBIL2CPP_PATH)
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        val patches = fpsPatches(frameRate.value!!.toInt())
        val patched = patchLibIl2Cpp(libFile, patches, logger)
        logger.info("$patched/${patches.size} libil2cpp.so methods patched")
    }
}
