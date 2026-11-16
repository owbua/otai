package io.github.owbua.otai.io.web

import java.net.URLEncoder

/**
 * Chefkoch-Suche ohne offizielle Schnittstelle: Die Suchergebnisseite wird geladen und nach Links auf
 * Rezeptseiten (`/rezepte/<Nummer>/<Titel>.html`) durchsucht. Bewusst nur dieses eine, stabile Merkmal –
 * so übersteht das Auslesen Änderungen am Seitenaufbau.
 */
object ChefkochSearch {
    const val BASE = "https://www.chefkoch.de"

    fun searchUrl(terms: List<String>): String {
        val query = URLEncoder.encode(terms.joinToString(" ") { it.trim() }.lowercase(), "UTF-8")
        return "$BASE/rs/s0/$query/Rezepte.html"
    }

    private val LINK = Regex("""(?:https?://(?:www\.)?chefkoch\.de)?(/rezepte/(\d{5,})/[A-Za-z0-9%._~-]+\.html)""")

    /** Rezept-Links in Trefferreihenfolge, ohne Doppelte (gleiche Rezeptnummer). */
    fun recipeLinks(html: String): List<String> =
        LINK.findAll(html.replace("\\/", "/"))
            .distinctBy { it.groupValues[2] }
            .map { BASE + it.groupValues[1] }
            .toList()
}
