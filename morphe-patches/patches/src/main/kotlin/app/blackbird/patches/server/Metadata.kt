package app.blackbird.patches.server

import app.morphe.patcher.patch.PatchException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.logging.Logger

private const val METADATA_MAGIC = 0xFAB11BAF.toInt()
private const val HDR_STRING_LITERAL_OFF = 8
private const val HDR_STRING_LITERAL_DATA_OFF = 16
private const val GAME_NAME = "nierreincarnation"

internal data class ServerConfig(
    val protocol: String,
    val host: String,
    val httpPort: Int?,
    val grpcPort: Int,
) {
    val webBaseUrl: String
        get() = "$protocol://$host" + (httpPort?.let { ":$it" } ?: "")
}

internal fun buildReplacements(cfg: ServerConfig): List<Pair<String, String>> {
    val game = GAME_NAME
    val replacements = listOf(
        "api.app.$game.com" to cfg.host,
        "https://web.app.$game.com/assets/release/{0}/database.bin" to
            "${cfg.webBaseUrl}/assets/release/{0}/database.bin",
        "https://web.app.$game.com" to cfg.webBaseUrl,
        "https://resources-api.app.$game.com/" to "${cfg.webBaseUrl}/",
    )

    for ((old, new) in replacements) {
        if (new.toByteArray().size > old.toByteArray().size) {
            throw PatchException(
                "Replacement too long: '$old' -> '$new'. Use a shorter host name or omit the port.",
            )
        }
    }
    return replacements
}

/** Rewrites IL2CPP string literals in place. Returns the number of strings patched. */
internal fun patchMetadataStrings(file: File, replacements: List<Pair<String, String>>, logger: Logger): Int {
    val data = file.readBytes()
    val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    val magic = buf.getInt(0)
    if (magic != METADATA_MAGIC) throw PatchException("Bad metadata magic: 0x%08X".format(magic))
    logger.info("metadata v${buf.getInt(4)}, ${data.size} bytes")

    val slOff = buf.getInt(HDR_STRING_LITERAL_OFF)
    val slSize = buf.getInt(HDR_STRING_LITERAL_OFF + 4)
    val sldOff = buf.getInt(HDR_STRING_LITERAL_DATA_OFF)
    val sldSize = buf.getInt(HDR_STRING_LITERAL_DATA_OFF + 4)
    val entryCount = slSize / 8

    var patched = 0
    for ((oldStr, newStr) in replacements) {
        val oldBytes = oldStr.toByteArray()
        val newBytes = newStr.toByteArray()

        val blobPos = indexOf(data, oldBytes, sldOff, sldOff + sldSize)
        if (blobPos < 0) {
            logger.warning("NOT FOUND in blob: '$oldStr'")
            continue
        }

        val dataIndex = blobPos - sldOff
        val entry = (0 until entryCount).firstOrNull { i ->
            val entryOffset = slOff + i * 8
            buf.getInt(entryOffset + 4) == dataIndex && buf.getInt(entryOffset) == oldBytes.size
        }
        if (entry == null) {
            logger.warning("No table entry found for '$oldStr' (dataIndex=0x%X)".format(dataIndex))
            continue
        }

        buf.putInt(slOff + entry * 8, newBytes.size)
        newBytes.copyInto(data, blobPos)
        data.fill(0, blobPos + newBytes.size, blobPos + oldBytes.size)
        logger.info("PATCHED: '$oldStr' -> '$newStr'")
        patched++
    }

    file.writeBytes(data)
    return patched
}

private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int, to: Int): Int {
    val last = minOf(to, haystack.size) - needle.size
    outer@ for (i in from..last) {
        for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}
