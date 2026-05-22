package com.mrrobot.aiworkspace.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kai 9000-style settings export/import.
 *
 * Serializes both DataStore preference files (`mr_robot_settings` and
 * `mr_robot_agent_config`) to a single JSON document. Each section is keyed
 * by its DataStore name, and each preference is stored as
 *   "key" -> { "type": "string|int|long|float|boolean|double|stringSet", "value": ... }
 *
 * The exported file contains sensitive data (API keys for every configured
 * provider, soul prompt, heartbeat schedule). Surface that warning in the UI
 * the same way Kai does.
 */
object SettingsImportExport {

    private const val VERSION = 1
    private const val APP_ID = "mr-robot-ai-workspace"

    private const val SECTION_SETTINGS = "mr_robot_settings"
    private const val SECTION_AGENT = "mr_robot_agent_config"

    /** Export every preference from both DataStore files into a JSON string. */
    suspend fun exportToJson(context: Context): String {
        val app = context.applicationContext

        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("version", VERSION)
        root.put("exported_at_epoch_ms", System.currentTimeMillis())

        val sections = JSONObject()
        sections.put(
            SECTION_SETTINGS,
            preferencesToJson(app.settingsDataStore.data.first())
        )
        sections.put(
            SECTION_AGENT,
            preferencesToJson(app.agentConfigDataStore.data.first())
        )
        root.put("sections", sections)

        return root.toString(2)
    }

    /**
     * Import a previously exported JSON document back into both DataStore files.
     * Unknown sections / keys are ignored gracefully so that an import from a
     * future version of the app degrades without crashing.
     *
     * If [replace] is true, all existing keys in each touched section are
     * cleared first (mirrors Kai's "Replace all" import switch). Otherwise the
     * import is additive — incoming keys overwrite, but other existing keys
     * are kept.
     */
    suspend fun importFromJson(
        context: Context,
        json: String,
        replace: Boolean = true
    ): ImportOutcome {
        val app = context.applicationContext

        val root = runCatching { JSONObject(json) }.getOrElse {
            return ImportOutcome.Failure("Not a valid JSON file")
        }

        val sections = root.optJSONObject("sections")
            ?: return ImportOutcome.Failure("Missing 'sections' object")

        var imported = 0
        var errors = 0

        sections.optJSONObject(SECTION_SETTINGS)?.let { obj ->
            val (ok, err) = applySection(
                obj = obj,
                replace = replace,
                edit = { mutator -> app.settingsDataStore.edit { mutator(it) } }
            )
            imported += ok
            errors += err
        }

        sections.optJSONObject(SECTION_AGENT)?.let { obj ->
            val (ok, err) = applySection(
                obj = obj,
                replace = replace,
                edit = { mutator -> app.agentConfigDataStore.edit { mutator(it) } }
            )
            imported += ok
            errors += err
        }

        return when {
            imported == 0 && errors > 0 -> ImportOutcome.Failure("Could not parse any settings")
            errors > 0 -> ImportOutcome.PartialSuccess(imported = imported, errorCount = errors)
            else -> ImportOutcome.Success(imported = imported)
        }
    }

    private suspend fun applySection(
        obj: JSONObject,
        replace: Boolean,
        edit: suspend (mutator: (Preferences.MutablePreferences) -> Unit) -> Unit
    ): Pair<Int, Int> {
        var imported = 0
        var errors = 0

        edit { mutable ->
            if (replace) mutable.clear()

            val keys = obj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val entry = obj.optJSONObject(name)
                if (entry == null) {
                    errors++
                } else {
                    val applied = applyTypedEntry(mutable, name, entry)
                    if (applied) imported++ else errors++
                }
            }
        }

        return imported to errors
    }

    private fun applyTypedEntry(
        mutable: Preferences.MutablePreferences,
        name: String,
        entry: JSONObject
    ): Boolean {
        val type = entry.optString("type")
        return runCatching {
            when (type) {
                "string" -> {
                    mutable[stringPreferencesKey(name)] = entry.optString("value", "")
                    true
                }
                "int" -> {
                    mutable[intPreferencesKey(name)] = entry.optInt("value", 0)
                    true
                }
                "long" -> {
                    mutable[longPreferencesKey(name)] = entry.optLong("value", 0L)
                    true
                }
                "float" -> {
                    mutable[floatPreferencesKey(name)] = entry.optDouble("value", 0.0).toFloat()
                    true
                }
                "double" -> {
                    mutable[doublePreferencesKey(name)] = entry.optDouble("value", 0.0)
                    true
                }
                "boolean" -> {
                    mutable[booleanPreferencesKey(name)] = entry.optBoolean("value", false)
                    true
                }
                "stringSet" -> {
                    val arr = entry.optJSONArray("value") ?: JSONArray()
                    val values = (0 until arr.length()).map { arr.optString(it, "") }.toSet()
                    mutable[stringSetPreferencesKey(name)] = values
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun preferencesToJson(prefs: Preferences): JSONObject {
        val obj = JSONObject()
        prefs.asMap().forEach { (key, value) ->
            val entry = JSONObject()
            when (value) {
                is String -> {
                    entry.put("type", "string")
                    entry.put("value", value)
                }
                is Int -> {
                    entry.put("type", "int")
                    entry.put("value", value)
                }
                is Long -> {
                    entry.put("type", "long")
                    entry.put("value", value)
                }
                is Float -> {
                    entry.put("type", "float")
                    entry.put("value", value.toDouble())
                }
                is Double -> {
                    entry.put("type", "double")
                    entry.put("value", value)
                }
                is Boolean -> {
                    entry.put("type", "boolean")
                    entry.put("value", value)
                }
                is Set<*> -> {
                    entry.put("type", "stringSet")
                    val arr = JSONArray()
                    value.forEach { arr.put(it?.toString() ?: "") }
                    entry.put("value", arr)
                }
                else -> {
                    entry.put("type", "string")
                    entry.put("value", value.toString())
                }
            }
            obj.put(key.name, entry)
        }
        return obj
    }
}

/**
 * Result of an import operation. Mirrors Kai's `ImportResult` (Success /
 * PartialSuccess / Failure) so the General tab UI can show identical states.
 */
sealed class ImportOutcome {
    data class Success(val imported: Int) : ImportOutcome()
    data class PartialSuccess(val imported: Int, val errorCount: Int) : ImportOutcome()
    data class Failure(val message: String) : ImportOutcome()
}
