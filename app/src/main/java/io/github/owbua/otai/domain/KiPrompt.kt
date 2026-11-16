package io.github.owbua.otai.domain

/**
 * Prompt für Claude aus `claude-prompt.md`: [main] ist der Text zwischen der ersten und zweiten
 * Linie „---“, [packageAddon] der Abschnitt „### Zusatz: …“ am Ende (Rezept-Paket mit Fotos).
 */
data class KiPrompt(val main: String, val packageAddon: String) {
    /** Prompt für ein Rezept-Paket (ZIP mit Fotos): Hauptprompt + Zusatz. */
    val withPackage: String get() = if (packageAddon.isBlank()) main else "$main\n\nVariante: Rezept-Paket\n\n$packageAddon"

    companion object {
        fun parse(markdown: String): KiPrompt {
            val lines = markdown.replace("\r\n", "\n").lines()
            val rules = lines.indices.filter { lines[it].trim() == "---" }
            val main = if (rules.size >= 2) lines.subList(rules[0] + 1, rules[1]).joinToString("\n").trim()
            else if (rules.size == 1) lines.drop(rules[0] + 1).joinToString("\n").trim()
            else markdown.trim()
            val addonStart = lines.indexOfFirst { it.trimStart().startsWith("### Zusatz") }
            val addon = if (addonStart >= 0) lines.drop(addonStart + 1).joinToString("\n").trim() else ""
            return KiPrompt(main, addon)
        }
    }
}
