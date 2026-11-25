package app.blackbird.patches.improvements

import app.blackbird.patches.shared.Il2CppPatch
import app.blackbird.patches.shared.LIBIL2CPP_PATH
import app.blackbird.patches.shared.MOV_X0_0
import app.blackbird.patches.shared.insn
import app.blackbird.patches.shared.nierCompatibility
import app.blackbird.patches.shared.patchLibIl2Cpp
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.util.logging.Logger

private val logger = Logger.getLogger("NewsOnceOnlyPatch")

// The news pop-ups on the home screen are "dokans". CalculatorMomPromotion.CreateMomPromotionData keeps only the ones
// IsAlreadyView(dokanId, comebackDatetime) reports as unseen. IsAlreadyView counts a dokan as seen when the account
// has an IUserDokan row for it and either the account has no comeback date or the row's display time is later than
// that date. A player back after a long break gets a comeback date (the private server sets it at that login), so
// every news item seen before it shows again.
//
// CreateMomPromotionData (0x2AA5A70) fetches that date with CalculatorComeback.GetComeBackDateTime and stores it in the
// closure's comebackDatetime field (str x0, [x19, #0x20]). Replacing the call with "mov x0, #0" stores null, so
// IsAlreadyView takes its "no comeback date" branch: a news item with a row counts as seen, whatever the break.
// GetComeBackDateTime itself is untouched, because the comeback campaigns (stamina, drops, enhance) read it too.
private val newsOncePatches = listOf(
    Il2CppPatch(
        "CalculatorMomPromotion.CreateMomPromotionData (comeback date)",
        "News pop-ups: seen news never shows again after a long break (comeback date read as none)",
        0x2AA5B64,
        insn(MOV_X0_0),
        insn(0x97F73D09.toInt()),   // bl CalculatorComeback.GetComeBackDateTime
    ),
)

@Suppress("unused")
val newsOnceOnlyPatch = resourcePatch(
    name = "News shown once",
    description = "News pop-ups an account has already seen are never shown again, however long the account was away.",
) {
    category("Feature improvements")
    compatibleWith(*nierCompatibility)

    execute {
        val libFile = get(LIBIL2CPP_PATH)
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        val patched = patchLibIl2Cpp(libFile, newsOncePatches, logger)
        logger.info("$patched/${newsOncePatches.size} libil2cpp.so news methods patched")
    }
}
