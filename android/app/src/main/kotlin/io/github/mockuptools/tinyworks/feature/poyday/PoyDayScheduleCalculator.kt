package io.github.mockuptools.tinyworks.feature.poyday

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields

object PoyDayScheduleCalculator {
    fun occurrences(
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        rules: Collection<PoyDayRule>,
    ): Map<LocalDate, Set<PoyDayCategory>> {
        if (startDate.isAfter(endDateInclusive)) return emptyMap()

        val activeRules = rules.filter(PoyDayRule::enabled)
        val result = linkedMapOf<LocalDate, Set<PoyDayCategory>>()
        var date = startDate
        while (!date.isAfter(endDateInclusive)) {
            val categories = activeRules
                .filter { matches(date, it) }
                .mapTo(linkedSetOf(), PoyDayRule::category)
            if (categories.isNotEmpty()) {
                result[date] = categories
            }
            date = date.plusDays(1)
        }
        return result
    }

    fun occurrencesForMonths(
        firstMonth: YearMonth,
        monthCount: Int,
        rules: Collection<PoyDayRule>,
    ): Map<LocalDate, Set<PoyDayCategory>> {
        require(monthCount > 0)
        val lastMonth = firstMonth.plusMonths(monthCount.toLong() - 1L)
        return occurrences(
            startDate = firstMonth.atDay(1),
            endDateInclusive = lastMonth.atEndOfMonth(),
            rules = rules,
        )
    }

    fun matches(date: LocalDate, rule: PoyDayRule): Boolean {
        if (!rule.enabled) return false

        return when (rule.mode) {
            PoyDayMode.DAY_OF_MONTH -> date.dayOfMonth == rule.dayOfMonth
            PoyDayMode.WEEK -> matchesWeek(date, rule)
        }
    }

    private fun matchesWeek(date: LocalDate, rule: PoyDayRule): Boolean {
        if (date.dayOfWeek != rule.weekday.dayOfWeek) return false

        return when (rule.weekPattern) {
            PoyDayWeekPattern.EVERY -> true
            PoyDayWeekPattern.ODD -> isoWeekNumber(date) % 2 == 1
            PoyDayWeekPattern.EVEN -> isoWeekNumber(date) % 2 == 0
            PoyDayWeekPattern.FIRST -> ordinalWeekdayInMonth(date) == 1
            PoyDayWeekPattern.SECOND -> ordinalWeekdayInMonth(date) == 2
            PoyDayWeekPattern.THIRD -> ordinalWeekdayInMonth(date) == 3
            PoyDayWeekPattern.FOURTH -> ordinalWeekdayInMonth(date) == 4
        }
    }

    private fun isoWeekNumber(date: LocalDate): Int =
        date.get(WeekFields.ISO.weekOfWeekBasedYear())

    private fun ordinalWeekdayInMonth(date: LocalDate): Int =
        ((date.dayOfMonth - 1) / 7) + 1
}
