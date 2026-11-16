package app.blackbird.patches.server

import app.blackbird.patches.shared.Il2CppPatch
import app.blackbird.patches.shared.LIBIL2CPP_PATH
import app.blackbird.patches.shared.MOV_W0_1
import app.blackbird.patches.shared.NOP
import app.blackbird.patches.shared.RET
import app.blackbird.patches.shared.insn
import app.blackbird.patches.shared.nierCompatibility
import app.blackbird.patches.shared.patchLibIl2Cpp
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.util.logging.Logger

private val logger = Logger.getLogger("PurchasesPatch")

private val purchasePatches = listOf(
    Il2CppPatch(
        "PurchaseRealProductAsync.MoveNext (inlined IsInitialized check)",
        "Purchases to private server — NOP the cbz that branches to PurchasingUnavailable when Google Play billing is not initialized",
        0x2831CA8, insn(NOP),
    ),
    Il2CppPatch(
        "Purchaser.IsExistProduct",
        "Purchases to private server — always report product as existing (no Google Play store to query)",
        0x282CE78, insn(MOV_W0_1, RET),
    ),
    Il2CppPatch(
        "Purchaser.<BuyProduct>d__24.MoveNext (null _storeController)",
        "Purchases to private server — redirect null _storeController from NRE to cancelled-return path",
        0x2834028, insn(0xB4001675.toInt()),
    ),
    Il2CppPatch(
        "PurchaseRealProductAsync.MoveNext (skip CheckPurchasingAlert)",
        "Fast purchase — skip CheckPurchasingAlert call, awaiter, and CESA dialog",
        0x2831CAC, insn(0x14000019),
    ),
    Il2CppPatch(
        "Initialize.MoveNext (skip _initialized check)",
        "Fast purchase — NOP the cbz so Initialize always proceeds (skip ~8s GP timeout)",
        0x2830834, insn(NOP),
    ),
    Il2CppPatch(
        "DarkPurchase.OnInitializeProcessPurchase",
        "Purchases to private server — ignore the purchases Google Play still holds",
        0x282D018, insn(RET), insn(0xA9BD57F6.toInt(), 0xA9014FF4.toInt(), 0xA9027BFD.toInt(), 0x910083FD.toInt()),
    ),
    Il2CppPatch(
        "Purchaser.OnInitialized (keep _storeController null)",
        "Purchases to private server — keep the Google Play store controller null",
        0x2833A2C, insn(NOP), insn(0xA9020801.toInt()),
    ),
)

@Suppress("unused")
val purchasesPatch = resourcePatch(
    name = "Purchases through private server",
    description = "Routes in-app purchases to the private server instead of Google Play billing, " +
        "which no longer sells anything for this game. Meant to be used with Redirect to private server.",
) {
    category("Private server")
    compatibleWith(*nierCompatibility)

    execute {
        val libFile = get(LIBIL2CPP_PATH)
        if (!libFile.isFile) throw PatchException("Not found: $LIBIL2CPP_PATH (arm64 split missing?)")

        val patched = patchLibIl2Cpp(libFile, purchasePatches, logger)
        logger.info("$patched/${purchasePatches.size} libil2cpp.so purchase methods patched")
    }
}
