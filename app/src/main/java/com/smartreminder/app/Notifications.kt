package com.smartreminder.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat

object Notifications {

    const val CHANNEL = "reminders"
    const val ACTION_DONE = "com.smartreminder.app.DONE"
    const val ACTION_SNOOZE = "com.smartreminder.app.SNOOZE"
    const val SNOOZE_MINUTES = 10

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "التذكيرات", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "إشعارات منبثقة للمواعيد والأعمال"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 200, 350)
            // الصوت يتولاه التطبيق نفسه (جرس مرتين ثم نطق) عند قفل الهاتف
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    fun idFor(reminderId: Long): Int = (reminderId % Int.MAX_VALUE).toInt()

    fun build(ctx: Context, r: Reminder, rel: String): android.app.Notification {
        val text = Speech.notificationText(r)
        val open = PendingIntent.getActivity(
            ctx,
            idFor(r.id),
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(Speech.notificationTitle(r, rel))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setWhen(r.eventAt)
            .setShowWhen(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "تم", actionIntent(ctx, ACTION_DONE, r.id))
            .addAction(0, "غفوة $SNOOZE_MINUTES د", actionIntent(ctx, ACTION_SNOOZE, r.id))
            .build()
    }

    /** إشعار بديل صامت لحالة نادرة (التذكير محذوف لحظة التنبيه). */
    fun placeholder(ctx: Context): android.app.Notification =
        NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("تذكير")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun actionIntent(ctx: Context, action: String, id: Long): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_ID, id)
        i.data = Uri.parse("reminder://$id/$action")
        return PendingIntent.getBroadcast(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
