package com.studyhub.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.studyhub.app.MainActivity
import com.studyhub.app.R
import com.studyhub.app.data.Fmt
import com.studyhub.app.data.StudyItem
import com.studyhub.app.data.Store
import com.studyhub.app.data.Week
import com.studyhub.app.ui.Palette
import java.time.Duration
import java.time.Instant

object Widgets {
    suspend fun updateAll(c: Context) {
        runCatching {
            NextDeadlineWidget().updateAll(c)
            DeadlineListWidget().updateAll(c)
        }
    }
}

/** What the widgets show, read fresh each time they redraw. */
private class WidgetData(val upcoming: List<StudyItem>, val weekDone: Int, val weekTotal: Int, val hasFeed: Boolean) {
    companion object {
        fun load(c: Context): WidgetData {
            val done = Store.done(c)
            val now = Instant.now()
            val deadlines = Store.cachedItems(c).filter { it.tracksCompletion }
            val upcoming = deadlines.filter { it.id !in done && !it.due.isBefore(now) }.sortedBy { it.due }
            val (start, end) = Week.current()
            val week = deadlines.filter { !it.due.isBefore(start) && it.due.isBefore(end) }
            return WidgetData(upcoming.take(8), week.count { it.id in done }, week.size, Store.feed(c).isNotBlank())
        }
    }
}

private val idKey = ActionParameters.Key<String>("itemId")

/** Runs when you tap ✓ on a widget. */
class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[idKey] ?: return
        Store.toggleDone(context, id)
        Widgets.updateAll(context)
    }
}

private fun white(alpha: Float = 1f) = ColorProvider(Color.White.copy(alpha = alpha))

class NextDeadlineWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { NextDeadlineContent(WidgetData.load(context)) }
    }
}

class DeadlineListWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { DeadlineListContent(WidgetData.load(context)) }
    }
}

class NextDeadlineReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextDeadlineWidget()
}

class DeadlineListReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeadlineListWidget()
}

private val container: GlanceModifier
    get() = GlanceModifier
        .fillMaxSize()
        .background(ImageProvider(R.drawable.widget_bg))
        .cornerRadius(24.dp)
        .padding(14.dp)
        .clickable(actionStartActivity<MainActivity>())

@Composable
private fun EmptyContent(hasFeed: Boolean) {
    Column {
        Text(
            if (hasFeed) "All caught up" else "Open the app",
            style = TextStyle(color = white(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            if (hasFeed) "No upcoming deadlines." else "Add your Canvas link to see deadlines.",
            style = TextStyle(color = white(0.7f), fontSize = 12.sp)
        )
    }
}

@Composable
private fun NextDeadlineContent(data: WidgetData) {
    Column(modifier = container) {
        val item = data.upcoming.firstOrNull()
        if (item == null) {
            EmptyContent(data.hasFeed)
        } else {
            Text(
                item.kind.label.uppercase(),
                style = TextStyle(color = ColorProvider(item.kind.color), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                item.title,
                maxLines = 3,
                style = TextStyle(color = white(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                Fmt.countdown(item.due),
                style = TextStyle(color = white(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            )
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.course ?: Fmt.full(item.due),
                    maxLines = 1,
                    style = TextStyle(color = white(0.7f), fontSize = 11.sp),
                    modifier = GlanceModifier.defaultWeight()
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_check_circle),
                    contentDescription = "Mark done",
                    colorFilter = ColorFilter.tint(ColorProvider(Palette.Mint)),
                    modifier = GlanceModifier
                        .size(28.dp)
                        .clickable(actionRunCallback<ToggleDoneAction>(actionParametersOf(idKey to item.id)))
                )
            }
        }
    }
}

@Composable
private fun DeadlineListContent(data: WidgetData) {
    val rows = if (LocalSize.current.height > 220.dp) 6 else 3
    Column(modifier = container) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "DEADLINES",
                style = TextStyle(color = ColorProvider(Palette.Violet), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                "${data.weekDone}/${data.weekTotal} done this week",
                style = TextStyle(color = white(0.7f), fontSize = 11.sp)
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (data.upcoming.isEmpty()) {
            EmptyContent(data.hasFeed)
        } else {
            data.upcoming.take(rows).forEach { item ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_circle),
                        contentDescription = "Mark done",
                        colorFilter = ColorFilter.tint(ColorProvider(item.kind.color)),
                        modifier = GlanceModifier
                            .size(24.dp)
                            .clickable(actionRunCallback<ToggleDoneAction>(actionParametersOf(idKey to item.id)))
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            item.title,
                            maxLines = 1,
                            style = TextStyle(color = white(), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        )
                        Text(
                            item.course ?: item.kind.label,
                            maxLines = 1,
                            style = TextStyle(color = white(0.6f), fontSize = 11.sp)
                        )
                    }
                    Text(
                        Fmt.short(Duration.between(Instant.now(), item.due).seconds),
                        style = TextStyle(color = ColorProvider(badgeColor(item)), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

private fun badgeColor(item: StudyItem): Color {
    val s = Duration.between(Instant.now(), item.due).seconds
    return when {
        s < 86_400 -> Palette.Amber
        s < 3 * 86_400 -> Palette.Sky
        else -> Color.White.copy(alpha = 0.7f)
    }
}
