package app.blackbird.patches.shared

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

internal const val MOV_X0_0 = 0xD2800000.toInt()   // mov x0, #0
internal const val MOV_W0_1 = 0x52800020           // mov w0, #1
internal const val NOP = 0xD503201F.toInt()        // nop
internal const val RET = 0xD65F03C0.toInt()       // ret
internal const val YEAR_9999 = 0x5284E1E1          // movz w1, #0x270f (year 9999 literal)

/** Encode ARM64 MOVZ W<rd>, #imm for a 16-bit immediate. */
internal fun movzWImm16(rd: Int, imm: Int): Int {
    require(rd in 0..30 && imm in 0..0xFFFF)
    return 0x52800000 or (imm shl 5) or rd
}

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
