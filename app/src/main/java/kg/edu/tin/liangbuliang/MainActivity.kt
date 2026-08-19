package kg.edu.tin.liangbuliang

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kg.edu.tin.liangbuliang.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: SettingsRepository
    private var pendingFeature: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = SettingsRepository(this)

        setContent {
            MyApplicationTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(800)
                    showSplash = false
                }
                if (showSplash) {
                    Image(
                        painter = painterResource(id = R.drawable.splash_bg),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    MainScreen(
                        repository = repository,
                        onBrightnessToggle = { enable ->
                            handleBrightnessToggle(enable)
                        },
                        onTimeoutToggle = { enable ->
                            handleTimeoutToggle(enable)
                        },
                        onBrightnessChange = { sliderPos ->
                            handleBrightnessChange(sliderPos)
                        },
                        onTimeoutChange = { index ->
                            handleTimeoutChange(index)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        when (pendingFeature) {
            "brightness" -> continueBrightnessEnable()
            "timeout" -> continueTimeoutEnable()
        }
        // Re-establish the keep-alive service (and overlay fallback) after the app was
        // cleared from recents and reopened, or when returning from a permission screen.
        if (repository.isAnyEnabled) {
            startService(Intent(this, LightService::class.java).apply {
                action = LightService.ACTION_START
            })
        }
    }

    // --- Permission helpers ---

    private fun requestWriteSettingsPermission(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun requestOverlayPermission() {
        Toast.makeText(
            this,
            "需要[显示在其他应用上层]权限以在极暗模式下调节亮度",
            Toast.LENGTH_LONG
        ).show()
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    /**
     * Enable brightness, requesting any missing permissions in order. Re-invoked from
     * onResume() after each permission grant until everything needed is available.
     */
    private fun continueBrightnessEnable() {
        if (!Settings.System.canWrite(this)) {
            requestWriteSettingsPermission("需要[修改系统设置]权限以调节屏幕亮度")
            return
        }
        // The overlay is only used when Extra Dim (persistent) is unavailable.
        if (!repository.belowMinimumUsesExtraDim && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        pendingFeature = null
        doEnableBrightness()
    }

    // --- Brightness ---

    private fun handleBrightnessToggle(enable: Boolean) {
        if (enable) {
            pendingFeature = "brightness"
            continueBrightnessEnable()
        } else {
            pendingFeature = null
            repository.lastBrightnessValue = repository.brightnessValue
            repository.isBrightnessEnabled = false
            resetWindowBrightness()
            if (repository.isAnyEnabled) {
                startService(Intent(this, LightService::class.java).apply {
                    action = LightService.ACTION_UPDATE_BRIGHTNESS
                })
            } else {
                startService(Intent(this, LightService::class.java).apply {
                    action = LightService.ACTION_STOP
                })
            }
        }
    }

    private fun doEnableBrightness() {
        repository.isBrightnessEnabled = true
        // Reset originals so saveOriginalBrightnessSettingsIfNeeded() captures fresh system state
        repository.originalBrightness = -1
        repository.originalBrightnessMode = -1
        repository.originalExtraDimActivated = -1
        repository.originalExtraDimLevel = -1
        repository.saveOriginalBrightnessSettingsIfNeeded()
        repository.initializeBrightnessFromSystem()
        repository.applyBrightnessToSystem()

        val serviceIntent = Intent(this, LightService::class.java).apply {
            action = LightService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun handleBrightnessChange(sliderPosition: Float) {
        val value = SettingsRepository.sliderPositionToBrightnessValue(sliderPosition)
        repository.brightnessValue = value
        if (repository.isBrightnessEnabled) {
            repository.applyBrightnessToSystem()
            startService(Intent(this, LightService::class.java).apply {
                action = LightService.ACTION_UPDATE_BRIGHTNESS
            })
        }
    }

    // --- Timeout ---

    private fun handleTimeoutToggle(enable: Boolean) {
        if (enable) {
            pendingFeature = "timeout"
            continueTimeoutEnable()
        } else {
            pendingFeature = null
            repository.lastTimeoutIndex = repository.timeoutIndex
            repository.isTimeoutEnabled = false
            if (repository.isAnyEnabled) {
                startService(Intent(this, LightService::class.java).apply {
                    action = LightService.ACTION_UPDATE_TIMEOUT
                })
            } else {
                startService(Intent(this, LightService::class.java).apply {
                    action = LightService.ACTION_STOP
                })
            }
        }
    }

    private fun continueTimeoutEnable() {
        if (!Settings.System.canWrite(this)) {
            requestWriteSettingsPermission("需要[修改系统设置]权限以调节熄屏时长")
            return
        }
        pendingFeature = null
        doEnableTimeout()
    }

    private fun doEnableTimeout() {
        repository.isTimeoutEnabled = true
        // Reset originals so saveOriginalTimeoutSettingsIfNeeded() captures fresh system state
        repository.originalTimeout = -1
        repository.saveOriginalTimeoutSettingsIfNeeded()
        repository.initializeTimeoutFromSystem()
        repository.applyTimeoutToSystem()

        val serviceIntent = Intent(this, LightService::class.java).apply {
            action = LightService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun handleTimeoutChange(index: Int) {
        repository.timeoutIndex = index
        if (repository.isTimeoutEnabled) {
            repository.applyTimeoutToSystem()
            startService(Intent(this, LightService::class.java).apply {
                action = LightService.ACTION_UPDATE_TIMEOUT
            })
        }
    }

    // --- Window brightness helper ---

    private fun resetWindowBrightness() {
        try {
            val lp = window.attributes
            lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: SettingsRepository,
    onBrightnessToggle: (Boolean) -> Unit,
    onTimeoutToggle: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onTimeoutChange: (Int) -> Unit
) {
    var isBrightnessEnabled by remember { mutableStateOf(repository.isBrightnessEnabled) }
    var isTimeoutEnabled by remember { mutableStateOf(repository.isTimeoutEnabled) }
    var sliderPosition by remember {
        mutableFloatStateOf(
            SettingsRepository.brightnessValueToSliderPosition(repository.brightnessValue)
        )
    }
    var selectedTimeoutIndex by remember { mutableIntStateOf(repository.timeoutIndex) }

    // Derive the display brightness value reactively from slider position
    val displayBrightnessValue by remember {
        derivedStateOf {
            val value = SettingsRepository.sliderPositionToBrightnessValue(sliderPosition)
            SettingsRepository.formatBrightnessValue(value)
        }
    }

    // Sync from repository when returning to screen
    LaunchedEffect(Unit) {
        isBrightnessEnabled = repository.isBrightnessEnabled
        isTimeoutEnabled = repository.isTimeoutEnabled
        sliderPosition =
            SettingsRepository.brightnessValueToSliderPosition(repository.brightnessValue)
        selectedTimeoutIndex = repository.timeoutIndex
    }

    val onBg = MaterialTheme.colorScheme.onBackground
    val surfaceColor = MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // ====== App Title ======
                    Text(
                        text = "亮不亮",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        ),
                        color = onBg,
                        modifier = Modifier
                            .testTag("app_title")
                            .padding(bottom = 28.dp)
                    )

                    // ====== 屏幕亮度 Card ======
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = surfaceColor)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Brightness7,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "屏幕亮度",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = onBg,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (isBrightnessEnabled) displayBrightnessValue
                                    else "关闭",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = if (isBrightnessEnabled) MaterialTheme.colorScheme.primary
                                    else onBg.copy(alpha = 0.35f),
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Switch(
                                    checked = isBrightnessEnabled,
                                    onCheckedChange = { checked ->
                                        isBrightnessEnabled = checked
                                        onBrightnessToggle(checked)
                                        // Refresh slider after enable
                                        if (checked) {
                                            sliderPosition = SettingsRepository
                                                .brightnessValueToSliderPosition(
                                                    repository.brightnessValue
                                                )
                                        }
                                    },
                                    modifier = Modifier.testTag("brightness_toggle"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = onBg.copy(alpha = 0.45f),
                                        uncheckedTrackColor = onBg.copy(alpha = 0.12f)
                                    )
                                )
                            }

                            AnimatedVisibility(
                                visible = isBrightnessEnabled,
                                enter = fadeIn(animationSpec = spring()),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                val sliderHeight = 30.dp
                                val thumbRadius = sliderHeight / 2
                                Column(modifier = Modifier.padding(top = 20.dp)) {
                                    Slider(
                                        value = sliderPosition,
                                        onValueChange = { newPos ->
                                            sliderPosition = newPos
                                            onBrightnessChange(newPos)
                                        },
                                        valueRange = 0f..1f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("brightness_slider"),
                                        track = { sliderState ->
                                            val fraction = sliderState.value
                                            val extraPx = with(LocalDensity.current) { thumbRadius.toPx().toInt() }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .layout { measurable, constraints ->
                                                        val totalWidth = constraints.maxWidth + extraPx * 2
                                                        val placeable = measurable.measure(
                                                            constraints.copy(maxWidth = totalWidth)
                                                        )
                                                        layout(placeable.width, placeable.height) {
                                                            placeable.placeRelative(0, 0)
                                                        }
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(sliderHeight)
                                                        .clip(RoundedCornerShape(thumbRadius))
                                                ) {
                                                    // Inactive track (full width)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(onBg.copy(alpha = 0.08f))
                                                    )
                                                    // Active track (ends at thumb center, always covered by thumb)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .layout { measurable, constraints ->
                                                                val totalW = constraints.maxWidth.toFloat()
                                                                val originalW = totalW - extraPx * 2
                                                                val targetWidth = (extraPx + originalW * fraction)
                                                                    .coerceIn(0f, totalW)
                                                                    .toInt()
                                                                val placeable = measurable.measure(
                                                                    constraints.copy(
                                                                        minWidth = targetWidth,
                                                                        maxWidth = targetWidth
                                                                    )
                                                                )
                                                                layout(targetWidth, placeable.height) {
                                                                    placeable.placeRelative(0, 0)
                                                                }
                                                            }
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                            }
                                        },
                                        thumb = {
                                            CircleWithBorder(
                                                diameter = sliderHeight,
                                                fillColor = MaterialTheme.colorScheme.surface,
                                                borderColor = MaterialTheme.colorScheme.primary,
                                                borderWidth = 5.dp
                                            )
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.Transparent,
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent,
                                            activeTickColor = Color.Transparent,
                                            inactiveTickColor = Color.Transparent
                                        )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "0.1%",
                                            fontSize = 11.sp,
                                            color = onBg.copy(alpha = 0.35f)
                                        )
                                        Text(
                                            text = "10%",
                                            fontSize = 11.sp,
                                            color = onBg.copy(alpha = 0.35f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ====== 熄屏时长 Card ======
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = surfaceColor)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "熄屏时长",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = onBg,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (isTimeoutEnabled) TimeoutOption.fromIndex(
                                        selectedTimeoutIndex
                                    ).label else "关闭",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = if (isTimeoutEnabled) MaterialTheme.colorScheme.primary
                                    else onBg.copy(alpha = 0.35f),
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Switch(
                                    checked = isTimeoutEnabled,
                                    onCheckedChange = { checked ->
                                        isTimeoutEnabled = checked
                                        onTimeoutToggle(checked)
                                        // Refresh slider after enable (first-time init picks the
                                        // closest option to the system timeout).
                                        if (checked) {
                                            selectedTimeoutIndex = repository.timeoutIndex
                                        }
                                    },
                                    modifier = Modifier.testTag("timeout_toggle"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = onBg.copy(alpha = 0.45f),
                                        uncheckedTrackColor = onBg.copy(alpha = 0.12f)
                                    )
                                )
                            }

                            AnimatedVisibility(
                                visible = isTimeoutEnabled,
                                enter = fadeIn(animationSpec = spring()),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                val sliderHeight = 30.dp
                                val thumbRadius = sliderHeight / 2
                                Column(modifier = Modifier.padding(top = 20.dp)) {
                                    Slider(
                                        value = selectedTimeoutIndex.toFloat(),
                                        onValueChange = { newValue ->
                                            val index = newValue
                                                .roundToInt()
                                                .coerceIn(0, TimeoutOption.entries.lastIndex)
                                            selectedTimeoutIndex = index
                                            onTimeoutChange(index)
                                        },
                                        valueRange = 0f..TimeoutOption.entries.lastIndex.toFloat(),
                                        steps = TimeoutOption.entries.size - 2,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("timeout_slider"),
                                        track = { sliderState ->
                                            val fraction =
                                                (sliderState.value - sliderState.valueRange.start) /
                                                    (sliderState.valueRange.endInclusive -
                                                        sliderState.valueRange.start)
                                            val extraPx =
                                                with(LocalDensity.current) { thumbRadius.toPx().toInt() }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .layout { measurable, constraints ->
                                                        val totalWidth = constraints.maxWidth + extraPx * 2
                                                        val placeable = measurable.measure(
                                                            constraints.copy(maxWidth = totalWidth)
                                                        )
                                                        layout(placeable.width, placeable.height) {
                                                            placeable.placeRelative(0, 0)
                                                        }
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(sliderHeight)
                                                        .clip(RoundedCornerShape(thumbRadius))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(onBg.copy(alpha = 0.08f))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .layout { measurable, constraints ->
                                                                val totalW = constraints.maxWidth.toFloat()
                                                                val originalW = totalW - extraPx * 2
                                                                val targetWidth = (extraPx + originalW * fraction)
                                                                    .coerceIn(0f, totalW)
                                                                    .toInt()
                                                                val placeable = measurable.measure(
                                                                    constraints.copy(
                                                                        minWidth = targetWidth,
                                                                        maxWidth = targetWidth
                                                                    )
                                                                )
                                                                layout(targetWidth, placeable.height) {
                                                                    placeable.placeRelative(0, 0)
                                                                }
                                                            }
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                            }
                                        },
                                        thumb = {
                                            CircleWithBorder(
                                                diameter = sliderHeight,
                                                fillColor = MaterialTheme.colorScheme.surface,
                                                borderColor = MaterialTheme.colorScheme.primary,
                                                borderWidth = 5.dp
                                            )
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.Transparent,
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent,
                                            activeTickColor = Color.Transparent,
                                            inactiveTickColor = Color.Transparent
                                        )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = TimeoutOption.entries.first().label,
                                            fontSize = 11.sp,
                                            color = onBg.copy(alpha = 0.35f)
                                        )
                                        Text(
                                            text = TimeoutOption.entries.last().label,
                                            fontSize = 11.sp,
                                            color = onBg.copy(alpha = 0.35f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Footer
            Text(
                text = buildFooterText(BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = onBg.copy(alpha = 0.35f)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .testTag("footer_info")
            )
        }
    }
}

/**
 * A circle with a border, drawn inward so the outer diameter equals [diameter].
 * Used as the slider thumb indicator.
 */
@Composable
private fun CircleWithBorder(
    diameter: Dp,
    fillColor: Color,
    borderColor: Color,
    borderWidth: Dp
) {
    Canvas(modifier = Modifier.size(diameter)) {
        val outerRadius = size.minDimension / 2f
        val innerRadius = outerRadius - borderWidth.toPx()

        // Outer circle (border color)
        drawCircle(color = borderColor, radius = outerRadius)
        // Inner circle (fill color)
        drawCircle(color = fillColor, radius = innerRadius)
    }
}

private fun buildFooterText(versionName: String): String {
    val currentYearShort = java.util.Calendar.getInstance()[java.util.Calendar.YEAR] % 100
    return "Copyright \u00A9 26-${currentYearShort} Tinger, $versionName"
}
