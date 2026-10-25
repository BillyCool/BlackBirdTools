package app.blackbird.patches.server

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import java.util.logging.Logger

private val logger = Logger.getLogger("RouletteWithoutAdsPatch")

private const val ANDROID_BRIDGE = "Lcom/ironsource/unity/androidbridge/AndroidBridge;"
private const val LISTENER = "Lcom/ironsource/unity/androidbridge/UnityRewardedVideoListener;"

// IronSourceUtils.getPlacementFromObject needs all three keys, or the game crashes on a null placement.
private const val PLACEMENT_JSON =
    """{\"placement_name\":\"DefaultRewardedVideo\",\"placement_reward_name\":\"Gems\",\"placement_reward_amount\":\"1\"}"""

/**
 * Tells the game the ad was watched, then closed, without showing one. Rewarded must come first:
 * the closed handler waits up to 3 s for the rewarded flag, which the game sets before its first await.
 */
private val REPORT_AD_WATCHED = """
    iget-object v0, p0, $ANDROID_BRIDGE->mUnityRewardedVideoListener:$LISTENER
    if-eqz v0, :done
    const-string v1, "$PLACEMENT_JSON"
    invoke-interface {v0, v1}, $LISTENER->onRewardedVideoAdRewarded(Ljava/lang/String;)V
    invoke-interface {v0}, $LISTENER->onRewardedVideoAdClosed()V
    :done
    return-void
"""

@Suppress("unused")
val rouletteWithoutAdsPatch = bytecodePatch(
    name = "Roulette without ads",
    description = "The daily gem roulette spins straight away instead of playing an IronSource video ad, " +
        "and the IronSource SDK never starts.",
) {
    compatibleWith(*nierCompatibility)

    execute {
        val bridge = mutableClassDefByOrNull(ANDROID_BRIDGE)
            ?: throw PatchException("Not found: $ANDROID_BRIDGE")

        // Two locals (listener, JSON string) on top of the parameters.
        bridge.replaceMethod("showRewardedVideo", emptyList(), extraRegisters = 2, REPORT_AD_WATCHED)
        bridge.replaceMethod("showRewardedVideo", listOf("Ljava/lang/String;"), extraRegisters = 2, REPORT_AD_WATCHED)

        // Never start the SDK, so it doesn't contact IronSource or its ad networks.
        bridge.replaceMethod("init", listOf("Ljava/lang/String;"), extraRegisters = 0, "return-void")
        bridge.replaceMethod(
            "init", listOf("Ljava/lang/String;", "[Ljava/lang/String;"), extraRegisters = 0, "return-void",
        )
    }
}

/**
 * Swaps a method for one with the same signature and [smali] as its body. A new method is needed
 * because the originals don't have enough registers for the new bodies.
 */
private fun MutableClass.replaceMethod(name: String, parameters: List<String>, extraRegisters: Int, smali: String) {
    val signature = "$name(${parameters.joinToString("")})"
    val old = methods.firstOrNull { method ->
        method.name == name && method.parameterTypes.map { it.toString() } == parameters
    } ?: throw PatchException("Not found: $type->$signature")

    // p0 (this) plus one register per parameter; none here are wide (long/double).
    val registers = extraRegisters + 1 + parameters.size
    val new = ImmutableMethod(
        old.definingClass,
        old.name,
        old.parameters,
        old.returnType,
        old.accessFlags,
        old.annotations,
        old.hiddenApiRestrictions,
        MutableMethodImplementation(registers),
    ).toMutable()
    new.addInstructions(0, smali.trimIndent())

    methods.remove(old)
    methods.add(new)
    logger.info("replaced $type->$signature")
}
