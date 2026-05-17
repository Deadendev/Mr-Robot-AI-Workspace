package com.mrrobot.aiworkspace.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.skillDataStore by preferencesDataStore(name = "mr_robot_skills")

/**
 * A composable AI capability the user has equipped on the assistant.
 *
 * A "skill" is anything the user wants Mr. Robot to consistently do — a
 * persona instruction, a tool habit, a domain expertise — and is stitched
 * into the system prompt at request time.
 */
data class Skill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val instructions: String = "",
    val category: SkillCategory = SkillCategory.GENERAL,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SkillCategory(val displayName: String, val emoji: String) {
    GENERAL("General", "✨"),
    CODING("Coding", "💻"),
    WRITING("Writing", "✍️"),
    RESEARCH("Research", "🔍"),
    PRODUCTIVITY("Productivity", "⚡"),
    DESIGN("Design", "🎨"),
    DATA("Data", "📊"),
    AUTOMATION("Automation", "🤖");

    companion object {
        fun fromString(value: String?): SkillCategory =
            values().firstOrNull { it.name == value } ?: GENERAL
    }
}

object SkillCatalog {
    /**
     * Default starter skills the user gets out-of-the-box. They can be
     * disabled, edited, or deleted just like custom skills.
     */
    val builtInSkills: List<Skill> = listOf(
        Skill(
            id = "builtin_concise",
            name = "Concise Answers",
            description = "Reply briefly and to the point. Skip filler.",
            instructions = "Always answer in the fewest words possible while staying complete. " +
                "Skip apologies, preamble, and 'as an AI…' filler. Use bullet points when listing.",
            category = SkillCategory.PRODUCTIVITY,
            enabled = true,
            isBuiltIn = true
        ),
        Skill(
            id = "builtin_code_first",
            name = "Code-First",
            description = "Lead with code, then explain.",
            instructions = "When the user asks anything code-related, lead with the working " +
                "code block and only briefly explain afterwards. Always include imports and " +
                "use the most idiomatic style for the language.",
            category = SkillCategory.CODING,
            enabled = true,
            isBuiltIn = true
        ),
        Skill(
            id = "builtin_step_by_step",
            name = "Step-by-Step",
            description = "Break complex tasks into numbered steps.",
            instructions = "For any task with more than one step, output a numbered list of " +
                "concrete actions. Each step must be independently verifiable.",
            category = SkillCategory.GENERAL,
            enabled = false,
            isBuiltIn = true
        ),
        Skill(
            id = "builtin_research_cite",
            name = "Cite Sources",
            description = "Cite sources when stating facts.",
            instructions = "When stating any fact about the real world, products, people, or " +
                "events, name your source. If you are not sure, say so explicitly instead of " +
                "guessing.",
            category = SkillCategory.RESEARCH,
            enabled = false,
            isBuiltIn = true
        )
    )
}

/**
 * DataStore-backed persistence for skills. The JSON blob holds both the
 * built-in skills (so users can toggle them off / edit them) and any
 * custom skills they create.
 */
class SkillStore(private val context: Context) {

    private val mutex = Mutex()

    private object Keys {
        val SKILLS_JSON = stringPreferencesKey("skills_json")
    }

    val skillsFlow: Flow<List<Skill>> =
        context.skillDataStore.data.map { prefs ->
            val raw = prefs[Keys.SKILLS_JSON].orEmpty()
            mergeWithBuiltIns(decode(raw))
        }

    suspend fun getAll(): List<Skill> = skillsFlow.first()

    suspend fun getEnabled(): List<Skill> = getAll().filter { it.enabled }

    suspend fun getSkill(id: String): Skill? = getAll().firstOrNull { it.id == id }

    suspend fun upsert(skill: Skill): Skill = mutex.withLock {
        val current = readRaw().toMutableList()
        val idx = current.indexOfFirst { it.id == skill.id }
        val final = skill.copy(updatedAt = System.currentTimeMillis())
        if (idx >= 0) current[idx] = final else current.add(final)
        save(current)
        final
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Boolean = mutex.withLock {
        val current = readRaw().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            current[idx] = current[idx].copy(
                enabled = enabled,
                updatedAt = System.currentTimeMillis()
            )
            save(current)
            return@withLock true
        }

        // Built-in not yet persisted → materialize it with overridden enabled.
        val builtIn = SkillCatalog.builtInSkills.firstOrNull { it.id == id }
        if (builtIn != null) {
            current.add(
                builtIn.copy(
                    enabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
            )
            save(current)
            return@withLock true
        }

        false
    }

    suspend fun delete(id: String): Boolean = mutex.withLock {
        val current = readRaw().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) save(current)
        removed
    }

    suspend fun resetToDefaults() {
        mutex.withLock { save(emptyList()) }
    }

    /**
     * Merge persisted skills with built-ins so that:
     *  - Built-ins always appear, even on a fresh install.
     *  - Built-ins the user customized override the catalog defaults.
     *  - Custom user skills are listed after the built-ins.
     */
    private fun mergeWithBuiltIns(persisted: List<Skill>): List<Skill> {
        val byId = persisted.associateBy { it.id }
        val merged = mutableListOf<Skill>()

        // Built-ins first (with persisted overrides).
        SkillCatalog.builtInSkills.forEach { builtIn ->
            merged.add(byId[builtIn.id] ?: builtIn)
        }

        // Then any user-created skills.
        persisted.forEach { skill ->
            if (SkillCatalog.builtInSkills.none { it.id == skill.id }) {
                merged.add(skill)
            }
        }

        return merged
    }

    private suspend fun readRaw(): List<Skill> {
        val raw = context.skillDataStore.data.first()[Keys.SKILLS_JSON].orEmpty()
        return decode(raw)
    }

    private suspend fun save(skills: List<Skill>) {
        context.skillDataStore.edit { prefs ->
            prefs[Keys.SKILLS_JSON] = encode(skills)
        }
    }

    private fun encode(skills: List<Skill>): String {
        val arr = JSONArray()
        skills.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("description", s.description)
                    .put("instructions", s.instructions)
                    .put("category", s.category.name)
                    .put("enabled", s.enabled)
                    .put("isBuiltIn", s.isBuiltIn)
                    .put("createdAt", s.createdAt)
                    .put("updatedAt", s.updatedAt)
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String): List<Skill> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Skill(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", "Untitled skill"),
                    description = o.optString("description", ""),
                    instructions = o.optString("instructions", ""),
                    category = SkillCategory.fromString(o.optString("category")),
                    enabled = o.optBoolean("enabled", true),
                    isBuiltIn = o.optBoolean("isBuiltIn", false),
                    createdAt = o.optLong("createdAt", 0L),
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }
}
