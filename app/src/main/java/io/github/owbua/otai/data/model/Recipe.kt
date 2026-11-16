package io.github.owbua.otai.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.builtins.ListSerializer

const val RECIPE_FORMAT = "kochbuch-rezept/1"

/**
 * Ein Rezept. Diese Klasse ist gleichzeitig das Import-/Exportformat
 * (siehe rezept-format/SCHEMA.md) und das Speicherformat der App.
 */
@Serializable
data class Recipe(
    val format: String = RECIPE_FORMAT,
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val servings: Int = 2,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val difficulty: String? = null,
    val times: Times = Times(),
    @Serializable(with = IngredientListSerializer::class)
    val ingredients: List<Ingredient> = emptyList(),
    @Serializable(with = StepListSerializer::class)
    val steps: List<Step> = emptyList(),
    val tips: List<Tip> = emptyList(),
    val nutrition: Nutrition? = null,
    val source: Source? = null,
    val favorite: Boolean = false,
    val rating: Int? = null,
    val cookedCount: Int = 0,
    val lastCooked: String? = null,
    val notes: List<Note> = emptyList(),
    /** Dateiname des Fotos (im Export unter `fotos/`), sonst null. */
    val photo: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class Times(
    val totalMin: Int? = null,
    val activeMin: Int? = null,
    val cookMin: Int? = null,
)

@Serializable
data class Ingredient(
    val key: String? = null,
    @Serializable(with = AmountSerializer::class)
    val amount: Double? = null,
    val unit: String? = null,
    val name: String = "",
    val note: String? = null,
    val group: String? = null,
)

@Serializable
data class Step(
    val text: String = "",
    val ingredients: List<String> = emptyList(),
    val timer: StepTimer? = null,
)

@Serializable
data class StepTimer(
    val seconds: Int,
    val label: String? = null,
)

@Serializable
data class Tip(
    val title: String? = null,
    val text: String = "",
)

@Serializable
data class Nutrition(
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
)

@Serializable
data class Source(
    val name: String? = null,
    val author: String? = null,
    val url: String? = null,
)

@Serializable
data class Note(
    val date: String,
    val text: String = "",
    /** Automatisch erzeugte Änderungszeilen aus der Rückmeldung, z. B. „Möhre(n): 2 → 3“. */
    val changes: List<String> = emptyList(),
    val rating: Int? = null,
)

/**
 * Menge: akzeptiert Zahlen und Strings wie "0,5", "1/2", "½", "1 ½" oder "2-3" (nimmt den ersten Wert).
 * Nicht lesbare Werte werden zu null (= „nach Belieben“).
 */
object AmountSerializer : KSerializer<Double?> {
    override val descriptor = PrimitiveSerialDescriptor("Amount", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }

    override fun deserialize(decoder: Decoder): Double? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeDouble()
        if (element is JsonNull || element !is JsonPrimitive) return null
        element.doubleOrNull?.let { return it }
        return parseAmount(element.content)
    }
}

private val FRACTIONS = mapOf(
    '½' to 0.5, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '¼' to 0.25, '¾' to 0.75,
    '⅛' to 0.125, '⅕' to 0.2, '⅙' to 1.0 / 6,
)

fun parseAmount(raw: String): Double? {
    var s = raw.trim().lowercase()
        .removePrefix("ca.").removePrefix("ca").removePrefix("etwa").trim()
    if (s.isEmpty()) return null
    // Bereich "2-3" / "2–3" → erster Wert
    s = s.split('-', '–', '—').first().trim()
    var total = 0.0
    var found = false
    for (part in s.split(' ').filter { it.isNotBlank() }) {
        val p = part.trim()
        when {
            p.length == 1 && FRACTIONS.containsKey(p[0]) -> { total += FRACTIONS.getValue(p[0]); found = true }
            p.last() in FRACTIONS && p.dropLast(1).toDoubleOrNull() != null -> {
                total += p.dropLast(1).toDouble() + FRACTIONS.getValue(p.last()); found = true
            }
            '/' in p -> {
                val (a, b) = p.split('/', limit = 2)
                val n = a.replace(',', '.').toDoubleOrNull()
                val d = b.replace(',', '.').toDoubleOrNull()
                if (n != null && d != null && d != 0.0) { total += n / d; found = true } else return if (found) total else null
            }
            else -> {
                val v = p.replace(',', '.').toDoubleOrNull() ?: return if (found) total else null
                total += v; found = true
            }
        }
    }
    return if (found) total else null
}

/** Erlaubt Zutaten auch als reine Strings: "Salz" → {"name": "Salz"}. */
object IngredientListSerializer : JsonTransformingSerializer<List<Ingredient>>(ListSerializer(Ingredient.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element !is JsonArray) JsonArray(emptyList())
        else JsonArray(element.map { if (it is JsonPrimitive) JsonObject(mapOf("name" to it)) else it })
}

/** Erlaubt Schritte auch als reine Strings: "Kartoffeln schälen" → {"text": "…"}. */
object StepListSerializer : JsonTransformingSerializer<List<Step>>(ListSerializer(Step.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element !is JsonArray) JsonArray(emptyList())
        else JsonArray(element.map { if (it is JsonPrimitive) JsonObject(mapOf("text" to it)) else it })
}
