package dev.pgm.roadmate.domain.model

/**
 * A sustained cabin-silence occurrence detected by DetectSilenceUseCase.
 */
data class SilenceEvent(
    val timestamp: Long,
    val duration: Long,
    val action: SilenceAction
)

enum class SilenceAction {
    ALERT,
}
