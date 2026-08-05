package com.hanifedma.streak.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.streak.i18n.Lang
import com.hanifedma.streak.i18n.Strings.t
import com.hanifedma.streak.ui.theme.Streak

/**
 * Sign-in. Signing in is optional throughout: "use on this device" is a
 * first-class choice, not a dead end, and everything works without an account.
 */
@Composable
fun LoginScreen(
    lang: Lang,
    firebaseAvailable: Boolean,
    onGoogle: () -> Unit,
    onLocal: () -> Unit,
) {
    val c = Streak.colors
    Box(
        Modifier.fillMaxSize().background(c.bg).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(18.dp))
                .border(1.dp, c.border, RoundedCornerShape(18.dp))
                .padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TickMark()
            Spacer(Modifier.height(14.dp))
            Text(
                t(lang, "login.h1"),
                color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                t(lang, "login.sub"),
                color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))

            if (firebaseAvailable) {
                Button(
                    onClick = onGoogle,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.surface2, contentColor = c.text,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.borderStrong),
                ) {
                    Text(t(lang, "login.google"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Never show a sign-in button that cannot possibly work.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        t(lang, "setup.h1"),
                        color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t(lang, "setup.p1"),
                        color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onLocal) {
                Text(t(lang, "login.local"), color = c.accent, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(t(lang, "login.privacy"), color = c.muted, fontSize = 12.sp)
        }
    }
}

/** The app's mark: the same bold tick as the web favicon. */
@Composable
private fun TickMark(size: androidx.compose.ui.unit.Dp = 56.dp) {
    val c = Streak.colors
    Box(
        Modifier
            .size(size)
            .background(c.accent.copy(alpha = 0.16f), RoundedCornerShape(15.dp))
            .border(2.dp, c.accent, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.62f)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.10f, h * 0.52f)
                lineTo(w * 0.38f, h * 0.80f)
                lineTo(w * 0.90f, h * 0.18f)
            }
            drawPath(
                path,
                color = c.accent,
                style = Stroke(
                    width = w * 0.16f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
