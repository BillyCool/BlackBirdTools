package app.blackbird.patches.server

import app.blackbird.patches.shared.Il2CppPatch
import app.blackbird.patches.shared.MOV_X0_0
import app.blackbird.patches.shared.RET
import app.blackbird.patches.shared.YEAR_9999
import app.blackbird.patches.shared.insn
import app.blackbird.patches.shared.movzWImm16

/** Name of the patch that makes the gRPC channel insecure; left out for https, where the game keeps its own TLS. */
internal const val SSL_BYPASS_PATCH = "ToNativeCredentials"

internal val serverIl2CppPatches = listOf(
    Il2CppPatch(
        SSL_BYPASS_PATCH,
        "SSL bypass — return NULL to force insecure gRPC channel (http only)",
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
