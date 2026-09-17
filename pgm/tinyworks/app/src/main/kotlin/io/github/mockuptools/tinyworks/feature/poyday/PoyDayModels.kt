package io.github.mockuptools.tinyworks.feature.poyday

import java.time.DayOfWeek

enum class PoyDayCategory(
    val displayName: String,
    val icon: String,
) {
    COMBUSTIBLE("可燃ゴミ", "🔥"),
    NON_COMBUSTIBLE("不燃ゴミ", "■"),
    RECYCLABLE("資源ゴミ", "♻"),
}

enum class PoyDayMode(val displayName: String) {
    WEEK("週"),
    DAY_OF_MONTH("日"),
}

enum class PoyDayWeekPattern(val displayName: String) {
    EVERY("毎週"),
    ODD("奇数週"),
    EVEN("偶数週"),
    FIRST("第1週"),
    SECOND("第2週"),
    THIRD("第3週"),
    FOURTH("第4週"),
}

enum class PoyDayWeekday(
    val displayName: String,
    val dayOfWeek: DayOfWeek,
) {
    SUNDAY("日", DayOfWeek.SUNDAY),
    MONDAY("月", DayOfWeek.MONDAY),
    TUESDAY("火", DayOfWeek.TUESDAY),
    WEDNESDAY("水", DayOfWeek.WEDNESDAY),
    THURSDAY("木", DayOfWeek.THURSDAY),
    FRIDAY("金", DayOfWeek.FRIDAY),
    SATURDAY("土", DayOfWeek.SATURDAY),
}

data class PoyDayRule(
    val category: PoyDayCategory,
    val enabled: Boolean = false,
    val mode: PoyDayMode = PoyDayMode.WEEK,
    val weekPattern: PoyDayWeekPattern = PoyDayWeekPattern.EVERY,
    val weekday: PoyDayWeekday = PoyDayWeekday.SUNDAY,
    val dayOfMonth: Int = 1,
)

data class PoyDayOccurrence(
    val date: java.time.LocalDate,
    val categories: Set<PoyDayCategory>,
)

fun defaultPoyDayRules(): List<PoyDayRule> = PoyDayCategory.entries.map(::PoyDayRule)
