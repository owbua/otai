package io.github.owbua.otai

import io.github.owbua.otai.domain.applyFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    @Test fun `geaenderte Mengen werden auf Basisportionen umgerechnet und notiert`() {
        val r = TestData.bauerntopf // 3 Portionen
        // für 6 Portionen gekocht: 4 Möhren laut Rezept → tatsächlich 6 genommen, Kartoffeln unverändert (1000 g)
        val out = r.applyFeedback(
            cookedServings = 6,
            cookedAmounts = mapOf("moehren" to 6.0, "kartoffeln" to 1000.0),
            text = "Mehr Möhren war super",
            rating = 4,
            today = "2026-11-14",
        )
        assertEquals(3.0, out.ingredients.first { it.key == "moehren" }.amount!!, 1e-9)
        assertEquals(500.0, out.ingredients.first { it.key == "kartoffeln" }.amount!!, 1e-9)
        val note = out.notes.single()
        assertEquals("2026-11-14", note.date)
        assertEquals("Mehr Möhren war super", note.text)
        assertEquals(listOf("Möhre(n): 4 → 6 (für 6 Portionen)"), note.changes)
        assertEquals(4, out.rating)
        assertEquals(1, out.cookedCount)
        assertEquals("2026-11-14", out.lastCooked)
    }

    @Test fun `Menge auf n B setzen und zurueck`() {
        val r = TestData.hackPilz
        val out = r.applyFeedback(2, mapOf("erbsen" to null, "salz-pfeffer" to 1.0), "", null, "2026-11-14")
        assertNull(out.ingredients.first { it.key == "erbsen" }.amount)
        assertEquals(1.0, out.ingredients.first { it.key == "salz-pfeffer" }.amount!!, 0.0)
        assertEquals(listOf("Salz und Pfeffer: n. B. → 1", "Erbsen, TK: 100 g → n. B."), out.notes.single().changes)
    }

    @Test fun `ohne Angaben keine Notiz aber gezaehlt`() {
        val out = TestData.hackPilz.applyFeedback(2, emptyMap(), "  ", null, "2026-11-14")
        assertTrue(out.notes.isEmpty())
        assertEquals(1, out.cookedCount)
    }
}
