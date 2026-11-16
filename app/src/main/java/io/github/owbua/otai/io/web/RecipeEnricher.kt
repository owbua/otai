package io.github.owbua.otai.io.web

import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.model.StepTimer
import io.github.owbua.otai.data.normalized
import java.util.Locale

/**
 * Ergänzt bei Web-Rezepten, was Claude sonst liefert: Timer aus Zeitangaben im Schritttext
 * und die Zuordnung der Zutaten zu den Schritten (für die Chips in der Kochansicht).
 */
object RecipeEnricher {
    private val TIME = Regex(
        """(?:ca\.\s*|etwa\s*|circa\s*)?(\d+(?:[.,]\d+)?)(?:\s*[-–]\s*(\d+(?:[.,]\d+)?))?\s*(Minuten|Minute|Min\.?|min\.?|Stunden|Stunde|Std\.?|h)(?![a-zäöü])""",
        RegexOption.IGNORE_CASE,
    )
    private val VERBS = listOf(
        "köcheln" to "Köcheln", "backen" to "Backen", "braten" to "Braten", "ruhen" to "Ruhen lassen",
        "ziehen" to "Ziehen lassen", "garen" to "Garen", "dünsten" to "Dünsten", "schmoren" to "Schmoren",
        "kochen" to "Kochen", "marinieren" to "Marinieren", "gehen" to "Gehen lassen", "abkühlen" to "Abkühlen",
        "quellen" to "Quellen lassen", "rösten" to "Rösten", "frittieren" to "Frittieren",
    )
    private val STOP = setOf("und", "oder", "mit", "frisch", "frische", "frischer", "gehackt", "gemischtes", "etwas", "für", "die", "der", "das")

    /**
     * @param timers Timer aus Zeitangaben ableiten (Web-Import). Im Editor aus, damit ein bewusst
     *               geleertes Timer-Feld leer bleibt.
     */
    fun enrich(recipe: Recipe, timers: Boolean = true): Recipe {
        val r = recipe.normalized()
        val stems = r.ingredients.associate { ing -> ing.key!! to stemsFor(ing.name) }
        val steps = r.steps.map { step ->
            val lower = step.text.lowercase(Locale.GERMAN)
            val refs = step.ingredients.ifEmpty {
                stems.filter { (_, s) -> s.any { it.containsMatchIn(lower) } }.keys.toList()
            }
            step.copy(ingredients = refs, timer = step.timer ?: if (timers) timerFor(step.text) else null)
        }
        return r.copy(steps = steps)
    }

    fun timerFor(text: String): StepTimer? {
        // Bei mehreren Angaben die längste nehmen (typisch: „… 3 Min. anbraten, dann 40 Min. köcheln“)
        val best = TIME.findAll(text).mapNotNull { m ->
            val v = (m.groupValues[2].ifEmpty { m.groupValues[1] }).replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val unit = m.groupValues[3].lowercase(Locale.GERMAN)
            val seconds = if (unit.startsWith("st") || unit == "h") v * 3600 else v * 60
            seconds.toInt() to m.range.first
        }.maxByOrNull { it.first } ?: return null
        if (best.first < 60 || best.first > 24 * 3600) return null
        val lower = text.lowercase(Locale.GERMAN)
        // Verb in der Nähe der Zeitangabe bevorzugen
        val label = VERBS
            .flatMap { (verb, label) -> Regex(Regex.escape(verb)).findAll(lower).map { kotlin.math.abs(it.range.first - best.second) to label }.toList() }
            .minByOrNull { it.first }?.second ?: "Timer"
        return StepTimer(best.first, label)
    }

    /** Suchmuster je Zutat: lange Wörter als Wortstamm („kartoffe“ trifft „Kartoffeln“), kurze („Öl“, „Ei“) nur als ganzes Wort. */
    private fun stemsFor(name: String): List<Regex> {
        val base = name.substringBefore(',').replace(Regex("\\(.*?\\)"), " ").lowercase(Locale.GERMAN)
        return base.split(Regex("[^a-zäöüß]+"))
            .filter { it.length >= 2 && it !in STOP }
            .map { w ->
                when {
                    w.length > 6 -> Regex(Regex.escape(w.dropLast(1)))
                    w.length >= 4 -> Regex(Regex.escape(w))
                    else -> Regex("(?<![a-zäöüß])" + Regex.escape(w) + "(?:e|er|s)?(?![a-zäöüß])")
                }
            }
    }
}
