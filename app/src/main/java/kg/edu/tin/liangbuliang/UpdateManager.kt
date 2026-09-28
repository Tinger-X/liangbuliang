package kg.edu.tin.liangbuliang

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 检查更新 / 下载 / 安装的全过程状态。Service 与 Activity 同进程，直接共享这一个状态源。 */
sealed interface UpdateState {
    /** 空闲：未检查，也没有正在进行的下载。 */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** 检查完成：有新版本可用。 */
    data class Available(val latestVersion: String, val currentVersion: String) : UpdateState

    /** 检查完成：当前已是最新版本。 */
    data class UpToDate(val version: String) : UpdateState

    /** 检查或下载失败，[message] 可直接展示给用户。 */
    data class Failed(val message: String) : UpdateState

    data class Downloading(
        val version: String,
        val bytesRead: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long
    ) : UpdateState {
        /** 进度比例；服务端未给出总长度时为 0（UI 应显示为不确定进度）。 */
        val fraction: Float
            get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    /** 已下载并通过校验，等待拉起系统安装器。 */
    data class Ready(val file: File, val version: String) : UpdateState
}

/**
 * 更新相关的共享状态与本地安装包管理。
 *
 * 下载地址复用官网的 `/download`：该入口本身就是「本站直接下载」，
 * 与网页端点击下载是同一个来源（版本号取自 R2 对象元数据），
 * 因此发布新版本只需覆盖 R2 对象，应用侧无需任何改动。
 */
object UpdateManager {

    /** 安装包存放目录，位于应用私有空间，无需存储权限；FileProvider 只暴露这一个子目录。 */
    private const val UPDATE_DIR = "update"

    /** 下载完成的安装包保留时长；超时后由 [cleanStaleDownloads] 清掉。 */
    private const val KEEP_DOWNLOADED_MILLIS = 60L * 60 * 1000

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val downloadUrl: String get() = BuildConfig.SITE_BASE_URL + "/download"

    /** 仅供下载服务与界面切换状态使用。 */
    fun setState(state: UpdateState) {
        _state.value = state
    }

    fun updateDir(context: Context): File = File(context.filesDir, UPDATE_DIR).apply { mkdirs() }

    /**
     * 安装包路径。版本号来自服务端的文件名元数据，这里过一遍字符白名单，
     * 避免其中的路径分隔符之类把文件写到 update/ 目录之外。
     */
    fun apkFile(context: Context, version: String): File {
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(updateDir(context), "LiangBuLiang-$safeVersion.apk")
    }

    /** 清理上次遗留的安装包。一小时内下载的保留，可能还等着用户点「安装」。 */
    fun cleanStaleDownloads(context: Context) {
        val cutoff = System.currentTimeMillis() - KEEP_DOWNLOADED_MILLIS
        updateDir(context).listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    /** 是否已获得「安装未知应用」授权（Android 8.0 起才需要该授权）。 */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** 拉起系统安装器的 Intent；文件通过 FileProvider 以 content:// 暴露。 */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 安装前校验，返回错误描述；返回 null 表示通过。
     *
     * 下载来源只有 HTTPS 一层保护，这里再确认拿到的东西确实是「同一个应用的新版本」：
     * 包名一致、版本确实更高、签名与当前安装的应用一致。签名不符时系统安装器本来也会拒绝，
     * 但那时的报错对用户很难理解，提前拦下能给出更清楚的原因。
     */
    fun verifyApk(context: Context, file: File): String? {
        if (!file.exists() || file.length() == 0L) return "安装包不完整，请重新下载"

        val pm = context.packageManager
        val downloaded = archiveInfo(pm, file.absolutePath) ?: return "安装包已损坏，无法解析"
        if (downloaded.packageName != context.packageName) return "安装包与应用不匹配"

        val currentCode = installedInfo(pm, context.packageName)?.longVersionCode
            ?: return "无法读取当前应用信息"
        if (downloaded.longVersionCode <= currentCode) return "下载到的版本不比当前版本新，已取消安装"

        val installed = installedInfo(pm, context.packageName) ?: return "无法读取当前应用的签名"
        val installedDigests = signerDigests(installed)
        if (installedDigests.isEmpty() || installedDigests != signerDigests(downloaded)) {
            return "安装包签名与当前应用不一致，无法覆盖安装"
        }
        return null
    }

    /**
     * 读取安装包（APK 文件）或已安装应用的信息。
     *
     * 这里统一用带 Int 标志的旧重载：它在 Android 13+ 上被标记为废弃但依然可用，
     * 而 `PackageInfoFlags` 重载要求 API 33+，用它会平白多出一条分支。
     */
    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apkPath: String): PackageInfo? =
        pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo? =
        pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)

    /** 签名证书的 SHA-256 集合；比较指纹而不是证书本身，跨版本 API 都能拿到。 */
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** 把字节数格式化成 `15.6 MB` 这样的短文本，用于通知与进度对话框。 */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
