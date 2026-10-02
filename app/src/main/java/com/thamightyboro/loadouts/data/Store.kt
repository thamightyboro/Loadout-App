package com.thamightyboro.loadouts.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppData(
    val parts: List<Part> = emptyList(),
    val loadouts: List<Loadout> = emptyList(),
) {
    val partsById: Map<String, Part> get() = parts.associateBy { it.id }
}

/**
 * Everything lives in one small JSON file on the phone. Simple, and it doubles as
 * a backup format: export/import is just copying this text.
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "loadouts.json")
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (file.exists()) {
            runCatching { _data.value = decode(file.readText()) }
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "loadouts.json.tmp")
        tmp.writeText(encode(_data.value))
        tmp.renameTo(file)
    }

    suspend fun upsertPart(part: Part) {
        _data.update { d ->
            val exists = d.parts.any { it.id == part.id }
            d.copy(parts = if (exists) d.parts.map { if (it.id == part.id) part else it } else d.parts + part)
        }
        save()
    }

    suspend fun deletePart(id: String) {
        _data.update { d ->
            d.copy(
                parts = d.parts.filterNot { it.id == id },
                loadouts = d.loadouts.map { l -> l.copy(slots = l.slots.filterValues { it != id }) },
            )
        }
        save()
    }

    suspend fun upsertLoadout(loadout: Loadout) {
        _data.update { d ->
            val exists = d.loadouts.any { it.id == loadout.id }
            d.copy(loadouts = if (exists) d.loadouts.map { if (it.id == loadout.id) loadout else it } else d.loadouts + loadout)
        }
        save()
    }

    suspend fun deleteLoadout(id: String) {
        _data.update { d -> d.copy(loadouts = d.loadouts.filterNot { it.id == id }) }
        save()
    }

    fun exportJson(): String = encode(_data.value)

    /** Merges an exported backup in; items with the same id are replaced. */
    suspend fun importJson(text: String) {
        val incoming = decode(text)
        _data.update { d ->
            val parts = (d.parts.associateBy { it.id } + incoming.parts.associateBy { it.id }).values.toList()
            val loadouts = (d.loadouts.associateBy { it.id } + incoming.loadouts.associateBy { it.id }).values.toList()
            AppData(parts.sortedBy { it.createdAt }, loadouts.sortedBy { it.createdAt })
        }
        save()
    }

    companion object {
        fun encode(d: AppData): String = JSONObject().apply {
            put("version", 1)
            put("parts", JSONArray(d.parts.map { it.toJson() }))
            put("loadouts", JSONArray(d.loadouts.map { it.toJson() }))
        }.toString(2)

        fun decode(text: String): AppData {
            val o = JSONObject(text)
            return AppData(
                parts = o.optJSONArray("parts").objects().map { it.toPart() },
                loadouts = o.optJSONArray("loadouts").objects().map { it.toLoadout() },
            )
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

        private fun List<StatLine>.toJson() = JSONArray(map {
            JSONObject().put("label", it.label).put("value", it.value)
        })

        private fun JSONArray?.toStats() = objects().map {
            StatLine(it.optString("label"), it.optString("value"))
        }

        private fun Part.toJson() = JSONObject().apply {
            put("id", id); put("name", name); put("type", type.name)
            reLevel?.let { put("reLevel", it) }
            put("stats", stats.toJson()); put("qualities", qualities.toJson())
            put("notes", notes); put("createdAt", createdAt)
            refId?.let { put("refId", it) }
        }

        private fun JSONObject.toPart() = Part(
            id = getString("id"),
            name = optString("name"),
            type = PartType.fromName(optString("type")),
            reLevel = if (has("reLevel")) optInt("reLevel") else null,
            stats = optJSONArray("stats").toStats(),
            qualities = optJSONArray("qualities").toStats(),
            notes = optString("notes"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            refId = if (has("refId")) optString("refId") else null,
        )

        private fun Loadout.toJson() = JSONObject().apply {
            put("id", id); put("name", name); put("chassis", chassis)
            massLimit?.let { put("massLimit", it) }
            put("weaponSlots", weaponSlots)
            put("slots", JSONObject().also { s -> slots.forEach { (k, v) -> s.put(k.name, v) } })
            put("notes", notes); put("createdAt", createdAt)
        }

        private fun JSONObject.toLoadout(): Loadout {
            val s = optJSONObject("slots")
            val slots = buildMap {
                s?.keys()?.forEach { k -> Slot.fromName(k)?.let { put(it, s.getString(k)) } }
            }
            return Loadout(
                id = getString("id"),
                name = optString("name"),
                chassis = optString("chassis"),
                massLimit = if (has("massLimit")) optDouble("massLimit") else null,
                weaponSlots = optInt("weaponSlots", 2),
                slots = slots,
                notes = optString("notes"),
                createdAt = optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}
