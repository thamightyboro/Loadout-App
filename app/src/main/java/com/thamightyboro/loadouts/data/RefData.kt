package com.thamightyboro.loadouts.data

import android.content.Context
import org.json.JSONObject

/** Average + modifier for one stat of one component template (from the server datatables). */
data class RefStat(val avg: Double, val mod: Double, val uniform: Boolean = false)

data class RefItem(val id: String, val type: PartType, val re: Int, val stats: Map<String, RefStat>) {
    val name: String = prettyName(id)
}

/** Every lootable/buyable ship component and the Space Duty token vendor lists. */
class RefData(
    val items: List<RefItem>,
    val vendor: Map<PartType, Map<Int, List<String>>>,
    private val priceBase: Int,
    private val pricePerLevel: Int,
) {
    val byId: Map<String, RefItem> = items.associateBy { it.id }

    /** trial.getSpaceDutyTokenPrice: 50 + 5 x level */
    fun price(level: Int) = priceBase + pricePerLevel * level

    fun vendorItems(type: PartType, level: Int): List<RefItem> =
        vendor[type]?.get(level).orEmpty().mapNotNull(byId::get)

    /** Vendor levels an item is sold at (empty if it's loot-only). */
    fun vendorLevels(item: RefItem): List<Int> =
        vendor[item.type].orEmpty().filterValues { item.id in it }.keys.sorted()

    companion object {
        private val typeNames = mapOf(
            "armor" to PartType.ARMOR, "booster" to PartType.BOOSTER, "capacitor" to PartType.CAPACITOR,
            "droid_interface" to PartType.DROID_INTERFACE, "engine" to PartType.ENGINE,
            "reactor" to PartType.REACTOR, "shield" to PartType.SHIELD, "weapon" to PartType.WEAPON,
        )

        fun load(context: Context): RefData? = runCatching {
            val o = JSONObject(context.assets.open("components.json").bufferedReader().use { it.readText() })
            val arr = o.getJSONArray("items")
            val items = (0 until arr.length()).mapNotNull { i ->
                val it = arr.getJSONObject(i)
                val type = typeNames[it.getString("type")] ?: return@mapNotNull null
                val s = it.getJSONObject("s")
                val stats = s.keys().asSequence().associateWith { k ->
                    val a = s.getJSONArray(k)
                    RefStat(a.getDouble(0), a.getDouble(1), a.length() > 2 && a.getString(2) == "u")
                }
                RefItem(it.getString("id"), type, it.optInt("re"), stats)
            }
            val v = o.getJSONObject("vendor")
            val vendor = v.keys().asSequence().mapNotNull { t ->
                val type = typeNames[t] ?: return@mapNotNull null
                val levels = v.getJSONObject(t)
                type to levels.keys().asSequence().associate { lv ->
                    val list = levels.getJSONArray(lv)
                    lv.toInt() to (0 until list.length()).map { list.getString(it) }
                }
            }.toMap()
            val price = o.getJSONObject("price")
            RefData(items, vendor, price.getInt("base"), price.getInt("perLevel"))
        }.getOrNull()

        private val prefixes = setOf("shd", "wpn", "eng", "rct", "arm", "cap", "bst", "ddi", "armor")
        private val upperWords = Regex("^(mk\\d*|[a-z]{1,2}\\d+[a-z]?|\\d+[a-z]*|[ivx]+|sfs|sds|kse|taim|esp|hk|cnb|ddi)$")

        /** "shd_koensayr_deflector_m8" -> "Koensayr Deflector M8" */
        fun prettyName(id: String): String {
            val parts = id.split('_').filter { it.isNotBlank() }
            val words = if (parts.size > 1 && parts[0] in prefixes) parts.drop(1) else parts
            return words.joinToString(" ") { w ->
                if (upperWords.matches(w)) w.uppercase() else w.replaceFirstChar { it.uppercase() }
            }
        }
    }
}
