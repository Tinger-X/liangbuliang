package kg.edu.tin.liangbuliang

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 版本检查：请求官网的 `/api/stats`，取其中的 `version` 字段。
 *
 * 该接口本来供官网首页显示「当前最新版本」用，版本号来自 R2 对象元数据 ——
 * 与 `/download` 返回的安装包是同一个来源。因此发布新版本只需覆盖 R2 对象，
 * 应用与后端都不必改动，也不需要新增接口。
 */
object UpdateChecker {

    private const val STATS_PATH = "/api/stats"
    private const val TIMEOUT_SECONDS = 10L

    private val userAgent: String get() = "LiangBuLiang/" + BuildConfig.VERSION_NAME

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    sealed interface Result {
        data class Success(val version: String) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(BuildConfig.SITE_BASE_URL + STATS_PATH)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.Failure("检查更新失败（HTTP ${response.code}）")
                }
                val body = response.body?.string().orEmpty()
                val version = JSONObject(body).optString("version").takeIf { it.isNotBlank() }
                    ?: return@withContext Result.Failure("检查更新失败：服务端未返回版本号")
                Result.Success(version)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure("检查更新失败，请检查网络后重试")
        }
    }
}
