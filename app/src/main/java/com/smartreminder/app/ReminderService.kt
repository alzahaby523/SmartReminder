package com.smartreminder.app

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ServiceCompat
import java.util.Locale

/**
 * قلب التطبيق: يقرر كيف يُنبَّه الشخص لحظة وصول التذكير.
 *
 *  • الهاتف مفتوح (الشاشة تعمل وغير مقفل)  → إشعار منبثق مباشر فقط.
 *  • الهاتف مقفل/الشاشة مطفأة وبوضع "صوتي" → جرس مرتين ثم نطق التذكير صوتياً
 *    ("عندك موعد بعد نصف ساعة مع ...") + الإشعار.
 *  • وضع "صوري فقط"                       → إشعار منبثق + اهتزاز بدون صوت.
 */
class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var active = false
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        Notifications.ensureChannel(this)

        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        val store = ReminderStore(this)
        val reminder = store.get(id)
        if (reminder == null) {
            // لازم نستدعي startForeground خلال 5 ثوان حتى لو لم يعد التذكير موجوداً
            enterForeground(1, Notifications.placeholder(this))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val rel = ArabicTime.relative(reminder.eventAt, System.currentTimeMillis())
        val notification = Notifications.build(this, reminder, rel)
        val notifId = Notifications.idFor(reminder.id)
        store.upsert(reminder.copy(fired = true))

        if (active) {
            // تذكير آخر وصل أثناء تشغيل تنبيه سابق: نعرضه كإشعار فقط حتى لا تتداخل الأصوات
            getSystemService(NotificationManager::class.java).notify(notifId, notification)
            return START_NOT_STICKY
        }
        active = true

        enterForeground(notifId, notification)
        acquireWakeLock()

        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        val phoneOpen = pm.isInteractive && !km.isKeyguardLocked

        if (phoneOpen || !reminder.voice) {
            // إشعار منبثق فقط. نترك الخدمة ثانيتين ونصف حتى يظهر الإشعار بشكل مؤكد
            handler.postDelayed({ finish() }, 2_500L)
        } else {
            handler.postDelayed({ finish() }, 45_000L) // شبكة أمان
            requestAudioFocus()
            playBell(2) { speak(Speech.spoken(reminder, rel)) }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(id: Int, n: android.app.Notification) {
        ServiceCompat.startForeground(
            this, id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun alarmAttrs(contentType: Int): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(contentType)
            .build()

    /** الجرس: يُشغَّل [times] مرات متتالية ثم يستدعي [onDone]. */
    private fun playBell(times: Int, onDone: () -> Unit) {
        if (times <= 0) {
            onDone()
            return
        }
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(alarmAttrs(AudioAttributes.CONTENT_TYPE_SONIFICATION))
            resources.openRawResourceFd(R.raw.bell).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
                playBell(times - 1, onDone)
            }
            mp.setOnErrorListener { p, _, _ ->
                p.release()
                if (player === p) player = null
                onDone()
                true
            }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            mp.release()
            player = null
            onDone()
        }
    }

    /** نطق التذكير بالعربية. إن لم يتوفر صوت عربي في الهاتف نكتفي بالجرس والإشعار. */
    private fun speak(text: String) {
        tts = TextToSpeech(applicationContext, TextToSpeech.OnInitListener { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                finish()
                return@OnInitListener
            }
            val result = engine.setLanguage(Locale.forLanguageTag("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                finish()
                return@OnInitListener
            }
            engine.setAudioAttributes(alarmAttrs(AudioAttributes.CONTENT_TYPE_SPEECH))
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    handler.postDelayed({ finish() }, 400L)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.post { finish() }
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder")
        })
    }

    private fun requestAudioFocus() {
        val am = getSystemService(AudioManager::class.java)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAttrs(AudioAttributes.CONTENT_TYPE_SONIFICATION))
            .build()
        am.requestAudioFocus(req)
        focusRequest = req
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smartreminder:alert").apply {
            acquire(60_000L)
        }
    }

    private fun finish() {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        // نترك الإشعار ظاهراً في الشريط ليقدر الشخص يفتحه أو يغفيه
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf(lastStartId)
    }

    override fun onDestroy() {
        active = true // كي ينفّذ finish() التنظيف حتى لو دُمّرت الخدمة فجأة
        finish()
        super.onDestroy()
    }
}
