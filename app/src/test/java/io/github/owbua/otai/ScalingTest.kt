package io.github.owbua.otai

import io.github.owbua.otai.data.model.Ingredient
import io.github.owbua.otai.domain.amountText
import io.github.owbua.otai.domain.formatMinutes
import io.github.owbua.otai.domain.formatNumber
import io.github.owbua.otai.domain.formatTimer
import org.junit.Assert.assertEquals
import org.junit.Test

class ScalingTest {
    @Test fun `Brueche bei Loeffeln und Stueck`() {
        assertEquals("½", formatNumber(0.5, "Bund"))
        assertEquals("1 ½", formatNumber(1.5, "EL"))
        assertEquals("¼", formatNumber(0.25, "TL"))
        assertEquals("⅔", formatNumber(2.0 / 3, null))
        assertEquals("1", formatNumber(1.0, null))
        assertEquals("2", formatNumber(1.98, null))
        assertEquals("1,4", formatNumber(1.4, null))
    }

    @Test fun `metrische Einheiten runden sinnvoll`() {
        assertEquals("335", formatNumber(500 * 2.0 / 3, "g"))
        assertEquals("1000", formatNumber(1000.0, "g"))
        assertEquals("33", formatNumber(33.3, "ml"))
        assertEquals("2,5", formatNumber(2.5, "kg"))
    }

    @Test fun `Zutat skaliert anzeigen`() {
        assertEquals("½ Bund", Ingredient(amount = 0.5, unit = "Bund", name = "Schnittlauch").amountText())
        assertEquals("1 Bund", Ingredient(amount = 0.5, unit = "Bund", name = "Schnittlauch").amountText(2.0))
        assertEquals("335 g", Ingredient(amount = 500.0, unit = "g", name = "Kartoffeln").amountText(2.0 / 3))
        assertEquals("3", Ingredient(amount = 2.0, name = "Möhren").amountText(1.5))
        assertEquals("n. B.", Ingredient(name = "Salz").amountText(2.0))
    }

    @Test fun `Zeiten formatieren`() {
        assertEquals("35 Min", formatMinutes(35))
        assertEquals("1 Std", formatMinutes(60))
        assertEquals("2 Std 30", formatMinutes(150))
        assertEquals("40:00", formatTimer(2400))
        assertEquals("1:05:09", formatTimer(3909))
    }
}
