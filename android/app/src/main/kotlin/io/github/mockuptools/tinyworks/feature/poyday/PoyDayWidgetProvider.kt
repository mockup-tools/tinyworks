package io.github.mockuptools.tinyworks.feature.poyday

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.mockuptools.tinyworks.MainActivity
import io.github.mockuptools.tinyworks.R
import java.time.LocalDate

class PoyDayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PoyDayWidgetProvider::class.java)
            appWidgetManager.getAppWidgetIds(componentName).forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val repository = PoyDayRepository(context)
            val today = LocalDate.now()
            val thisSunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
            val nextSunday = thisSunday.plusDays(7)
            val schedule = PoyDayScheduleCalculator.occurrences(
                startDate = thisSunday,
                endDateInclusive = nextSunday.plusDays(6),
                rules = repository.loadRules(),
            )

            val views = RemoteViews(context.packageName, R.layout.poyday_widget)
            val thisWeekIds = intArrayOf(
                R.id.poyday_widget_this_sun,
                R.id.poyday_widget_this_mon,
                R.id.poyday_widget_this_tue,
                R.id.poyday_widget_this_wed,
                R.id.poyday_widget_this_thu,
                R.id.poyday_widget_this_fri,
                R.id.poyday_widget_this_sat,
            )
            val nextWeekIds = intArrayOf(
                R.id.poyday_widget_next_sun,
                R.id.poyday_widget_next_mon,
                R.id.poyday_widget_next_tue,
                R.id.poyday_widget_next_wed,
                R.id.poyday_widget_next_thu,
                R.id.poyday_widget_next_fri,
                R.id.poyday_widget_next_sat,
            )

            setWeekText(views, thisWeekIds, thisSunday, schedule)
            setWeekText(views, nextWeekIds, nextSunday, schedule)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_POYDAY, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                9100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.poyday_widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun setWeekText(
            views: RemoteViews,
            viewIds: IntArray,
            startDate: LocalDate,
            schedule: Map<LocalDate, Set<PoyDayCategory>>,
        ) {
            viewIds.forEachIndexed { index, viewId ->
                val date = startDate.plusDays(index.toLong())
                val icons = schedule[date]
                    .orEmpty()
                    .sortedBy(PoyDayCategory::ordinal)
                    .joinToString(separator = "") { it.icon }
                val text = if (icons.isEmpty()) {
                    "${date.dayOfMonth}日"
                } else {
                    "${date.dayOfMonth}日\n$icons"
                }
                views.setTextViewText(viewId, text)
            }
        }
    }
}
