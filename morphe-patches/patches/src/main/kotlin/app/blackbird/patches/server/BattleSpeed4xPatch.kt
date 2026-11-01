package app.blackbird.patches.server

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.util.logging.Logger

private val logger = Logger.getLogger("BattleSpeed4xPatch")

// SpeedController..cctor stores kHighSpeed from w11, loaded just before the stores.
private val ORR_W11_2_0F = insn(0x320203EB)   // orr w11, wzr, #0x40000000 (2.0f)
private val MOVZ_W11_4_0F = insn(0x52A8100B) // movz w11, #0x4080, lsl #16 (4.0f; not an orr bitmask immediate)

private val speedPatches = listOf(
    Il2CppPatch(
        "SpeedController..cctor (kHighSpeed)",
        "Battle high speed multiplier, 2.0 -> 4.0",
        0x3420028, MOVZ_W11_4_0F, ORR_W11_2_0F,
    ),
)

@Suppress("unused")
val battleSpeed4xPatch = resourcePatch(
    name = "Battle speed 4x",
    description = "The battle speed button switches between 1x and 4x instead of 1x and 2x.",
) {
    compatibleWith(*nierCompatibility)

    execute {
        val libFile = get(LIBIL2CPP_PATH)
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        val patched = patchLibIl2Cpp(libFile, speedPatches, logger)
        logger.info("$patched/${speedPatches.size} libil2cpp.so methods patched")
    }
}
