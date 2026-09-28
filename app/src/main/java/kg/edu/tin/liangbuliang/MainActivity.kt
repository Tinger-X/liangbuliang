package kg.edu.tin.liangbuliang

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kg.edu.tin.liangbuliang.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: SettingsRepository
    private var pendingFeature: String? = null

    /** 用户去「安装未知应用」授权，回到应用后继续尚未开始的更新下载。 */
    private var pendingUpdateDownload = false

    /** 已经自动拉起过安装器的安装包，避免每次回到前台都弹一次。 */
    private var autoInstalledFile: File? = null

    /**
     * Android 13+ 才需要运行时申请通知权限。无论是否授予都继续下载：
     * 授权只决定进度通知看不看得见，应用内的进度显示不依赖它。
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startUpdateDownload()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = SettingsRepository(this)

        // 清掉上次遗留的安装包（更新装完或用户放弃后都会留下）。
        UpdateManager.cleanStaleDownloads(this)
        // 检查请求挂在 Activity 的协程上，配置变更会把它取消掉，
        // 这里清掉可能残留的「检查中」状态，否则图标会一直转圈且点不动。
        if (UpdateManager.state.value is UpdateState.Checking) {
            UpdateManager.setState(UpdateState.Idle)
        }
        observeUpdateState()

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
                        },
                        onCheckUpdate = { checkForUpdate() },
                        onStartDownload = { startUpdateWithPermissions() },
                        onCancelDownload = { cancelUpdateDownload() },
                        onInstall = { file -> launchInstaller(file) }
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
        if (pendingUpdateDownload) {
            pendingUpdateDownload = false
            if (UpdateManager.canInstallPackages(this)) {
                startUpdateWithPermissions()
            } else {
                Toast.makeText(this, "未授予[安装未知应用]权限，已取消更新", Toast.LENGTH_LONG).show()
            }
        }
        // Re-establish the keep-alive service (and overlay fallback) after the app was
        // cleared from recents and reopened, or when returning from a permission screen.
        if (repository.isAnyEnabled) {
            startService(Intent(this, LightService::class.java).apply {
                action = LightService.ACTION_START
            })
        }
    }

    // --- App update ---

    /**
     * 下载完成（且通过了包名/版本/签名校验）后立刻拉起系统安装器。
     *
     * 只在应用处于前台时这么做：Android 10+ 不允许后台应用直接启动 Activity，
     * 下载在后台完成时改由通知栏的「点击安装」承担（见 UpdateService）。
     */
    private fun observeUpdateState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                UpdateManager.state.collect { state ->
                    if (state is UpdateState.Ready && state.file != autoInstalledFile) {
                        autoInstalledFile = state.file
                        launchInstaller(state.file)
                    }
                }
            }
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            UpdateManager.setState(UpdateState.Checking)
            when (val result = UpdateChecker.check()) {
                is UpdateChecker.Result.Success -> {
                    val current = BuildConfig.VERSION_NAME
                    UpdateManager.setState(
                        if (AppVersion.isNewer(result.version, current)) {
                            UpdateState.Available(result.version, current)
                        } else {
                            UpdateState.UpToDate(result.version)
                        }
                    )
                }

                is UpdateChecker.Result.Failure ->
                    UpdateManager.setState(UpdateState.Failed(result.message))
            }
        }
    }

    /**
     * 开始下载前的两道权限：先要「安装未知应用」（否则下完了也装不上），
     * 再要通知权限（Android 13+，否则通知栏看不到进度）。
     */
    private fun startUpdateWithPermissions() {
        if (!UpdateManager.canInstallPackages(this)) {
            pendingUpdateDownload = true
            Toast.makeText(this, "请允许[安装未知应用]后继续更新", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startUpdateDownload()
    }

    private fun startUpdateDownload() {
        val version = (UpdateManager.state.value as? UpdateState.Available)?.latestVersion
        if (version.isNullOrBlank()) {
            // 状态已经不在「有新版本」了（例如走了权限流程很久），重新查一次，
            // 避免用一个空的版本号去下载和命名安装包。
            checkForUpdate()
            return
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, UpdateService::class.java).putExtra(UpdateService.EXTRA_VERSION, version)
        )
    }

    private fun cancelUpdateDownload() {
        startService(
            Intent(this, UpdateService::class.java).setAction(UpdateService.ACTION_CANCEL)
        )
    }

    private fun launchInstaller(file: File) {
        if (!file.exists()) {
            UpdateManager.setState(UpdateState.Idle)
            Toast.makeText(this, "安装包已失效，请重新下载", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(UpdateManager.installIntent(this, file))
        } catch (e: Exception) {
            Toast.makeText(this, "无法启动安装器：${e.message}", Toast.LENGTH_LONG).show()
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
    onTimeoutChange: (Int) -> Unit,
    onCheckUpdate: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (File) -> Unit
) {
    var isBrightnessEnabled by remember { mutableStateOf(repository.isBrightnessEnabled) }
    var isTimeoutEnabled by remember { mutableStateOf(repository.isTimeoutEnabled) }
    var sliderPosition by remember {
        mutableFloatStateOf(
            SettingsRepository.brightnessValueToSliderPosition(repository.brightnessValue)
        )
    }
    var selectedTimeoutIndex by remember { mutableIntStateOf(repository.timeoutIndex) }

    val context = LocalContext.current
    val updateState by UpdateManager.state.collectAsState()
    // 点过「后台下载」后隐藏进度对话框；新一轮下载开始时再自动打开。
    var progressDialogVisible by remember { mutableStateOf(true) }

    LaunchedEffect(updateState) {
        when (val current = updateState) {
            is UpdateState.Downloading ->
                if (current.bytesRead == 0L) progressDialogVisible = true

            // 一次性结果用 Toast 提示，随后把状态复位，避免回到前台时重复弹出。
            is UpdateState.UpToDate -> {
                Toast
                    .makeText(context, "已是最新版本 ${current.version}", Toast.LENGTH_SHORT)
                    .show()
                UpdateManager.setState(UpdateState.Idle)
            }

            is UpdateState.Failed -> {
                Toast.makeText(context, current.message, Toast.LENGTH_LONG).show()
                UpdateManager.setState(UpdateState.Idle)
            }

            else -> Unit
        }
    }

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
            // ====== 右上角：检查更新 ======
            UpdateCheckButton(
                state = updateState,
                onClick = {
                    when (val current = updateState) {
                        // 检查中不重复发起；下载中再点一次则是重新打开进度对话框。
                        is UpdateState.Checking -> Unit
                        is UpdateState.Downloading -> progressDialogVisible = true
                        is UpdateState.Ready -> onInstall(current.file)
                        else -> onCheckUpdate()
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd)
            )

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

            UpdateDialogs(
                state = updateState,
                progressVisible = progressDialogVisible,
                onDownload = onStartDownload,
                onCancelDownload = onCancelDownload,
                onHideProgress = { progressDialogVisible = false },
                // 「稍后」即放弃本次提示；下次点检查更新会重新查询。
                onDismiss = { UpdateManager.setState(UpdateState.Idle) }
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
