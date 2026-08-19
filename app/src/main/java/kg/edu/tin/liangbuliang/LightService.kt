package kg.edu.tin.liangbuliang

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class LightService : Service() {

    private lateinit var repository: SettingsRepository
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            if (repository.isBrightnessEnabled || repository.isTimeoutEnabled) {
                if (repository.isBrightnessEnabled) {
                    repository.applyBrightnessToSystem()
                    updateOverlay()
                }
                if (repository.isTimeoutEnabled) {
                    repository.applyTimeoutToSystem()
                }
                updateNotification()
                handler.postDelayed(this, 15_000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // When restarted by the system after being killed, intent is null.
        // Re-apply all active settings so brightness / overlay are restored.
        val action = intent?.action
        if (action == null) {
            repository.saveOriginalBrightnessSettingsIfNeeded()
            repository.saveOriginalTimeoutSettingsIfNeeded()
            if (repository.isBrightnessEnabled) {
                repository.applyBrightnessToSystem()
            }
            if (repository.isTimeoutEnabled) {
                repository.applyTimeoutToSystem()
            }
            updateOverlay()
            startKeepAlive()
            startForeground(NOTIFICATION_ID, buildNotification())
            return START_STICKY
        }

        when (action) {
            ACTION_STOP -> {
                stopKeepAlive()
                removeOverlay()
                repository.restoreBrightnessSettings()
                repository.restoreTimeoutSettings()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                repository.saveOriginalBrightnessSettingsIfNeeded()
                repository.saveOriginalTimeoutSettingsIfNeeded()
                if (repository.isBrightnessEnabled) {
                    repository.applyBrightnessToSystem()
                }
                if (repository.isTimeoutEnabled) {
                    repository.applyTimeoutToSystem()
                }
                updateOverlay()
                startKeepAlive()
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_UPDATE_BRIGHTNESS -> {
                updateOverlay()
                if (repository.isBrightnessEnabled) {
                    repository.applyBrightnessToSystem()
                } else {
                    repository.restoreBrightnessSettings()
                }
                startKeepAlive()
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_UPDATE_TIMEOUT -> {
                updateOverlay()
                if (repository.isTimeoutEnabled) {
                    repository.applyTimeoutToSystem()
                } else {
                    repository.restoreTimeoutSettings()
                }
                startKeepAlive()
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }

        return START_STICKY
    }

    // --- Overlay ---

    private fun updateOverlay() {
        // Extra Dim (persistent system setting) takes over below-minimum dimming when available.
        if (repository.belowMinimumUsesExtraDim) {
            removeOverlay()
            return
        }

        // Keep the overlay present for the whole time brightness is enabled (fully transparent
        // at/above 1.0), so the only brightness change happens at enable/disable — not when
        // dragging across the 1.0 boundary. Remove it once brightness is disabled.
        if (!repository.isBrightnessEnabled) {
            removeOverlay()
            return
        }

        // Android 12+ blocks untrusted touches through a NOT_TOUCHABLE overlay whose
        // obscuring opacity exceeds the platform maximum (0.8). Clamp so touches pass through.
        val maxOpacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.8f else 1f
        val alpha = repository.overlayAlpha.coerceIn(0f, maxOpacity)

        val current = overlayView
        if (current == null || !current.isAttachedToWindow) {
            // Re-create if missing or detached: the system removes the overlay window
            // when the app is swiped from recents, leaving a stale reference behind.
            overlayView = null
            overlayParams = null
            createOverlay()
        }
        val view = overlayView ?: return
        val params = overlayParams ?: return
        try {
            params.alpha = alpha
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createOverlay() {
        val wm = windowManager ?: return

        val view = View(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        // TYPE_APPLICATION_OVERLAY (API 26+) is the standard screen-dimmer window for a
        // normal app with the "Display over other apps" permission. FLAG_NOT_TOUCHABLE lets
        // touches pass through; window-level alpha (not view alpha) is used so Android 12+'s
        // maximum-obscuring-opacity rule for touch is respected.
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val (screenW, screenH) = fullScreenSize()

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            screenW,
            screenH,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = 0f // set by updateOverlay() to avoid an initial full-dark flash
            layoutInDisplayCutoutMode = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }

        overlayView = view
        overlayParams = params
        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            overlayView = null
            overlayParams = null
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        overlayView = null
        overlayParams = null
    }

    /** Full physical screen size (includes the navigation bar / gesture area). */
    private fun fullScreenSize(): Pair<Int, Int> {
        val wm = windowManager ?: return 0 to 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    // --- Keep alive ---

    private fun startKeepAlive() {
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
    }

    private fun stopKeepAlive() {
        handler.removeCallbacks(checkRunnable)
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "亮不亮服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台持续维持屏幕亮度与熄屏时长"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val parts = mutableListOf<String>()
        if (repository.isBrightnessEnabled) {
            parts.add("亮度: ${repository.displayBrightnessValue}")
        }
        if (repository.isTimeoutEnabled) {
            val option = TimeoutOption.fromIndex(repository.timeoutIndex)
            parts.add("熄屏: ${option.label}")
        }
        val textContent = parts.joinToString("  |  ")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("亮不亮后台防护中")
            .setContentText(textContent)
            .setSmallIcon(R.drawable.ic_launcher_icon_light)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        // Do NOT remove overlay or restore settings here.
        // When killed by the system, the WindowManager automatically cleans up the overlay;
        // when stopped via ACTION_STOP, cleanup is done explicitly in onStartCommand.
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "liangbuliang_fg_service"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "kg.edu.tin.liangbuliang.action.START"
        const val ACTION_UPDATE_BRIGHTNESS = "kg.edu.tin.liangbuliang.action.UPDATE_BRIGHTNESS"
        const val ACTION_UPDATE_TIMEOUT = "kg.edu.tin.liangbuliang.action.UPDATE_TIMEOUT"
        const val ACTION_STOP = "kg.edu.tin.liangbuliang.action.STOP"
    }
}
