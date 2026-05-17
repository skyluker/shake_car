package com.shakecar.domain

/**
 * Klasyfikacja kondycji pojazdu na bazie sygnatury cz\u0119stotliwo\u015bciowej.
 * Reguly dziedzinowe - pierwsza wersja, do kalibracji.
 */
data class Finding(
    val component: String,
    val severity: Severity,
    val explanation: String,
)

enum class Severity { OK, WARNING, ALERT }

object Diagnosis {

    fun evaluate(result: AnalysisResult): List<Finding> {
        val out = mutableListOf<Finding>()
        val bands = result.bandEnergy

        // 1. Amortyzatory - na bazie damping ratio i piku body bounce
        val zeta = result.dampingRatioBody
        val zetaFinding = when {
            zeta in 0.20f..0.40f -> Finding(
                "Amortyzatory",
                Severity.OK,
                "Wsp\u00f3\u0142czynnik t\u0142umienia \u03b6 = %.2f w normie (0.20-0.40).".format(zeta)
            )
            zeta in 0.10f..0.20f -> Finding(
                "Amortyzatory",
                Severity.WARNING,
                "\u03b6 = %.2f obni\u017cone - mo\u017ce wskazywa\u0107 na zu\u017cycie. Rekomendowana kontrola.".format(zeta)
            )
            zeta < 0.10f && result.bodyBounceFreqHz > 0.5f -> Finding(
                "Amortyzatory",
                Severity.ALERT,
                "\u03b6 = %.2f - bardzo niski. Auto buja si\u0119 zbyt d\u0142ugo. Wymie\u0144 amortyzatory.".format(zeta)
            )
            else -> Finding(
                "Amortyzatory",
                Severity.OK,
                "Brak wyra\u017anego rezonansu nadwozia - pomiar mo\u017ce by\u0107 zbyt kr\u00f3tki lub droga zbyt g\u0142adka."
            )
        }
        out += zetaFinding

        // 2. Wheel hop / opony / tuleje
        val hop = bands[FrequencyBand.WHEEL_HOP] ?: 0f
        val body = bands[FrequencyBand.BODY_BOUNCE] ?: 0f
        if (hop > 1.5f * body && hop > 0.05f) {
            out += Finding(
                "Opony / tuleje wahaczy",
                Severity.WARNING,
                "Silne drgania w pa\u015bmie 10-15 Hz. Mo\u017cliwe zu\u017cycie tulei wahaczy lub nier\u00f3wne opony."
            )
        } else {
            out += Finding(
                "Opony / tuleje wahaczy",
                Severity.OK,
                "Pasmo 10-15 Hz w normie."
            )
        }

        // 3. Niewyważenie kół - pasmo 15-25 Hz przy wyższych prędkościach
        val imb = bands[FrequencyBand.WHEEL_IMBALANCE] ?: 0f
        if (imb > 0.05f && imb > 0.8f * body) {
            out += Finding(
                "Wywa\u017cenie k\u00f3\u0142",
                Severity.WARNING,
                "Energia w pa\u015bmie 15-25 Hz sugeruje niewywa\u017cone ko\u0142a lub uszkodzon\u0105 felg\u0119."
            )
        }

        // 4. Stuki / luzy
        if (result.crestFactor > 6f) {
            out += Finding(
                "Luzy zawieszenia",
                Severity.WARNING,
                "Wysoki crest factor (%.1f) - sygnatury impulsowe, mo\u017cliwe luzy w \u0142\u0105cznikach lub ko\u0144c\u00f3wkach.".format(result.crestFactor)
            )
        }

        // 5. Komfort wg ISO 2631
        out += Finding(
            "Komfort jazdy (ISO 2631)",
            when {
                result.rmsWeightedIso2631 < 0.315f -> Severity.OK
                result.rmsWeightedIso2631 < 0.8f -> Severity.WARNING
                else -> Severity.ALERT
            },
            "Wa\u017cone RMS = %.2f m/s\u00b2 (\u017ar\u00f3d\u0142o md\u0142o\u015bci, m\u0119cz\u0105ce drgania).".format(result.rmsWeightedIso2631)
        )

        return out
    }
}
