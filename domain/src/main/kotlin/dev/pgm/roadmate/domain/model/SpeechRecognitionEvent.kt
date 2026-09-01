package dev.pgm.roadmate.domain.model

/**
 * What the speech recognizer reports while listening to one utterance.
 *
 * [Partial] arrives repeatedly as the user speaks so the UI can show the
 * words landing in real time; exactly one [Result] or [Failed] ends the
 * stream.
 */
sealed interface SpeechRecognitionEvent {

    /** Best guess so far — updates live, not final. */
    data class Partial(val text: String) : SpeechRecognitionEvent

    /** The final transcript for this utterance (may be blank if nothing was said). */
    data class Result(val text: String) : SpeechRecognitionEvent

    /** Recognition could not complete; [message] is user-facing Spanish text. */
    data class Failed(val message: String) : SpeechRecognitionEvent
}
