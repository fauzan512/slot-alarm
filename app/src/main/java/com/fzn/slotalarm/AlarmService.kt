package com.fzn.slotalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.fzn.slotalarm.START"
        const val ACTION_STOP = "com.fzn.slotalarm.STOP"
        const val CHANNEL_ID = "slotalarm_service"
        const val ALARM_CHANNEL_ID = "slotalarm_alarm"
        const val NOTIF_ID = 1001
        var isRunning = false
    }

    private var wsClient: WebSocketClient? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmActive = false

    override fun onCreate() {
        super.onCreate()
        createChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildServiceNotif("Menghubungkan..."))
                isRunning = true
                connectWebSocket()
            }
            ACTION_STOP -> {
                stopAlarm()
                disconnectWebSocket()
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ── WebSocket ──────────────────────────────────────────────

    private fun connectWebSocket() {
        val prefs = getSharedPreferences("slotalarm", MODE_PRIVATE)
        val server = prefs.getString("server", "ws://43.156.47.36/alarm") ?: return
        val token = prefs.getString("token", "") ?: return

        val uri = URI(server)

        wsClient = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                updateNotif("Terhubung — mantau...")
                send("""{"token":"$token"}""")
            }

            override fun onMessage(message: String?) {
                message ?: return
                if (message.contains("stock_kosong")) {
                    triggerAlarm()
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                updateNotif("Terputus — reconnect...")
                android.os.Handler(mainLooper).postDelayed({
                    if (isRunning) connectWebSocket()
                }, 5000)
            }

            override fun onError(ex: Exception?) {
                updateNotif("Error: ${ex?.message}")
            }
        }

        // Cert server SAN-nya IP-only + critical marker, yang bikin
        // Java-WebSocket gagal validasi. Server ini IP publik dengan token
        // auth, jadi validasi cert dimatiin.
        if (uri.scheme.equals("wss", ignoreCase = true)) {
            try {
                val trustAll = object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<java.security.cert.X509Certificate>?, authType: String?
                    ) = Unit
                    override fun checkServerTrusted(
                        chain: Array<java.security.cert.X509Certificate>?, authType: String?
                    ) = Unit
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
                        emptyArray()
                }
                val sslCtx = javax.net.ssl.SSLContext.getInstance("TLS")
                sslCtx.init(null, arrayOf<javax.net.ssl.TrustManager>(trustAll), null)
                wsClient?.setSocketFactory(sslCtx.socketFactory)
            } catch (e: Exception) {
                updateNotif("SSL setup gagal: ${e.message}")
            }
        }

        try {
            wsClient?.connect()
        } catch (e: Exception) {
            updateNotif("Gagal connect: ${e.message}")
        }
    }

    private fun disconnectWebSocket() {
        try {
            wsClient?.close()
        } catch (_: Exception) {}
        wsClient = null
    }

    // ── Alarm ──────────────────────────────────────────────────

    private fun triggerAlarm() {
        if (alarmActive) return
        alarmActive = true

        // bunyi alarm: pakai custom MP3 kalau ada, kalau nggak pakai default
        try {
            val prefs = getSharedPreferences("slotalarm", MODE_PRIVATE)
            val customUri = prefs.getString("alarm_mp3", null)

            val soundUri: Uri = if (!customUri.isNullOrEmpty()) {
                Uri.parse(customUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, soundUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true   // berulang sampai dimatiin
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // fallback ke alarm default kalau custom gagal
            try {
                val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {}
        }

        // getar
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 1000, 500, 1000, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        // notif full-screen
        showAlarmNotif()
        updateNotif("🚨 ALARM — data kosong!")
    }

    private fun stopAlarm() {
        alarmActive = false
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID + 1)
    }

    // ── Notifikasi ─────────────────────────────────────────────

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            NotificationChannel(CHANNEL_ID, "Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Status koneksi"
                nm.createNotificationChannel(this)
            }

            NotificationChannel(
                ALARM_CHANNEL_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm data kosong"
                enableVibration(true)
                setBypassDnd(true)
                nm.createNotificationChannel(this)
            }
        }
    }

    private fun buildServiceNotif(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Slot Alarm")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun showAlarmNotif() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("🚨 DATA KOSONG")
            .setContentText("Domain ditarik tapi data bisnis kosong. Isi sekarang!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(false)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID + 1, notif)
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildServiceNotif(text))
    }

    // ── WakeLock ───────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "slotalarm::ws"
        ).apply {
            acquire(10 * 60 * 60 * 1000L)  // max 10 jam
        }
    }

    override fun onDestroy() {
        stopAlarm()
        disconnectWebSocket()
        isRunning = false
        try {
            wakeLock?.release()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
