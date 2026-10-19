package com.smartreminder.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val EXTRA_ID = "reminder_id"

/**
 * تذكير واحد.
 * eventAt  = وقت الموعد الفعلي.
 * leadMin  = قبل كم دقيقة من الموعد يصل التنبيه (0 = عند الموعد).
 * voice    = true: تنبيه صوتي (جرس مرتين + نطق) عندما يكون الهاتف مقفلاً، false: مرئي فقط.
 */
data class Reminder(
    val id: Long,
    val title: String,
    val type: Int,
    val eventAt: Long,
    val leadMin: Int,
    val voice: Boolean,
    val fired: Boolean = false
) {
    val fireAt: Long get() = eventAt - leadMin * 60_000L

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("type", type)
        put("eventAt", eventAt)
        put("leadMin", leadMin)
        put("voice", voice)
        put("fired", fired)
    }

    companion object {
        const val TYPE_APPOINTMENT = 0
        const val TYPE_TASK = 1

        fun fromJson(o: JSONObject) = Reminder(
            id = o.getLong("id"),
            title = o.getString("title"),
            type = o.optInt("type", TYPE_APPOINTMENT),
            eventAt = o.getLong("eventAt"),
            leadMin = o.optInt("leadMin", 0),
            voice = o.optBoolean("voice", true),
            fired = o.optBoolean("fired", false)
        )
    }
}

/** تخزين بسيط في SharedPreferences بصيغة JSON (بدون قاعدة بيانات خارجية). */
class ReminderStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun all(): List<Reminder> = synchronized(LOCK) {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
        val list = ArrayList<Reminder>()
        for (i in 0 until arr.length()) {
            try {
                list.add(Reminder.fromJson(arr.getJSONObject(i)))
            } catch (e: Exception) {
                // تجاهل السجل التالف
            }
        }
        list.sortedBy { it.eventAt }
    }

    fun get(id: Long): Reminder? = all().firstOrNull { it.id == id }

    fun upsert(r: Reminder) = synchronized(LOCK) {
        save(all().filter { it.id != r.id } + r)
    }

    fun delete(id: Long) = synchronized(LOCK) {
        save(all().filter { it.id != id })
    }

    private fun save(list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).commit()
    }

    private companion object {
        const val KEY = "list"
        val LOCK = Any()
    }
}
