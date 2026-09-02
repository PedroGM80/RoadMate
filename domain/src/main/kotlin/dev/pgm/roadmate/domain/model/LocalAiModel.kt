package dev.pgm.roadmate.domain.model

/**
 * A downloadable on-device model for the MediaPipe local-AI backend.
 *
 * Only models that download with **no account and no licence click-through**
 * belong here — that is the whole point of RoadMate's local AI. [sizeBytes]
 * is the exact size when known (lets the download be verified to the byte),
 * or 0 to fall back to a "looks plausible" check.
 */
data class LocalAiModel(
    val id: String,
    val name: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    /** One short line for the picker: what the trade-off is. */
    val note: String,
    val recommended: Boolean = false,
) {
    /** e.g. "0,5 GB" — for the picker, computed so it can't drift from [sizeBytes]. */
    val approxSize: String
        get() = if (sizeBytes <= 0L) "" else "%,.1f GB".format(sizeBytes / 1_000_000_000.0)
}

/**
 * The fixed set of models the driver can choose between. Adding one is a
 * single entry here — the manager, the repository and the settings screen
 * all read from this list.
 */
object LocalAiCatalog {

    private const val HF = "https://huggingface.co/litert-community"

    val models: List<LocalAiModel> = listOf(
        LocalAiModel(
            id = "qwen2.5-0.5b",
            name = "Qwen2.5 0.5B",
            url = "$HF/Qwen2.5-0.5B-Instruct/resolve/main/" +
                "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "qwen2.5-0.5b-instruct-q8.task",
            sizeBytes = 546_660_344L,
            note = "Más rápido y ligero. Suficiente para el día a día.",
            recommended = true,
        ),
        LocalAiModel(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B",
            url = "$HF/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
            fileName = "qwen3-0.6b-int4.litertlm",
            sizeBytes = 497_664_000L,
            note = "El más nuevo y el que menos ocupa. Buen equilibrio.",
        ),
        LocalAiModel(
            id = "olmo2-1b",
            name = "OLMo-2 1B",
            url = "$HF/OLMo-2-1B-Instruct/resolve/main/OLMo-2-1B-Instruct_q4_block32_ekv4096.litertlm",
            fileName = "olmo2-1b-instruct-q4.litertlm",
            sizeBytes = 931_241_056L,
            note = "Modelo 100% abierto (AllenAI). Otra familia, otro estilo.",
        ),
        LocalAiModel(
            id = "qwen2.5-1.5b",
            name = "Qwen2.5 1.5B",
            url = "$HF/Qwen2.5-1.5B-Instruct/resolve/main/" +
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "qwen2.5-1.5b-instruct-q8.task",
            sizeBytes = 1_597_913_616L,
            note = "Responde mejor, pero es más lento y ocupa más.",
        ),
    )

    val recommended: LocalAiModel = models.first { it.recommended }

    fun byId(id: String?): LocalAiModel? = models.firstOrNull { it.id == id }
}
