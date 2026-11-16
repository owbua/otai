package io.github.owbua.otai.data

import io.github.owbua.otai.data.model.RECIPE_FORMAT
import io.github.owbua.otai.data.model.Recipe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.text.Normalizer
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
object RecipeJson {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
        allowTrailingComma = true
        allowComments = true
    }

    fun encode(recipe: Recipe): String = json.encodeToString(Recipe.serializer(), recipe)

    fun encodeList(recipes: List<Recipe>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Recipe.serializer()), recipes)

    fun decode(text: String): Recipe = json.decodeFromString(Recipe.serializer(), text)

    /**
     * Liest ein Dokument, das ein einzelnes Rezept, eine Liste von Rezepten oder
     * ein Objekt `{ "recipes": [...] }` enthält. Markdown-Codezäune (```json) werden toleriert,
     * falls jemand die Claude-Antwort komplett kopiert.
     */
    fun decodeDocument(text: String): List<Recipe> {
        val cleaned = stripCodeFence(text)
        val root: JsonElement = json.parseToJsonElement(cleaned)
        val elements: List<JsonElement> = when {
            root is JsonArray -> root
            root is JsonObject && root["recipes"] is JsonArray -> root["recipes"]!!.jsonArray
            root is JsonObject -> listOf(root)
            else -> throw IllegalArgumentException("Kein Rezept-Objekt gefunden")
        }
        return elements.map { json.decodeFromJsonElement(Recipe.serializer(), it.jsonObject) }
    }

    private fun stripCodeFence(text: String): String {
        val t = text.trim().removePrefix("﻿")
        if (!t.startsWith("```")) return t
        return t.lines().drop(1).dropLastWhile { it.isBlank() || it.trim() == "```" }.joinToString("\n")
    }
}

fun slugify(text: String): String {
    val replaced = text.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    val ascii = Normalizer.normalize(replaced, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    return ascii.replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "rezept" }
}

fun newRecipeId(title: String): String = slugify(title) + "-" + UUID.randomUUID().toString().take(6)

/** Ergänzt fehlende IDs und Zutaten-Keys und räumt Werte auf. */
fun Recipe.normalized(): Recipe {
    val usedKeys = mutableSetOf<String>()
    val ings = ingredients.filter { it.name.isNotBlank() }.map { ing ->
        var key = ing.key?.takeIf { it.isNotBlank() }
            ?: slugify(ing.name.substringBefore(',').replace(Regex("\\(.*?\\)"), " "))
        if (!usedKeys.add(key)) {
            var i = 2
            while (!usedKeys.add("$key-$i")) i++
            key = "$key-$i"
        }
        ing.copy(
            key = key,
            name = ing.name.trim(),
            unit = ing.unit?.trim()?.ifEmpty { null },
            note = ing.note?.trim()?.ifEmpty { null },
            amount = ing.amount?.takeIf { it > 0 && it.isFinite() },
        )
    }
    return copy(
        format = RECIPE_FORMAT,
        id = id.trim().ifEmpty { newRecipeId(title) },
        title = title.trim(),
        description = description.trim(),
        servings = servings.coerceIn(1, 99),
        tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
        ingredients = ings,
        steps = steps.filter { it.text.isNotBlank() }.map { s ->
            s.copy(text = s.text.trim(), timer = s.timer?.takeIf { it.seconds > 0 })
        },
        rating = rating?.takeIf { it in 1..5 },
    )
}
