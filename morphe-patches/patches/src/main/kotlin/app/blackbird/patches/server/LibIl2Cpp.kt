package app.blackbird.patches.server

import java.io.File
import java.io.RandomAccessFile
import java.util.logging.Logger

/** [expected], when set, must match the original bytes at [rva] or the patch is skipped. */
internal class Il2CppPatch(
    val name: String,
    val desc: String,
    val rva: Long,
    val bytes: ByteArray,
    val expected: ByteArray? = null,
)

internal fun insn(vararg words: Int) = ByteArray(words.size * 4).also { out ->
    words.forEachIndexed { i, w -> for (b in 0..3) out[i * 4 + b] = (w ushr (8 * b)).toByte() }
}

private const val MOV_X0_0 = 0xD2800000.toInt()   // mov x0, #0
private const val MOV_W0_1 = 0x52800020           // mov w0, #1
private const val NOP = 0xD503201F.toInt()        // nop
internal const val RET = 0xD65F03C0.toInt()       // ret
private const val YEAR_9999 = 0x5284E1E1          // movz w1, #0x270f (year 9999 literal)

/** Encode ARM64 MOVZ W<rd>, #imm for a 16-bit immediate. */
private fun movzWImm16(rd: Int, imm: Int): Int {
    require(rd in 0..30 && imm in 0..0xFFFF)
    return 0x52800000 or (imm shl 5) or rd
}

internal val baseIl2CppPatches = listOf(
    Il2CppPatch(
        "ToNativeCredentials",
        "SSL bypass — return NULL to force insecure gRPC channel",
        0x35C8670, insn(MOV_X0_0, RET),
    ),
    Il2CppPatch(
        "OctoManager.Internal.GetListAes",
        "Octo list: force plain list (return false = no AES); server serves raw list.bin",
        0x4C27038, insn(MOV_X0_0, RET),
    ),
    Il2CppPatch(
        "TitleScreen.ForceYear9999",
        "Replace EoS year 2024 with 9999",
        0x2F11E7C, insn(YEAR_9999),
    ),
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
)

internal fun portPatches(grpcPort: Int) = listOf(
    Il2CppPatch(
        "NetworkConfig.get_ServerPort",
        "gRPC port override — MOVZ W0, #$grpcPort (return configured port)",
        0x361D548, insn(movzWImm16(0, grpcPort), RET),
    ),
    Il2CppPatch(
        "InitializeApiClient.OnStateBegin (port override)",
        "conductor port override — MOVZ W2, #$grpcPort",
        0x2DEAF68, insn(movzWImm16(2, grpcPort)),
    ),
    Il2CppPatch(
        "CalculatorNetworking.InitializeApiClient (port override)",
        "catch-all port override — MOVZ W20, #$grpcPort",
        0x2E1B278, insn(movzWImm16(20, grpcPort)),
    ),
)

/** Writes each patch at file offset == RVA, as patcher.ipynb does. Returns the number applied. */
internal fun patchLibIl2Cpp(file: File, patches: List<Il2CppPatch>, logger: Logger): Int {
    var patched = 0
    RandomAccessFile(file, "rw").use { handle ->
        val fileSize = handle.length()
        for (patch in patches) {
            if (patch.rva + patch.bytes.size > fileSize) {
                logger.warning("SKIP ${patch.name}: RVA 0x%X beyond file size".format(patch.rva))
                continue
            }

            val expected = patch.expected
            if (expected != null) {
                val actual = ByteArray(expected.size)
                handle.seek(patch.rva)
                handle.readFully(actual)
                if (!actual.contentEquals(expected)) {
                    logger.warning(
                        "SKIP ${patch.name}: unexpected bytes at 0x%X (${actual.hex()}), wrong game version?"
                            .format(patch.rva),
                    )
                    continue
                }
            }

            val original = ByteArray(patch.bytes.size)
            handle.seek(patch.rva)
            handle.readFully(original)
            handle.seek(patch.rva)
            handle.write(patch.bytes)

            patched++
            logger.info("${patch.name} @ 0x%X: ${original.hex()} -> ${patch.bytes.hex()}".format(patch.rva))
        }
    }
    return patched
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
