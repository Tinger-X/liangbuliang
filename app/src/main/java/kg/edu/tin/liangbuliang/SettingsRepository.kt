package kg.edu.tin.liangbuliang

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlin.math.roundToInt

class SettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("liangbuliang_prefs", Context.MODE_PRIVATE)

    // --- Feature toggles ---

    var isBrightnessEnabled: Boolean
        get() = prefs.getBoolean("is_brightness_enabled", false)
        set(value) = prefs.edit().putBoolean("is_brightness_enabled", value).apply()

    var isTimeoutEnabled: Boolean
        get() = prefs.getBoolean("is_timeout_enabled", false)
        set(value) = prefs.edit().putBoolean("is_timeout_enabled", value).apply()

    // --- Brightness value (0.1f = dimmest, 10.0f = brightest) ---

    var brightnessValue: Float
        get() = if (prefs.contains("brightness_value_float")) {
            prefs.getFloat("brightness_value_float", 5.0f).coerceIn(0.1f, 10.0f)
        } else {
            // Migrate from the legacy int key (tenths of a brightness value: 1 = 0.1, 100 = 10.0).
            (prefs.getInt("brightness_value", 50) / 10f).coerceIn(0.1f, 10.0f) // default 5.0
        }
        set(value) = prefs.edit()
            .putFloat("brightness_value_float", value.coerceIn(0.1f, 10.0f))
            .apply()

    /** Last brightness value before disabling (restored on re-enable). -1 means not set. */
    var lastBrightnessValue: Float
        get() = if (prefs.contains("last_brightness_value_float")) {
            prefs.getFloat("last_brightness_value_float", -1f)
        } else {
            // Migrate from the legacy int key (tenths of a brightness value).
            val legacy = prefs.getInt("last_brightness_value", -1)
            if (legacy == -1) -1f else (legacy / 10f).coerceIn(0.1f, 10.0f)
        }
        set(value) = prefs.edit().putFloat("last_brightness_value_float", value).apply()

    val isAnyEnabled: Boolean get() = isBrightnessEnabled || isTimeoutEnabled

    // --- Derived brightness properties ---

    /** Actual brightness value (0.1f .. 10.0f). */
    val actualBrightnessValue: Float
        get() = brightnessValue

    /** Display string: "5.0%", "0.9%", etc. */
    val displayBrightnessValue: String
        get() = formatBrightnessValue(actualBrightnessValue)

    /**
     * Overlay alpha for sub-system-brightness effect (fallback when Extra Dim is unavailable).
     * Linearly maps brightness value 0.1-1.0 → 0.98-0.0:
     * - 0.1 → 0.98 (98% opaque, 2% light passes — near-total darkness)
     * - 1.0 → 0.00 (fully transparent, system minimum takes over)
     * The service clamps this further to the platform's maximum obscuring opacity for touch.
     */
    val overlayAlpha: Float
        get() {
            if (!isBrightnessEnabled || brightnessValue >= 1.0f) return 0f
            // Linear: alpha = maxAlpha * (1 - normalized)
            return (0.98f * (1f - belowMinimumNormalized())).coerceIn(0f, 0.98f)
        }

    /**
     * Android 12+ "Extra Dim" (reduce bright colors) is the only way to dim below the
     * hardware minimum backlight while persisting across app exit/clean — it lives in
     * Settings.Secure, so it requires WRITE_SECURE_SETTINGS (granted via adb / Shizuku).
     */
    val isExtraDimAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Prefer the persistent Extra Dim over the runtime overlay when it is available. */
    val belowMinimumUsesExtraDim: Boolean
        get() = isExtraDimAvailable

    /**
     * Extra Dim intensity for the below-minimum range.
     * 0.1 → 1.0 (maximum dimming), 1.0 → 0.0 (no extra dim).
     */
    val extraDimLevel: Float
        get() {
            if (!isBrightnessEnabled) return 0f
            return extraDimLevelFor(brightnessValue)
        }

    /** Normalized position within the below-minimum band: 0.1 → 0.0, 1.0 → 1.0. */
    private fun belowMinimumNormalized(): Float {
        val value = actualBrightnessValue.coerceIn(0.1f, 1.0f)
        return (value - 0.1f) / 0.9f
    }

    // --- Slider position <-> brightness value conversion ---

    companion object {
        // Android 12+ "Extra Dim" (reduce bright colors) — @hide Settings.Secure keys.
        private const val KEY_EXTRA_DIM_ACTIVATED = "reduce_bright_colors_activated"
        private const val KEY_EXTRA_DIM_LEVEL = "reduce_bright_colors_level"

        /** Non-linear slider position (0.0..1.0) → brightness value (0.1..10.0). */
        fun sliderPositionToBrightnessValue(position: Float): Float {
            val clamped = position.coerceIn(0f, 1f)
            return if (clamped <= 0.5f) {
                // Left half: 0.1 → 1.0
                0.1f + (clamped / 0.5f) * 0.9f
            } else {
                // Right half: 1.0 → 10.0
                1.0f + ((clamped - 0.5f) / 0.5f) * 9.0f
            }
        }

        /** brightness value (0.1..10.0) → non-linear slider position (0.0..1.0). */
        fun brightnessValueToSliderPosition(value: Float): Float {
            val clamped = value.coerceIn(0.1f, 10f)
            return if (clamped <= 1.0f) {
                // 0.1 → 1.0  maps to 0.0 → 0.5
                0.5f * (clamped - 0.1f) / 0.9f
            } else {
                // 1.0 → 10.0  maps to 0.5 → 1.0
                0.5f + 0.5f * (clamped - 1.0f) / 9.0f
            }
        }

        /**
         * Extra Dim intensity for a brightness value (0.1..10.0): 0.1 → 1.0, 1.0 → 0.0.
         * Returns 0 for values at/above 1.0.
         */
        fun extraDimLevelFor(value: Float): Float {
            if (value >= 1.0f) return 0f
            val clamped = value.coerceIn(0.1f, 1.0f)
            val normalized = (clamped - 0.1f) / 0.9f
            return (1f - normalized).coerceIn(0f, 1f)
        }

        /**
         * Format a brightness value for display.
         *
         * Values at/above 1 show as a whole number ("1%", "5%", "10%"). Values below 1
         * are floored to the nearest 0.1 so that, e.g., 0.99 shows as "0.9%" instead of
         * rounding up to "1.0%" — which would read as a second, darker "1%". Only the
         * displayed text is floored; the underlying value is left untouched.
         */
        fun formatBrightnessValue(value: Float): String {
            if (value >= 1f) return "%.0f%%".format(value)
            // Floor to the nearest 0.1. The tiny epsilon absorbs float representation error
            // (e.g. 0.9f * 10f == 8.9999997f) without pushing a value like 0.99
            // across the 1.0 boundary.
            val tenths = (value * 10f + 1e-5f).toInt()
            return "%.1f%%".format(tenths / 10f)
        }

        /**
         * Map app brightness value (0.1..10.0) to system SCREEN_BRIGHTNESS.
         * Field testing shows SCREEN_BRIGHTNESS maps 1:1 to the system brightness value
         */
        fun brightnessToSystemValue(value: Float): Int {
            if (value < 1.0f) return 1
            return value.roundToInt().coerceIn(1, 255)
        }
    }

    // --- Timeout ---

    var timeoutIndex: Int
        get() = prefs.getInt("timeout_index", TimeoutOption.entries.lastIndex)
            .coerceIn(0, TimeoutOption.entries.lastIndex)
        set(value) = prefs.edit().putInt(
            "timeout_index", value.coerceIn(0, TimeoutOption.entries.lastIndex)
        ).apply()

    /** Last timeout index before disabling (restored on re-enable). -1 means not set. */
    var lastTimeoutIndex: Int
        get() = prefs.getInt("last_timeout_index", -1)
        set(value) = prefs.edit().putInt("last_timeout_index", value).apply()

    // --- Original system settings (backup) ---

    var originalBrightness: Int
        get() = prefs.getInt("original_brightness", -1)
        set(value) = prefs.edit().putInt("original_brightness", value).apply()

    var originalBrightnessMode: Int
        get() = prefs.getInt("original_brightness_mode", -1)
        set(value) = prefs.edit().putInt("original_brightness_mode", value).apply()

    var originalExtraDimActivated: Int
        get() = prefs.getInt("original_extra_dim_activated", -1)
        set(value) = prefs.edit().putInt("original_extra_dim_activated", value).apply()

    var originalExtraDimLevel: Int
        get() = prefs.getInt("original_extra_dim_level", -1)
        set(value) = prefs.edit().putInt("original_extra_dim_level", value).apply()

    var originalTimeout: Int
        get() = prefs.getInt("original_timeout", -1)
        set(value) = prefs.edit().putInt("original_timeout", value).apply()

    // --- Save / Restore ---

    fun saveOriginalBrightnessSettingsIfNeeded() {
        val cr = context.contentResolver
        if (originalBrightness == -1) {
            try {
                originalBrightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) {
                originalBrightness = 128
            }
        }
        if (originalBrightnessMode == -1) {
            try {
                originalBrightnessMode = Settings.System.getInt(
                    cr, Settings.System.SCREEN_BRIGHTNESS_MODE
                )
            } catch (e: Exception) {
                originalBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
        }
        if (isExtraDimAvailable) {
            if (originalExtraDimActivated == -1) {
                try {
                    originalExtraDimActivated = Settings.Secure.getInt(cr, KEY_EXTRA_DIM_ACTIVATED)
                } catch (e: Exception) {
                    originalExtraDimActivated = 0
                }
            }
            if (originalExtraDimLevel == -1) {
                try {
                    originalExtraDimLevel = Settings.Secure.getInt(cr, KEY_EXTRA_DIM_LEVEL)
                } catch (e: Exception) {
                    originalExtraDimLevel = 50
                }
            }
        }
    }

    fun saveOriginalTimeoutSettingsIfNeeded() {
        val cr = context.contentResolver
        if (originalTimeout == -1) {
            try {
                originalTimeout = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT)
            } catch (e: Exception) {
                originalTimeout = 60000
            }
        }
    }

    /**
     * Initialize brightnessValue from current system brightness.
     * Only called on first-time enable (when lastBrightnessValue == -1).
     */
    fun initializeBrightnessFromSystem() {
        if (lastBrightnessValue != -1f) {
            brightnessValue = lastBrightnessValue
            return
        }
        val cr = context.contentResolver
        val systemValue: Int = try {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            50
        }
        // SCREEN_BRIGHTNESS maps 1:1 to the system brightness value on the target
        val systemBrightnessValue = systemValue.toFloat()
        // First enable: >10 -> cap at 10, 1~10 -> keep system, <1 -> floor at 0.1.
        brightnessValue = systemBrightnessValue.coerceIn(0.1f, 10f)
    }

    /**
     * Initialize timeoutIndex from the current system screen-off timeout.
     * Only called on first-time enable (when lastTimeoutIndex == -1).
     */
    fun initializeTimeoutFromSystem() {
        if (lastTimeoutIndex != -1) {
            timeoutIndex = lastTimeoutIndex
            return
        }
        val cr = context.contentResolver
        val systemTimeout: Int = try {
            Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (e: Exception) {
            60_000
        }
        timeoutIndex = TimeoutOption.closestIndex(systemTimeout)
    }

    fun restoreBrightnessSettings() {
        if (!Settings.System.canWrite(context)) return
        val cr = context.contentResolver
        try {
            if (originalBrightness != -1) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, originalBrightness)
            }
            if (originalBrightnessMode != -1) {
                Settings.System.putInt(
                    cr, Settings.System.SCREEN_BRIGHTNESS_MODE, originalBrightnessMode
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (isExtraDimAvailable) {
            try {
                if (originalExtraDimActivated != -1) {
                    Settings.Secure.putInt(cr, KEY_EXTRA_DIM_ACTIVATED, originalExtraDimActivated)
                }
                if (originalExtraDimLevel != -1) {
                    Settings.Secure.putInt(cr, KEY_EXTRA_DIM_LEVEL, originalExtraDimLevel)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun restoreTimeoutSettings() {
        if (!Settings.System.canWrite(context)) return
        val cr = context.contentResolver
        try {
            if (originalTimeout != -1) {
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, originalTimeout)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreOriginalSystemSettings() {
        // No-op: each feature restores its own settings independently.
        // This method is kept to avoid breaking compilation during migration.
    }

    // --- Apply settings ---

    fun applyBrightnessToSystem() {
        if (!Settings.System.canWrite(context)) return
        val cr = context.contentResolver
        try {
            Settings.System.putInt(
                cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            val value = actualBrightnessValue
            val brightness = brightnessToSystemValue(value)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, brightness)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        applyExtraDimIfNeeded()
    }

    /**
     * Drive the system "Extra Dim" (reduce bright colors) feature.
     *
     * Extra Dim is activated for the whole time brightness is enabled — not just below 1.0 —
     * and only its level changes while dragging (0 at/above 1.0, ramping to 100 at 0.1).
     * This moves the on/off step to enable/disable time, so crossing the 1.0 boundary no
     * longer jumps. Only active when WRITE_SECURE_SETTINGS is granted; the overlay is the
     * fallback.
     */
    private fun applyExtraDimIfNeeded() {
        if (!isExtraDimAvailable) return
        val cr = context.contentResolver
        try {
            // Write the level before the activation flag so there's no flash at a stale level
            // when Extra Dim first turns on.
            val level = (extraDimLevel * 100f).roundToInt().coerceIn(0, 100)
            Settings.Secure.putInt(cr, KEY_EXTRA_DIM_LEVEL, level)
            Settings.Secure.putInt(cr, KEY_EXTRA_DIM_ACTIVATED, if (isBrightnessEnabled) 1 else 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyTimeoutToSystem() {
        if (!Settings.System.canWrite(context)) return
        val cr = context.contentResolver
        try {
            val selectedOption = TimeoutOption.fromIndex(timeoutIndex)
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, selectedOption.timeoutMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
