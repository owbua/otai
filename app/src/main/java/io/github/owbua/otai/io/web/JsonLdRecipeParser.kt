package io.github.owbua.otai.io.web

import io.github.owbua.otai.data.model.Nutrition
import io.github.owbua.otai.data.model.Recipe
import io.github.owbua.otai.data.model.Source
import io.github.owbua.otai.data.model.Step
import io.github.owbua.otai.data.model.Times
import io.github.owbua.otai.data.slugify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI

class WebRecipe(val recipe: Recipe, val imageUrl: String?)

class NoRecipeFoundException : Exception("Auf der Seite wurden keine Rezeptdaten gefunden")

/**
 * Liest schema.org/Recipe-Daten (JSON-LD), wie sie Chefkoch und die meisten Rezeptseiten einbetten,
 * und wandelt sie ins otai-Format. Rein JVM, damit es ohne Gerät testbar ist.
 */
object JsonLdRecipeParser {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    private val SCRIPT = Regex(
        """<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun parse(html: String, pageUrl: String): WebRecipe {
        val node = SCRIPT.findAll(html)
            .mapNotNull { m -> runCatching { json.parseToJsonElement(sanitize(m.groupValues[1])) }.getOrNull() }
            .mapNotNull { findRecipe(it) }
            .firstOrNull() ?: throw NoRecipeFoundException()
        return map(node, pageUrl)
    }

    private fun sanitize(s: String) = s.trim()
        .removePrefix("<!--").removeSuffix("-->")
        .removePrefix("/*<![CDATA[*/").removeSuffix("/*]]>*/")
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
        .replace("\r", " ").replace("\n", " ").replace("\t", " ")

    private fun findRecipe(e: JsonElement): JsonObject? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull { findRecipe(it) }
        is JsonObject -> when {
            isType(e["@type"], "Recipe") -> e
            else -> (e["@graph"]?.let(::findRecipe)) ?: (e["mainEntity"]?.let(::findRecipe))
                ?: (e["mainEntityOfPage"]?.takeIf { it is JsonObject }?.let(::findRecipe))
        }
        else -> null
    }

    private fun isType(t: JsonElement?, name: String): Boolean = when (t) {
        is JsonPrimitive -> t.contentOrNull?.substringAfterLast('/')?.equals(name, true) == true
        is JsonArray -> t.any { isType(it, name) }
        else -> false
    }

    private fun map(o: JsonObject, pageUrl: String): WebRecipe {
        val title = text(o["name"]).orEmpty()
        val ingredients = strings(o["recipeIngredient"] ?: o["ingredients"]).map { IngredientParser.parse(it) }
        val steps = instructions(o["recipeInstructions"]).map { Step(text = it) }
        val host = runCatching { URI(pageUrl).host }.getOrNull()?.removePrefix("www.")
        val siteName = text((o["publisher"] as? JsonObject)?.get("name")) ?: siteNameFor(host)
        val keywords = strings(o["keywords"]).flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
        val cuisine = strings(o["recipeCuisine"])
        val category = strings(o["recipeCategory"]).firstOrNull()
        val tags = (cuisine + keywords)
            .filterNot { it.equals(category, true) || it.equals(title, true) || it.length > 24 }
            .distinctBy { it.lowercase() }
            .take(5)
        val recipe = Recipe(
            id = slugify(title),
            title = title,
            description = text(o["description"]).orEmpty(),
            servings = servings(o["recipeYield"]) ?: 2,
            category = category,
            tags = tags,
            times = Times(
                totalMin = duration(o["totalTime"]),
                activeMin = duration(o["prepTime"]),
                cookMin = duration(o["cookTime"]),
            ).let { t -> if (t.totalMin == null && t.activeMin != null && t.cookMin != null) t.copy(totalMin = t.activeMin + t.cookMin) else t },
            ingredients = ingredients,
            steps = steps,
            nutrition = nutrition(o["nutrition"] as? JsonObject),
            source = Source(name = siteName, author = author(o["author"]), url = text(o["url"])?.takeIf { it.startsWith("http") } ?: pageUrl),
        )
        return WebRecipe(RecipeEnricher.enrich(recipe), image(o["image"])?.let { resolve(pageUrl, it) })
    }

    private fun siteNameFor(host: String?): String? = when {
        host == null -> null
        host.endsWith("chefkoch.de") -> "Chefkoch"
        else -> host
    }

    // --- Hilfsfunktionen ---------------------------------------------------------------------

    private fun text(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull?.let(::cleanText)?.ifEmpty { null }
        is JsonArray -> e.firstNotNullOfOrNull { text(it) }
        is JsonObject -> text(e["text"] ?: e["name"] ?: e["@value"])
        else -> null
    }

    private fun strings(e: JsonElement?): List<String> = when (e) {
        null, JsonNull -> emptyList()
        is JsonArray -> e.flatMap { strings(it) }
        else -> listOfNotNull(text(e))
    }

    private fun instructions(e: JsonElement?): List<String> = when (e) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> splitInstructionText(e.contentOrNull.orEmpty())
        is JsonArray -> e.flatMap { instructions(it) }
        is JsonObject -> when {
            isType(e["@type"], "HowToSection") -> instructions(e["itemListElement"])
            e["itemListElement"] != null -> instructions(e["itemListElement"])
            else -> listOfNotNull(text(e["text"] ?: e["name"]))
        }
    }

    /** Ein einziger Textblock (älteres Chefkoch-Layout): an Leerzeilen bzw. Nummerierung teilen. */
    private fun splitInstructionText(raw: String): List<String> {
        val decoded = decodeEntities(raw).replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        val blocks = decoded.split(Regex("\\n\\s*\\n|\\r\\n\\s*\\r\\n|(?m)^\\s*\\d+[.)]\\s+")).map(::cleanText).filter { it.isNotEmpty() }
        if (blocks.size > 1) return blocks
        return decoded.split('\n').map(::cleanText).filter { it.isNotEmpty() }
    }

    private fun servings(e: JsonElement?): Int? =
        strings(e).firstNotNullOfOrNull { Regex("""\d+""").find(it)?.value?.toIntOrNull() }?.takeIf { it in 1..99 }
            ?: (e as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

    private val ISO_DURATION = Regex("""P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""", RegexOption.IGNORE_CASE)

    fun duration(e: JsonElement?): Int? {
        val s = text(e) ?: return null
        val m = ISO_DURATION.matchEntire(s.trim()) ?: return null
        val (d, h, min, sec) = m.destructured
        val total = (d.toIntOrNull() ?: 0) * 1440 + (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0) + ((sec.toIntOrNull() ?: 0) + 30) / 60
        return total.takeIf { it > 0 }
    }

    private fun number(e: JsonElement?): Double? =
        text(e)?.let { Regex("""\d+(?:[.,]\d+)?""").find(it)?.value?.replace(',', '.')?.toDoubleOrNull() }

    private fun nutrition(o: JsonObject?): Nutrition? {
        o ?: return null
        val n = Nutrition(
            kcal = number(o["calories"]),
            proteinG = number(o["proteinContent"]),
            fatG = number(o["fatContent"]),
            carbsG = number(o["carbohydrateContent"]),
        )
        return n.takeIf { listOf(it.kcal, it.proteinG, it.fatG, it.carbsG).any { v -> v != null } }
    }

    private fun author(e: JsonElement?): String? = when (e) {
        is JsonArray -> e.mapNotNull { author(it) }.joinToString(", ").ifEmpty { null }
        is JsonObject -> text(e["name"])
        else -> text(e)
    }

    private fun image(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.firstNotNullOfOrNull { image(it) }
        is JsonObject -> image(e["url"] ?: e["contentUrl"] ?: e["@id"])
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun resolve(base: String, url: String): String =
        runCatching { URI(base).resolve(url.trim()).toString() }.getOrDefault(url)

    private fun cleanText(s: String) = decodeEntities(s)
        .replace(Regex("<[^>]+>"), " ")
        .replace(' ', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun decodeEntities(s: String): String = s
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.let { c -> String(Character.toChars(c)) } ?: it.value }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toIntOrNull(16)?.let { c -> String(Character.toChars(c)) } ?: it.value }
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&auml;", "ä").replace("&ouml;", "ö").replace("&uuml;", "ü")
        .replace("&Auml;", "Ä").replace("&Ouml;", "Ö").replace("&Uuml;", "Ü").replace("&szlig;", "ß").replace("&amp;", "&")
}
