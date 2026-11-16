package io.github.owbua.otai.data.model

import kotlinx.serialization.Serializable

/**
 * Alles rund um Planung und Einkauf – gespeichert in `kitchen.json`, im ZIP-Backup als `kueche.json`.
 */
@Serializable
data class KitchenState(
    val format: String = "kochbuch-kueche/1",
    val plan: List<PlanEntry> = emptyList(),
    /** Selbst hinzugefügte Einkaufsartikel. */
    val manualItems: List<ShoppingItem> = emptyList(),
    /** Abgehakte Artikel, die aus dem Wochenplan stammen (Schlüssel siehe [ShoppingLine.key]). */
    val checkedPlanKeys: Set<String> = emptySet(),
    /** Was zuhause vorrätig ist (Namen; ohne Mengen). */
    val pantry: List<PantryItem> = emptyList(),
    /** Grundzutaten, die nie auf die Einkaufsliste kommen. */
    val staples: List<String> = DEFAULT_STAPLES,
)

@Serializable
data class PlanEntry(
    val id: String,
    /** ISO-Datum, z. B. 2026-11-16 */
    val date: String,
    val recipeId: String,
    val servings: Int,
    val cooked: Boolean = false,
    /** Begründung, wenn der Eintrag aus „Vorschlag“ stammt. */
    val reason: String? = null,
)

@Serializable
data class ShoppingItem(
    val id: String,
    val name: String,
    val amount: Double? = null,
    val unit: String? = null,
    val checked: Boolean = false,
)

@Serializable
data class PantryItem(
    val name: String,
    val addedAt: String? = null,
)

val DEFAULT_STAPLES = listOf(
    "Salz", "Pfeffer", "Salz und Pfeffer", "Zucker", "Öl", "Olivenöl", "Rapsöl", "Sonnenblumenöl",
    "Pflanzenöl", "Essig", "Wasser",
)
