package com.xflux.callblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Backup local versionado. Exporta as listas, preferências e o histórico Room.
 * A restauração mescla listas e histórico para não apagar dados já existentes.
 */
object BackupManager {
    private const val FORMAT = "callblocker-pro-backup"
    private const val SCHEMA_VERSION = 1
    private val preferenceFiles = listOf(
        "allow_list",
        "manual_block_list",
        "protection_settings",
        "history",
        "history_room_migrated"
    )

    suspend fun export(context: Context, output: OutputStream): Int = withContext(Dispatchers.IO) {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("createdAt", System.currentTimeMillis())

        val preferences = JSONObject()
        preferenceFiles.forEach { fileName ->
            val source = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            val entries = JSONObject()
            source.all.forEach { (key, value) ->
                val encoded = when (value) {
                    is String -> JSONObject().put("type", "string").put("value", value)
                    is Boolean -> JSONObject().put("type", "boolean").put("value", value)
                    is Int -> JSONObject().put("type", "int").put("value", value)
                    is Long -> JSONObject().put("type", "long").put("value", value)
                    is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
                    is Set<*> -> {
                        val array = JSONArray()
                        value.filterIsInstance<String>().forEach { array.put(it) }
                        JSONObject().put("type", "string_set").put("value", array)
                    }
                    else -> null
                }
                if (encoded != null) entries.put(key, encoded)
            }
            preferences.put(fileName, entries)
        }
        root.put("preferences", preferences)

        val history = JSONArray()
        val rows = AppDb.get(context).blockedDao().getAll()
        rows.forEach { row ->
            history.put(
                JSONObject()
                    .put("number", row.number)
                    .put("timestamp", row.timestamp)
                    .put("reason", row.reason)
            )
        }
        root.put("history", history)
        output.write(root.toString(2).toByteArray(Charsets.UTF_8))
        output.flush()
        rows.size
    }

    suspend fun restore(context: Context, input: InputStream): Int = withContext(Dispatchers.IO) {
        val jsonText = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        require(jsonText.length <= 25 * 1024 * 1024) { "Arquivo muito grande para restauração." }
        val root = JSONObject(jsonText)
        require(root.optString("format") == FORMAT) { "Este arquivo não é um backup do CallBlocker Pro." }
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "Versão de backup não compatível." }
        val preferences = root.optJSONObject("preferences")
            ?: throw IllegalArgumentException("O backup não contém as configurações.")
        val history = root.optJSONArray("history")
            ?: throw IllegalArgumentException("O backup não contém o histórico.")
        require(history.length() <= 100_000) { "O histórico do backup excede o limite permitido." }

        // Validar todo o conteúdo antes de gravar qualquer dado.
        val decoded = mutableMapOf<String, MutableMap<String, Any>>()
        preferenceFiles.forEach { fileName ->
            val entries = preferences.optJSONObject(fileName) ?: return@forEach
            val values = mutableMapOf<String, Any>()
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = entries.optJSONObject(key) ?: continue
                val value = when (item.optString("type")) {
                    "string" -> item.getString("value")
                    "boolean" -> item.getBoolean("value")
                    "int" -> item.getInt("value")
                    "long" -> item.getLong("value")
                    "float" -> item.getDouble("value").toFloat()
                    "string_set" -> {
                        val array = item.getJSONArray("value")
                        require(array.length() <= 20_000) { "Lista muito grande no backup." }
                        buildSet {
                            for (i in 0 until array.length()) {
                                val entry = array.getString(i)
                                require(entry.length <= 128) { "Número inválido no backup." }
                                add(entry)
                            }
                        }
                    }
                    else -> throw IllegalArgumentException("Tipo de configuração desconhecido no backup.")
                }
                values[key] = value
            }
            decoded[fileName] = values
        }

        val importedRows = ArrayList<BlockedCall>(history.length())
        for (i in 0 until history.length()) {
            val item = history.getJSONObject(i)
            val number = item.getString("number")
            val timestamp = item.getLong("timestamp")
            val reason = item.getString("reason")
            require(number.isNotBlank() && number.length <= 64) { "Número inválido no histórico do backup." }
            require(timestamp > 0L) { "Data inválida no histórico do backup." }
            require(reason.length <= 256) { "Motivo inválido no histórico do backup." }
            importedRows += BlockedCall(number = number, timestamp = timestamp, reason = reason)
        }

        // Mesclar listas com as atuais. Números bloqueados continuam tendo prioridade.
        val allowValues = decoded["allow_list"]?.get("numbers") as? Set<String>
        val blockValues = decoded["manual_block_list"]?.get("numbers") as? Set<String>
        if (allowValues != null) {
            val current = AllowList.manual(context)
            val merged = (current + allowValues).toMutableSet()
            val blocked = (BlockList.all(context) + (blockValues ?: emptySet()))
            merged.removeAll { allowed -> blocked.any { AllowList.normalize(it) == AllowList.normalize(allowed) } }
            decoded.getOrPut("allow_list") { mutableMapOf() }["numbers"] = merged
        }
        if (blockValues != null) {
            decoded.getOrPut("manual_block_list") { mutableMapOf() }["numbers"] =
                (BlockList.all(context) + blockValues).toSet()
        }

        decoded.forEach { (fileName, values) ->
            val editor = context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toMutableSet())
                }
            }
            editor.apply()
        }

        val dao = AppDb.get(context).blockedDao()
        val existing = dao.getAll().map { Triple(it.number, it.timestamp, it.reason) }.toMutableSet()
        var inserted = 0
        importedRows.forEach { row ->
            val key = Triple(row.number, row.timestamp, row.reason)
            if (existing.add(key)) {
                dao.insert(row)
                inserted++
            }
        }
        inserted
    }
}
