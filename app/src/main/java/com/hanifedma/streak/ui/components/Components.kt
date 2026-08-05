package com.hanifedma.streak.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.streak.ui.theme.Streak

/**
 * A progress ring, as on the web: a track plus an accent arc sweeping from
 * twelve o'clock. Drawn on a Canvas rather than with a CircularProgressIndicator
 * so the stroke and colours match the web app exactly.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 5.dp,
    color: Color? = null,
    label: String? = null,
) {
    val c = Streak.colors
    val arcColor = color ?: c.accent
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "ring",
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(
                color = c.track,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = sw),
            )
            if (animated > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        if (label != null) {
            Text(
                label,
                fontSize = if (size < 40.dp) 9.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = c.text,
            )
        }
    }
}

/** The small colour dot that identifies a habit in lists. */
@Composable
fun HabitDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** A card matching the web app's surface + hairline border. */
@Composable
fun StreakCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val c = Streak.colors
    Box(
        modifier
            .background(c.surface, RoundedCornerShape(14.dp))
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(padding)
    ) { content() }
}

/** The shared empty state: emoji, headline, subtext, optional action. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Streak.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = c.text, fontWeight = FontWeight.Medium)
        Text(subtitle, color = c.muted, fontSize = 14.sp)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** A labelled section heading, used across the settings sheets. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(bottom = 8.dp),
        color = Streak.colors.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** A thin progress bar, used under measurable habits in the Today list. */
@Composable
fun ThinProgress(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val c = Streak.colors
    Box(
        modifier
            .height(3.dp)
            .width(180.dp)
            .background(c.track, RoundedCornerShape(999.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(color, RoundedCornerShape(999.dp))
        )
    }
}

/** The weekday bar chart in the stats pane. */
@Composable
fun WeekdayBars(
    labels: List<String>,
    percents: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val c = Streak.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val pct = percents.getOrElse(i) { 0 }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .background(c.track, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // A 0% bar draws nothing at all — with a minimum height it
                    // would be indistinguishable from 1%.
                    if (pct > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((78 * pct / 100f).dp.coerceAtLeast(3.dp))
                                .background(color, RoundedCornerShape(6.dp))
                        )
                    }
                }
                Text(label, fontSize = 11.sp, color = c.muted)
                Text("$pct%", fontSize = 10.sp, color = c.faint)
            }
        }
    }
}
