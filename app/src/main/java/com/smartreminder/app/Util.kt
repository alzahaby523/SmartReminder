package com.smartreminder.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** صياغة المدد الزمنية بالعربية الصحيحة (دقيقتين، ساعتين، نصف ساعة ...). */
object ArabicTime {

    private fun unit(n: Long, one: String, two: String, few: String, many: String): String = when {
        n == 1L -> one
        n == 2L -> two
        n in 3L..10L -> "$n $few"
        else -> "$n $many"
    }

    /** مدة مجردة مثل: "نصف ساعة" / "ساعة ونصف" / "15 دقيقة" / "ساعتين". */
    fun duration(totalMinutes: Long): String {
        val m = totalMinutes.coerceAtLeast(1L)
        val days = m / 1440
        val hours = (m % 1440) / 60
        val mins = m % 60

        if (days == 0L && hours == 0L && mins == 30L) return "نصف ساعة"

        val hoursText = unit(hours, "ساعة", "ساعتين", "ساعات", "ساعة")
        if (days == 0L && hours > 0L && mins == 30L) return "$hoursText ونصف"

        val parts = ArrayList<String>()
        if (days > 0) parts.add(unit(days, "يوم", "يومين", "أيام", "يومًا"))
        if (hours > 0) parts.add(hoursText)
        if (mins > 0) parts.add(unit(mins, "دقيقة", "دقيقتين", "دقائق", "دقيقة"))
        return parts.joinToString(" و")
    }

    /** "بعد نصف ساعة" أو "الآن" حسب الوقت المتبقي فعلياً عند لحظة التنبيه. */
    fun relative(eventAt: Long, now: Long): String {
        val minutes = Math.round((eventAt - now) / 60_000.0)
        return if (minutes <= 0L) "الآن" else "بعد " + duration(minutes)
    }
}

/** النصوص التي يُنطق بها التنبيه ويُعرض بها الإشعار. */
object Speech {

    fun spoken(r: Reminder, rel: String): String =
        if (r.type == Reminder.TYPE_APPOINTMENT) {
            "تنبيه. عندك موعد $rel مع ${r.title}."
        } else {
            "تنبيه. عندك عمل $rel: ${r.title}."
        }

    fun notificationTitle(r: Reminder, rel: String): String =
        (if (r.type == Reminder.TYPE_APPOINTMENT) "موعد " else "عمل ") + rel

    fun notificationText(r: Reminder): String {
        val time = Fmt.time(r.eventAt)
        return if (r.type == Reminder.TYPE_APPOINTMENT) {
            "مع ${r.title} · الساعة $time"
        } else {
            "${r.title} · الساعة $time"
        }
    }
}

/** تنسيق التواريخ بأسماء عربية وأرقام لاتينية. */
object Fmt {
    private val loc: Locale = Locale.forLanguageTag("ar-u-nu-latn")

    fun time(ms: Long): String = SimpleDateFormat("HH:mm", loc).format(Date(ms))

    fun dateShort(ms: Long): String = SimpleDateFormat("EEE d MMM yyyy", loc).format(Date(ms))

    fun dateTime(ms: Long): String =
        SimpleDateFormat("EEEE d MMMM yyyy", loc).format(Date(ms)) + " · " + time(ms)

    fun leadLabel(min: Int): String =
        if (min <= 0) "عند الموعد" else "قبل " + ArabicTime.duration(min.toLong())
}
