package com.xflux.callblocker

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "blocked_calls")
data class BlockedCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val timestamp: Long,
    val reason: String
)

@Dao
interface BlockedDao {
    @Insert suspend fun insert(call: BlockedCall): Long
    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC") suspend fun getAll(): List<BlockedCall>
    @Query("SELECT COUNT(*) FROM blocked_calls") suspend fun count(): Int
    @Query("DELETE FROM blocked_calls WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM blocked_calls") suspend fun deleteAll()
}

@Database(entities = [BlockedCall::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun blockedDao(): BlockedDao
    companion object {
        @Volatile private var INSTANCE: AppDb? = null
        fun get(context: Context): AppDb = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "callblocker-history.db")
                .build().also { INSTANCE = it }
        }
    }
}

object HistoryStore {
    suspend fun add(context: Context, number: String, reason: String) = withContext(Dispatchers.IO) {
        AppDb.get(context).blockedDao().insert(
            BlockedCall(number = number.ifBlank { "Número oculto" }, timestamp = System.currentTimeMillis(), reason = reason)
        )
    }
}

object HistoryMigration {
    private const val LEGACY_PREF = "history"
    private const val MARKER_PREF = "history_room_migrated"
    suspend fun importLegacy(context: Context, dao: BlockedDao) = withContext(Dispatchers.IO) {
        val marker = context.getSharedPreferences(MARKER_PREF, Context.MODE_PRIVATE)
        if (marker.getBoolean("done", false)) return@withContext
        val rows = try {
            val json = org.json.JSONArray(context.getSharedPreferences(LEGACY_PREF, 0).getString("items", "[]"))
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val n = item.optString("number").ifBlank { "Número oculto" }
                    add(BlockedCall(number = n, timestamp = item.optLong("time", System.currentTimeMillis()),
                        reason = if (n == "Número oculto") "Número oculto" else "Número não autorizado"))
                }
            }
        } catch (_: Exception) { emptyList() }
        if (rows.isNotEmpty() && dao.count() == 0) rows.forEach { dao.insert(it) }
        marker.edit().putBoolean("done", true).apply()
    }
}