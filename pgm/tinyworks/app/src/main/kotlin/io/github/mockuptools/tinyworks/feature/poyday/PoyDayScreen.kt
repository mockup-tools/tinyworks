package io.github.mockuptools.tinyworks.feature.poyday

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

private enum class PoyDayTab {
    CALENDAR,
    SETTINGS,
}

@Composable
fun PoyDayScreen(
    modifier: Modifier = Modifier,
    onNavigateHome: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { PoyDayRepository(context) }
    var rules by remember { mutableStateOf(repository.loadRules()) }
    var selectedTab by rememberSaveable { mutableStateOf(PoyDayTab.CALENDAR) }

    BackHandler(onBack = onNavigateHome)

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "ポイの日",
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.headlineMedium,
        )

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == PoyDayTab.CALENDAR,
                onClick = { selectedTab = PoyDayTab.CALENDAR },
                text = { Text("カレンダー") },
            )
            Tab(
                selected = selectedTab == PoyDayTab.SETTINGS,
                onClick = { selectedTab = PoyDayTab.SETTINGS },
                text = { Text("設定") },
            )
        }

        when (selectedTab) {
            PoyDayTab.CALENDAR -> PoyDayCalendarTab(
                rules = rules,
                modifier = Modifier.weight(1f),
            )

            PoyDayTab.SETTINGS -> PoyDaySettingsScreen(
                rules = rules,
                modifier = Modifier.weight(1f),
                onSave = { updatedRules ->
                    repository.saveRules(updatedRules)
                    rules = updatedRules
                    PoyDayWidgetProvider.updateAll(context)
                    selectedTab = PoyDayTab.CALENDAR
                },
            )
        }
    }
}

@Composable
private fun PoyDayCalendarTab(
    rules: List<PoyDayRule>,
    modifier: Modifier = Modifier,
) {
    val currentMonth = remember { YearMonth.now() }
    val schedule = remember(currentMonth, rules) {
        PoyDayScheduleCalculator.occurrencesForMonths(
            firstMonth = currentMonth,
            monthCount = 2,
            rules = rules,
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "今日を含む2か月分",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MonthCalendar(
            month = currentMonth,
            schedule = schedule,
        )
        MonthCalendar(
            month = currentMonth.plusMonths(1),
            schedule = schedule,
        )

        PoyDayLegend()
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    schedule: Map<LocalDate, Set<PoyDayCategory>>,
) {
    val firstDate = month.atDay(1)
    val gridStart = firstDate.minusDays((firstDate.dayOfWeek.value % 7).toLong())
    val weekCount = ((firstDate.dayOfWeek.value % 7 + month.lengthOfMonth() + 6) / 7)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            PoyDayWeekday.entries.forEach { weekday ->
                Text(
                    text = weekday.displayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = when (weekday) {
                        PoyDayWeekday.SUNDAY -> MaterialTheme.colorScheme.error
                        PoyDayWeekday.SATURDAY -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                )
            }
        }

        repeat(weekCount) { weekIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(7) { dayIndex ->
                    val date = gridStart.plusDays((weekIndex * 7 + dayIndex).toLong())
                    CalendarDayCell(
                        date = date,
                        isInMonth = date.month == month.month && date.year == month.year,
                        categories = schedule[date].orEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isInMonth: Boolean,
    categories: Set<PoyDayCategory>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isInMonth) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                else Color.Transparent,
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (isInMonth) date.dayOfMonth.toString() else "",
            color = if (isInMonth) MaterialTheme.colorScheme.onSurface else Color.Transparent,
            fontSize = 12.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categories.sortedBy(PoyDayCategory::ordinal).forEach { category ->
                Text(text = category.icon, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PoyDayLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PoyDayCategory.entries.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = category.icon, fontSize = 16.sp)
                Spacer(Modifier.width(4.dp))
                Text(text = category.displayName, fontSize = 12.sp)
            }
        }
    }
}
