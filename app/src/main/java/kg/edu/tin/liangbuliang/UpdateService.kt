package kg.edu.tin.liangbuliang

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 下载新版本安装包的前台服务。
 *
 * 用前台服务而不是 Activity 内的协程，是为了让下载在用户离开应用后继续进行：
 * 进度既通过通知栏展示，也同步到 [UpdateManager.state] 供应用内显示。
 */
class UpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                // 运行中的下载由协程自己收尾（删掉 .part 并把状态置回 Idle），避免竞态。
                if (downloadJob?.isActive == true) downloadJob?.cancel()
                else UpdateManager.setState(UpdateState.Idle)
                stopSelf()
            }
            else -> {
                // 重复的启动请求（用户连点两次）用当前进度重新进入前台，然后忽略这次。
                if (downloadJob?.isActive == true) {
                    val current = UpdateManager.state.value as? UpdateState.Downloading
                    startForeground(
                        NOTIFICATION_ID_PROGRESS,
                        progressNotification(
                            current?.version.orEmpty(),
                            current?.bytesRead ?: 0,
                            current?.totalBytes ?: 0,
                            current?.bytesPerSecond ?: 0
                        )
                    )
                    return START_NOT_STICKY
                }
                val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty()
                UpdateManager.setState(UpdateState.Downloading(version, 0, 0, 0))
                startForeground(
                    NOTIFICATION_ID_PROGRESS,
                    progressNotification(version, 0, 0, 0)
                )
                downloadJob = scope.launch { download(version) }
            }
        }
        // 本服务只为一次下载而活：进程被杀后重来一遍没有意义，也不该被系统重启。
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- Download ---

    private suspend fun download(version: String) = withContext(Dispatchers.IO) {
        val target = UpdateManager.apkFile(this@UpdateService, version)
        // 先写 .part 再改名：中途失败或被杀不会留下一个看起来完整的安装包。
        val partial = File(target.parentFile, target.name + ".part")

        try {
            partial.delete()
            fetch(version, partial)
            if (!partial.renameTo(target)) throw IOException("无法写入安装包")
        } catch (e: CancellationException) {
            partial.delete()
            UpdateManager.setState(UpdateState.Idle)
            throw e
        } catch (e: Exception) {
            partial.delete()
            fail(e.message ?: "下载失败")
            return@withContext
        }

        val error = UpdateManager.verifyApk(this@UpdateService, target)
        if (error != null) {
            target.delete()
            fail(error)
            return@withContext
        }
        complete(target, version)
    }

    private suspend fun fetch(version: String, partial: File) {
        val request = Request.Builder()
            .url(UpdateManager.downloadUrl)
            .header("User-Agent", "LiangBuLiang/" + BuildConfig.VERSION_NAME)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("下载失败（HTTP ${response.code}）")
            val body = response.body ?: throw IOException("下载失败：响应为空")
            val total = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    var windowBytes = 0L
                    var windowStart = SystemClock.elapsedRealtime()
                    // 服务端未给出总长度时按不确定进度展示。
                    report(version, downloaded, total, 0)

                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        windowBytes += read

                        val now = SystemClock.elapsedRealtime()
                        val elapsed = now - windowStart
                        if (elapsed >= PROGRESS_INTERVAL_MS) {
                            report(version, downloaded, total, windowBytes * 1000 / elapsed)
                            windowStart = now
                            windowBytes = 0
                        }
                    }
                    report(version, downloaded, total, 0)
                }
            }

            if (total > 0 && partial.length() != total) {
                throw IOException("下载不完整，请重试")
            }
        }
    }

    private fun report(version: String, downloaded: Long, total: Long, speed: Long) {
        UpdateManager.setState(UpdateState.Downloading(version, downloaded, total, speed))
        notificationManager().notify(
            NOTIFICATION_ID_PROGRESS,
            progressNotification(version, downloaded, total, speed)
        )
    }

    private fun complete(file: File, version: String) {
        UpdateManager.setState(UpdateState.Ready(file, version))
        // 应用在后台时不允许直接拉起 Activity（Android 10+ 的后台启动限制），
        // 因此这里发一条可点击的安装通知；应用在前台时由 MainActivity 立即拉起安装器。
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager().notify(NOTIFICATION_ID_READY, readyNotification(file, version))
        stopSelf()
    }

    private fun fail(message: String) {
        UpdateManager.setState(UpdateState.Failed(message))
        // 失败提示走静默渠道：应用在前台时对话框已经说明了原因，不必再响一声。
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager().notify(NOTIFICATION_ID_PROGRESS, failedNotification(message))
        stopSelf()
    }

    // --- Notification ---

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "更新下载进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示新版本的下载进度"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_READY,
                "更新下载完成",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "新版本下载完成后提醒安装"
            }
        )
    }

    private fun progressNotification(
        version: String,
        downloaded: Long,
        total: Long,
        speed: Long
    ): Notification {
        val indeterminate = total <= 0
        val percent = if (indeterminate) 0 else (downloaded * 100 / total).toInt()
        val text = buildString {
            if (indeterminate) {
                append(formatBytes(downloaded))
            } else {
                append(formatBytes(downloaded))
                append(" / ")
                append(formatBytes(total))
                append("（").append(percent).append("%）")
            }
            if (speed > 0) append(" · ").append(formatBytes(speed)).append("/s")
        }

        val cancelIntent = PendingIntent.getService(
            this,
            REQUEST_CANCEL,
            Intent(this, UpdateService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setContentTitle("正在下载 $version")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_icon_light)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, percent, indeterminate)
            .addAction(0, "取消", cancelIntent)
            .build()
    }

    private fun readyNotification(file: File, version: String): Notification {
        val installIntent = PendingIntent.getActivity(
            this,
            REQUEST_INSTALL,
            UpdateManager.installIntent(this, file),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_READY)
            .setContentTitle("新版本 $version 已下载完成")
            .setContentText("点击安装")
            .setSmallIcon(R.drawable.ic_launcher_icon_light)
            .setContentIntent(installIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun failedNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setContentTitle("更新下载失败")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_icon_light)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_VERSION = "kg.edu.tin.liangbuliang.extra.UPDATE_VERSION"
        const val ACTION_CANCEL = "kg.edu.tin.liangbuliang.action.CANCEL_UPDATE_DOWNLOAD"

        private const val CHANNEL_PROGRESS = "liangbuliang_update_progress"
        private const val CHANNEL_READY = "liangbuliang_update_ready"

        private const val NOTIFICATION_ID_PROGRESS = 2001
        private const val NOTIFICATION_ID_READY = 2002

        private const val REQUEST_CANCEL = 2001
        private const val REQUEST_INSTALL = 2002
        private const val REQUEST_OPEN_APP = 2003

        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 400L
    }
}
