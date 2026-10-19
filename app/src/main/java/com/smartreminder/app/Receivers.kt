package com.smartreminder.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** جدولة التنبيهات الدقيقة عبر AlarmManager (تعمل حتى مع وضع توفير الطاقة Doze). */
object AlarmScheduler {

    fun schedule(ctx: Context, r: Reminder, fireAt: Long = r.fireAt) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, r.id)
        // إذا كان الوقت قد فات (مثلاً الموعد بعد 10 دقائق والتنبيه قبل نصف ساعة) ينطلق خلال ثانية
        val at = fireAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id))
    }

    private fun pending(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, id)
        i.data = Uri.parse("reminder://alarm/$id")
        return PendingIntent.getBroadcast(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** يستقبل لحظة التنبيه ويشغّل خدمة التنبيه الذكي. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id == -1L) return
        val svc = Intent(context, ReminderService::class.java).putExtra(EXTRA_ID, id)
        ContextCompat.startForegroundService(context, svc)
    }
}

/** أزرار الإشعار: "تم" و"غفوة". */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id == -1L) return
        NotificationManagerCompat.from(context).cancel(Notifications.idFor(id))
        if (intent.action == Notifications.ACTION_SNOOZE) {
            val r = ReminderStore(context).get(id) ?: return
            AlarmScheduler.schedule(
                context, r, System.currentTimeMillis() + Notifications.SNOOZE_MINUTES * 60_000L
            )
        }
    }
}

/** بعد إعادة تشغيل الهاتف أو تحديث التطبيق: إعادة جدولة كل التذكيرات القادمة. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        ReminderStore(context).all()
            .filter { !it.fired && it.eventAt > now }
            .forEach { AlarmScheduler.schedule(context, it) }
    }
}
