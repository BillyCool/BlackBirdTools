package app.blackbird.patches.server

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import java.util.logging.Logger

private val logger = Logger.getLogger("BattleSpeedPatch")

// SpeedController..cctor stores kHighSpeed from w11, loaded just before the stores.
private val ORR_W11_2_0F = insn(0x320203EB)   // orr w11, wzr, #0x40000000 (2.0f)

/** MOVZ W11, #(high half of the float), LSL #16. 2.0f, 3.0f and 4.0f only need the high half. */
private fun movzW11Float(highHalf: Int) = 0x52A00000 or (highHalf shl 5) or 11

private val FLOAT_HIGH_HALVES = mapOf("2" to 0x4000, "3" to 0x4040, "4" to 0x4080)

private fun speedPatches(speed: String) = listOf(
    Il2CppPatch(
        "SpeedController..cctor (kHighSpeed)",
        "Battle high speed multiplier, 2.0 -> $speed.0",
        0x3420028, insn(movzW11Float(FLOAT_HIGH_HALVES.getValue(speed))), ORR_W11_2_0F,
    ),
)

@Suppress("unused")
val battleSpeedPatch = resourcePatch(
    name = "Battle speed",
    description = "The battle speed button switches between 1x and a faster speed instead of 1x and 2x.",
) {
    compatibleWith(*nierCompatibility)

    val speed = stringOption(
        key = "speed",
        default = "2",
        values = mapOf("2x" to "2", "3x" to "3", "4x" to "4"),
        title = "Fast speed",
        description = "The speed the battle speed button switches to. 2x is the game's own.",
        required = true,
    ) { it in FLOAT_HIGH_HALVES }

    execute {
        val libFile = get(LIBIL2CPP_PATH)
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        val patches = speedPatches(speed.value!!)
        val patched = patchLibIl2Cpp(libFile, patches, logger)
        logger.info("$patched/${patches.size} libil2cpp.so methods patched")
    }
}
