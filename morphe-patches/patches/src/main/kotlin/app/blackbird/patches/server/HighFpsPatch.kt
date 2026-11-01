package app.blackbird.patches.server

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.util.logging.Logger

private val logger = Logger.getLogger("HighFpsPatch")

private const val TARGET_FPS = 60

/** MOVZ W<rd>, #imm for a 16-bit immediate. */
private fun movzW(rd: Int, imm: Int) = 0x52800000 or (imm shl 5) or rd

private val ORR_W0_30 = insn(0x321F0FE0)   // orr w0, wzr, #0x1e (mov w0, #30)
private val ORR_W9_30 = insn(0x321F0FE9)   // orr w9, wzr, #0x1e (mov w9, #30)

private val fpsPatches = listOf(
    Il2CppPatch(
        "Generator.OnlyWhenStarting (targetFrameRate)",
        "Startup frame rate cap, 30 -> $TARGET_FPS",
        0x2E5BE0C, insn(movzW(0, TARGET_FPS)), ORR_W0_30,
    ),
    Il2CppPatch(
        "Generator.SetupRenderer (targetFrameRate)",
        "Renderer setup frame rate cap, 30 -> $TARGET_FPS",
        0x2E5C31C, insn(movzW(0, TARGET_FPS)), ORR_W0_30,
    ),
    Il2CppPatch(
        "PerformanceSetting.Config..cctor (UnSavePowerTargetRenderFrameRate)",
        "Frame rate of every quality preset except power saving, 30 -> $TARGET_FPS",
        0x2C7AC80, insn(movzW(9, TARGET_FPS)), ORR_W9_30,
    ),
)

@Suppress("unused")
val highFpsPatch = resourcePatch(
    name = "60 FPS",
    description = "Raises the frame rate cap from 30 to $TARGET_FPS. The power saving presets stay at 10.",
) {
    compatibleWith(*nierCompatibility)

    execute {
        val libFile = get(LIBIL2CPP_PATH)
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        val patched = patchLibIl2Cpp(libFile, fpsPatches, logger)
        logger.info("$patched/${fpsPatches.size} libil2cpp.so methods patched")
    }
}
