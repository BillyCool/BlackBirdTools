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

// The Square Enix Bridge pages (account linking and transfer), opened in the in-game webview with a token from
// GetBackupToken. The server's transfer site takes their place.
private const val BRIDGE_REGISTER_URL =
    "https://psg.sqex-bridge.jp/ntv/{gameId}/reg/top?type={deviceType}&token={bridgeBackupToken}"
private const val BRIDGE_TRANSFER_URL =
    "https://psg.sqex-bridge.jp/ntv/{gameId}/update/top?type={deviceType}&token={bridgeBackupToken}"

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
        BRIDGE_REGISTER_URL to "${cfg.webBaseUrl}/bridge/reg?token={bridgeBackupToken}",
        BRIDGE_TRANSFER_URL to "${cfg.webBaseUrl}/bridge/update?token={bridgeBackupToken}",
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

private const val TICKS_PER_MILLISECOND = 10_000L
private const val UNIX_EPOCH_TICKS = 621_355_968_000_000_000L

/** Sets boot.config's buildDate (.NET ticks) to now, so Unity extracts the APK's IL2CPP data again on the next start. */
internal fun stampBuildDate(file: File, logger: Logger) {
    if (!file.isFile) throw PatchException("Not found: ${file.path}")
    val ticks = System.currentTimeMillis() * TICKS_PER_MILLISECOND + UNIX_EPOCH_TICKS
    val lines = file.readLines()
    if (lines.none { it.startsWith("buildDate=") }) throw PatchException("boot.config has no buildDate line")
    file.writeText(lines.joinToString("\n", postfix = "\n") { if (it.startsWith("buildDate=")) "buildDate=$ticks" else it })
    logger.info("boot.config buildDate set to $ticks")
}
