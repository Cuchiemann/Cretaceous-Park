package com.momentadesunt.cretaceouspark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Pal {
    val bg = Color(0xFF15201A)
    val panel = Color(0xE61C2A22)
    val panel2 = Color(0xFF243529)
    val line = Color(0xFF2C3A31)
    val ink = Color(0xFFEAF0E6)
    val ink2 = Color(0xFFB8C4BB)
    val ink3 = Color(0xFF7F8F84)
    val grass = Color(0xFF6DBA66)
    val grassDark = Color(0xFF3F7F3D)
    val sand = Color(0xFFE8CF85)
    val water = Color(0xFF5FB0E3)
    val alert = Color(0xFFE85A4D)
    val amber = Color(0xFFF0B04A)
    val ok = Color(0xFF7BE07B)
}

@Composable
fun CretaceousTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.grass, onPrimary = Color(0xFF0F1A13), background = Pal.bg, surface = Pal.panel2,
            onBackground = Pal.ink, onSurface = Pal.ink, secondary = Pal.sand, error = Pal.alert
        ),
        content = content
    )
}

/** Botón de bloque, plano, grande para el dedo. */
@Composable
fun BlockButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color = Pal.grass, textColor: Color = Color(0xFF0F1A13), enabled: Boolean = true, small: Boolean = false, sub: String? = null
) {
    val c = if (enabled) color else Pal.line
    val tc = if (enabled) textColor else Pal.ink3
    Column(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(c)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 6.dp else 10.dp)
            .heightIn(min = if (small) 32.dp else 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = tc, fontWeight = FontWeight.Bold, fontSize = if (small) 13.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, color = tc.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, color: Color = Pal.panel2, textColor: Color = Pal.ink, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val m = modifier.clip(RoundedCornerShape(4.dp)).background(color).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 10.dp, vertical = 6.dp)
    Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = m, maxLines = 1)
}

@Composable
fun Bar(label: String, value: Float, max: Float = 100f, color: Color = Pal.grass, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Pal.ink2, fontSize = 12.sp)
            Text("${value.toInt()}", color = Pal.ink, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(2.dp)).background(Pal.line)) {
            Box(Modifier.fillMaxWidth((value / max).coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
    }
}

@Composable
fun Title(text: String) = Text(text, color = Pal.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)

@Composable
fun Label(text: String) = Text(text, color = Pal.ink3, fontSize = 11.sp, letterSpacing = 1.sp)

@Composable
fun Body(text: String, color: Color = Pal.ink2) = Text(text, color = color, fontSize = 13.sp, lineHeight = 17.sp)

fun money(v: Double): String = money(v.toFloat())
fun money(v: Float): String {
    val a = kotlin.math.abs(v)
    val s = when {
        a >= 1_000_000f -> String.format("%.2fM", a / 1_000_000f)
        a >= 10_000f -> String.format("%.1fk", a / 1000f)
        else -> a.toInt().toString()
    }
    return (if (v < 0) "-" else "") + s + " $"
}

fun stars(v: Float): String {
    val full = v.toInt(); val half = v - full >= 0.5f
    return "★".repeat(full) + (if (half) "½" else "") + "☆".repeat(5 - full - (if (half) 1 else 0))
}
