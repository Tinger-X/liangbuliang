package kg.edu.tin.liangbuliang

enum class TimeoutOption(val label: String, val timeoutMs: Int) {
    SEC_5("5s", 5_000),
    SEC_10("10s", 10_000),
    SEC_15("15s", 15_000),
    SEC_30("30s", 30_000),
    SEC_45("45s", 45_000),
    MIN_1("1min", 60_000),
    MIN_2("2min", 120_000),
    MIN_3("3min", 180_000),
    MIN_5("5min", 300_000),
    MIN_10("10min", 600_000),
    MIN_15("15min", 900_000),
    MIN_20("20min", 1_200_000),
    MIN_25("25min", 1_500_000),
    MIN_30("30min", 1_800_000),
    MIN_40("40min", 2_400_000),
    MIN_45("45min", 2_700_000),
    MIN_50("50min", 3_000_000),
    HOUR_1("1h", 3_600_000),
    HOUR_1_5("1.5h", 5_400_000),
    HOUR_2("2h", 7_200_000),
    HOUR_2_5("2.5h", 9_000_000),
    HOUR_3("3h", 10_800_000),
    HOUR_4("4h", 14_400_000),
    HOUR_5("5h", 18_000_000),
    ALWAYS_ON("常亮", Int.MAX_VALUE);

    companion object {
        fun fromIndex(index: Int): TimeoutOption {
            val options = values()
            return if (index in options.indices) options[index] else ALWAYS_ON
        }

        /** Index of the option whose timeoutMs is closest to [timeoutMs]. */
        fun closestIndex(timeoutMs: Int): Int {
            val options = values()
            var best = 0
            var bestDist = Long.MAX_VALUE
            for (i in options.indices) {
                val dist = kotlin.math.abs(options[i].timeoutMs.toLong() - timeoutMs.toLong())
                if (dist < bestDist) {
                    bestDist = dist
                    best = i
                }
            }
            return best
        }
    }
}
