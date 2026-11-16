package io.github.owbua.otai.io.web

import io.github.owbua.otai.data.model.Ingredient
import io.github.owbua.otai.data.model.parseAmount

/**
 * Zerlegt Zutatenzeilen von Webseiten („500 g Kartoffel(n)“, „1 TL, gehäuft Zucker“,
 * „n. B. Schmand“, „2 Paprikaschote(n), bunte, ca. 250 g“) in Menge, Einheit, Name und Hinweis.
 */
object IngredientParser {
    private val UNITS = listOf(
        "Päckchen", "Packung", "Pck.", "Pkt.", "Pk.", "Msp.", "Msp", "Prise(n)", "Prisen", "Prise",
        "Bund", "Dose(n)", "Dosen", "Dose", "Zehe(n)", "Zehen", "Zehe", "Scheibe(n)", "Scheiben", "Scheibe",
        "Becher", "Tasse(n)", "Tassen", "Tasse", "Stück", "Stk.", "Glas", "Gläser", "Handvoll", "Würfel",
        "Spritzer", "Schuss", "Zweig(e)", "Zweige", "Zweig", "Blatt", "Blätter", "Stange(n)", "Stangen", "Stange",
        "Kopf", "Knolle(n)", "Knolle", "Beutel", "Flasche(n)", "Flasche", "Liter", "Gramm", "Kilogramm",
        "Milliliter", "Tropfen", "Portion(en)", "Portionen", "Messerspitze", "Esslöffel", "Teelöffel",
        "kg", "mg", "ml", "cl", "dl", "EL", "TL", "El", "Tl", "g", "l", "cm",
    )
    private val UNIT_NORMAL = mapOf(
        "El" to "EL", "Tl" to "TL", "Esslöffel" to "EL", "Teelöffel" to "TL", "Gramm" to "g",
        "Kilogramm" to "kg", "Liter" to "l", "Milliliter" to "ml", "Messerspitze" to "Msp.", "Msp" to "Msp.",
    )
    private const val AMOUNT =
        """(?:\d+(?:[.,]\d+)?\s*[½¼¾⅓⅔]|\d+\s*/\s*\d+|\d+(?:[.,]\d+)?(?:\s*[-–]\s*\d+(?:[.,]\d+)?)?|[½¼¾⅓⅔⅛])"""
    private val LEAD = Regex("""^\s*(?:(ca\.?|etwa|circa)\s+)?($AMOUNT)\s*""", RegexOption.IGNORE_CASE)
    private val NB = Regex("""^\s*(n\.\s?B\.|etwas|nach Belieben|nach Geschmack)\s*,?\s*""", RegexOption.IGNORE_CASE)
    private val MODIFIER = Regex("""^,?\s*(gehäuft|gestrichen|knapp|reichlich|groß|große|klein|kleine|mittelgroß|mittelgroße)\b\s*,?\s*""", RegexOption.IGNORE_CASE)
    private val NOTE_PART = Regex(
        """^(ca\.|etwa|z\.\s?B\.|n\.\s?B\.|nach Belieben|nach Geschmack|aus der Dose|aus dem Glas|tiefgekühlt|optional|zum\s|für\s|evtl\.|eventuell|gehäuft|gestrichen|\d)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): Ingredient {
        var s = raw.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
        val notes = mutableListOf<String>()
        var amount: Double? = null
        var unit: String? = null

        val nb = NB.find(s)
        if (nb != null) {
            notes += "nach Belieben"
            s = s.substring(nb.range.last + 1)
        } else {
            val m = LEAD.find(s)
            if (m != null && m.value.isNotBlank()) {
                val amountText = m.groupValues[2].trim()
                amount = parseAmount(amountText)
                // Bereich „2-3“: gerechnet wird mit dem ersten Wert, der Bereich bleibt als Hinweis sichtbar
                if (Regex("[-–]").containsMatchIn(amountText)) notes += amountText.replace("-", "–").replace(" ", "")
                s = s.substring(m.range.last + 1)
                val u = UNITS.firstOrNull { cand ->
                    s.startsWith(cand) && (s.length == cand.length || !s[cand.length].isLetter())
                }
                if (u != null) {
                    unit = UNIT_NORMAL[u] ?: u
                    s = s.substring(u.length).trimStart()
                }
            }
        }
        // „TL, gehäuft Zucker“
        MODIFIER.find(s)?.let { mod ->
            notes += mod.groupValues[1].lowercase()
            s = s.substring(mod.range.last + 1)
        }
        s = s.trim().trimStart(',').trim()

        // Kommateile: Hinweise (ca. 150 g, aus der Dose …) wandern in die Notiz
        val parts = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val nameParts = mutableListOf<String>()
        parts.forEachIndexed { i, p -> if (i > 0 && NOTE_PART.containsMatchIn(p)) notes += p else nameParts += p }
        val name = nameParts.joinToString(", ").ifEmpty { s }
        return Ingredient(amount = amount, unit = unit, name = name, note = notes.distinct().joinToString(", ").ifEmpty { null })
    }
}
