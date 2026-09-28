package kg.edu.tin.liangbuliang

import kotlin.ConsistentCopyVisibility

/**
 * 版本号（形如 `v26.08.r145`）的解析与比较。
 *
 * 生成规则见根目录 `build.gradle.kts`：`versionName = v{年后两位}.{月两位}.r{年内发布序号}`，
 * 而 `versionCode` 正是这三段的十进制拼接（`v26.08.r145` → `2608145`）。
 * 因此逐段按数字比较与比较 versionCode 完全等价 —— 跨年时发布序号会归 1，
 * 但年段（×100000）大于月段与序号的任何取值，单调性不会被破坏。
 */
@ConsistentCopyVisibility
data class AppVersion private constructor(
    val raw: String,
    val year: Int,
    val month: Int,
    val release: Int
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.release })

    override fun toString(): String = raw

    companion object {
        private val PATTERN = Regex("^v?(\\d{2})\\.(\\d{2})\\.r(\\d{1,3})$")

        /** 解析版本号；格式不符时返回 null，由调用方退化处理。 */
        fun parse(version: String?): AppVersion? {
            val text = version?.trim().orEmpty()
            val match = PATTERN.matchEntire(text) ?: return null
            return AppVersion(
                raw = text,
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                release = match.groupValues[3].toInt()
            )
        }

        /** 服务端的 [latest] 是否比当前安装的 [current] 更新。 */
        fun isNewer(latest: String?, current: String?): Boolean {
            if (latest.isNullOrBlank()) return false
            val remote = parse(latest)
            val local = parse(current)
            // 任一侧格式不符时退化为「字符串不同即认为有新版本」：
            // 下载后会再用安装包自身的 versionCode 复核，不会误装降级包。
            if (remote == null || local == null) return latest.trim() != current?.trim()
            return remote > local
        }
    }
}
