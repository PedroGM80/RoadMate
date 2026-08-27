package dev.pgm.roadmate.utils

/**
 * Original, road-trip-themed jokes (written for RoadMate, not sourced from
 * any third-party dataset — avoids the licensing question entirely for an
 * app meant to be published). Answered directly by GenerateResponseUseCase
 * when it detects joke-intent in the user's question, bypassing Gemini Nano
 * entirely — so "cuéntame un chiste" works identically whether or not this
 * device has on-device AI ("modo básico" included), and answers instantly.
 */
object JokeProvider {

    /** Words that, if present in the recognized speech, mean "tell me a joke". */
    val TRIGGER_WORDS = listOf("chiste", "broma")

    fun matchesJokeIntent(userInput: String): Boolean {
        val normalized = userInput.lowercase()
        return TRIGGER_WORDS.any { normalized.contains(it) }
    }

    fun randomJoke(): String = JOKES.random()

    private val JOKES = listOf(
        "¿Por qué el coche no podía dejar de reír? Porque el semáforo le hacía cosquillas en verde.",
        "¿Sabes por qué el GPS nunca se pierde? Porque siempre tiene un plan B, un plan C y un plan D.",
        "¿Qué le dijo un neumático a otro? Nada, se quedaron sin aire para hablar.",
        "¿Por qué el coche eléctrico fue al psicólogo? Porque tenía problemas de carga.",
        "¿Cuál es el colmo de un conductor? Que el GPS le diga \"has llegado\" y siga en el aparcamiento de casa.",
        "¿Por qué el mapa se puso rojo? Porque vio todas sus curvas.",
        "¿Qué hace una señal de stop en una fiesta? Se queda parada toda la noche.",
        "Un coche le pregunta a otro: ¿vienes mucho por esta carretera? Y el otro responde: solo cuando me da la gasolina.",
        "¿Por qué el coche de rally nunca cuenta chistes largos? Porque no aguanta las rectas.",
        "Mi navegador y yo tenemos una relación muy honesta: siempre me dice cuando me he equivocado, en voz alta y varias veces.",
        "¿Cómo se despide un peaje? \"Aquí te quedas tú y aquí me quedo yo, con tu dinero.\"",
        "¿Qué le dice un guardarraíl a un coche despistado? \"Para el carro.\"",
        "¿Por qué los coches antiguos no van al gimnasio? Porque ya tienen bastante con el motor de arranque.",
        "El área de servicio y yo nos entendemos perfectamente: yo pago, y ella se queda con mi dinero y mi dignidad del café.",
        "¿Sabes qué le dijo la rotonda al coche indeciso? \"Da igual la salida, total, vas a volver a pasar por aquí.\"",
        "Mi coche tiene el mejor sentido del humor: cada vez que algo va mal, se ríe con una lucecita naranja en el salpicadero."
    )
}
