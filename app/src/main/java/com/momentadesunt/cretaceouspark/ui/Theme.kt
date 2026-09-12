package com.momentadesunt.cretaceouspark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sistema de diseño al estilo Clash of Clans:
 *  - HUD oscuro sobre el mundo con insignias de recurso.
 *  - Paneles claros (pergamino) con marco oscuro, título en cinta y botón rojo de cerrar.
 *  - Botones gruesos con borde inferior más oscuro y texto blanco con sombra.
 *  - Código de color fijo: verde = confirmar, rojo = cerrar/peligro, oro = moneda y premium, azul = información, morado = genética.
 */
object Pal {
    // HUD sobre el mundo
    val bg = Color(0xFF15201A)
    val panel = Color(0xE61C2A22)
    val panel2 = Color(0xFF243529)
    val line = Color(0xFF2C3A31)
    val ink = Color(0xFFEAF0E6)
    val ink2 = Color(0xFFB8C4BB)
    val ink3 = Color(0xFF7F8F84)
    // paneles claros
    val frame = Color(0xFF4A3520)
    val frameLight = Color(0xFF8A6A45)
    val body = Color(0xFFF3E9D2)
    val bodyDark = Color(0xFFE2D3B0)
    val card = Color(0xFFFBF5E6)
    val text = Color(0xFF2B2418)
    val text2 = Color(0xFF6B5B45)
    val text3 = Color(0xFF9A8A70)
    // acciones
    val green = Color(0xFF5CB85C); val greenDark = Color(0xFF3B7F3B)
    val red = Color(0xFFE0533F); val redDark = Color(0xFF9C2F22)
    val blue = Color(0xFF4FA3D9); val blueDark = Color(0xFF2E6C93)
    val gold = Color(0xFFF2B233); val goldDark = Color(0xFFB07A12)
    val purple = Color(0xFF9B59D0); val purpleDark = Color(0xFF6A3596)
    val grey = Color(0xFFB9B1A3); val greyDark = Color(0xFF7D7669)
    val darkBtn = Color(0xFF3A4A3E); val darkBtnDark = Color(0xFF1E2A22)
    // compatibilidad con código antiguo
    val grass = green; val grassDark = greenDark; val sand = gold; val water = blue; val alert = red; val amber = gold; val ok = Color(0xFF3E9A3E)
}

val shadowStyle = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 2f))

@Composable
fun CretaceousTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Pal.green, onPrimary = Color.White, background = Pal.bg, surface = Pal.panel2, onBackground = Pal.ink, onSurface = Pal.ink, secondary = Pal.gold, error = Pal.red),
        content = content
    )
}

/** Botón grueso: cara de color con borde inferior más oscuro y texto blanco con sombra. */
@Composable
fun CocButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color = Pal.green, dark: Color = Pal.greenDark, enabled: Boolean = true,
    sub: String? = null, icon: String? = null, small: Boolean = false, textColor: Color = Color.White
) {
    val face = if (enabled) color else Pal.grey
    val edge = if (enabled) dark else Pal.greyDark
    val r = if (small) 8.dp else 10.dp
    Box(modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(r)).background(edge).clickable(enabled = enabled, onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = if (small) 3.dp else 4.dp).clip(RoundedCornerShape(r)).background(face)
                .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 5.dp else 8.dp).heightIn(min = if (small) 24.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            // lineHeight explícito: el estilo por defecto de Material3 impone 24 sp de línea y el botón pequeño se hincha.
            Text((if (icon != null) "$icon " else "") + text, color = if (enabled) textColor else Color(0xFFEDE8DC), fontWeight = FontWeight.Black, fontSize = if (small) 13.sp else 15.sp, lineHeight = if (small) 16.sp else 20.sp, style = shadowStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.92f), fontSize = if (small) 10.sp else 11.sp, lineHeight = if (small) 12.sp else 14.sp, style = shadowStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** Botón rojo cuadrado de cerrar. */
@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(Pal.redDark).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxSize().padding(bottom = 4.dp).clip(RoundedCornerShape(9.dp)).background(Pal.red), contentAlignment = Alignment.Center) {
            Text("✕", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, style = shadowStyle)
        }
    }
}

/** Marco oscuro con cuerpo claro. */
@Composable
fun CocFrame(modifier: Modifier = Modifier, body: Color = Pal.body, padding: Int = 10, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Pal.frame).padding(4.dp).clip(RoundedCornerShape(9.dp)).background(body).padding(padding.dp), content = content)
}

/** Título en cinta. */
@Composable
fun Ribbon(text: String, modifier: Modifier = Modifier, color: Color = Pal.frameLight, onClick: (() -> Unit)? = null) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(color).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 5.dp)) {
        Text(text.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp, style = shadowStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Insignia de recurso para el HUD: pastilla oscura con icono y valor. */
@Composable
fun ResourceBadge(icon: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.White, onClick: (() -> Unit)? = null, sub: String? = null, subColor: Color = Pal.ink2) {
    val m = modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xD9101A14)).border(2.dp, Color(0xFF3A4A3E), RoundedCornerShape(8.dp))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 9.dp, vertical = 4.dp)
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 14.sp, style = shadowStyle, maxLines = 1)
            if (sub != null) Text(sub, color = subColor, fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold)
        }
    }
}

/** Tarjeta clara con borde; dorada si está seleccionada. */
@Composable
fun CocCard(modifier: Modifier = Modifier, selected: Boolean = false, dim: Boolean = false, onClick: (() -> Unit)? = null, padding: Int = 8, enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (dim) Pal.bodyDark else Pal.card)
            .border(if (selected) 3.dp else 2.dp, if (selected) Pal.gold else Pal.frameLight.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }.padding(padding.dp), content = content
    )
}

/** Pastilla de coste: oro con icono de moneda. */
@Composable
fun CostBadge(text: String, modifier: Modifier = Modifier, color: Color = Pal.gold, icon: String = "💰") {
    Row(modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon.isNotEmpty()) { Text(icon, fontSize = 10.sp); Spacer(Modifier.width(3.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, style = shadowStyle, maxLines = 1)
    }
}

/** Cifra grande con etiqueta, sobre tarjeta clara. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Pal.text) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Pal.card).border(2.dp, Pal.frameLight.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = Pal.text2, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Barra de progreso con etiqueta y valor. */
@Composable
fun Bar(label: String, value: Float, max: Float = 100f, color: Color = Pal.green, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Pal.text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("${value.toInt()}", color = Pal.text, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Pal.frameLight.copy(alpha = 0.35f))) {
            Box(Modifier.fillMaxWidth((value / max).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

/** Barra fina sin etiqueta. */
@Composable
fun MiniBar(value: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(7.dp).clip(RoundedCornerShape(3.dp)).background(Pal.frameLight.copy(alpha = 0.35f))) { Box(Modifier.fillMaxWidth((value / 100f).coerceIn(0f, 1f)).fillMaxHeight().background(color)) }
}

@Composable
fun Title(text: String) = Text(text, color = Pal.text, fontSize = 18.sp, fontWeight = FontWeight.Black)

@Composable
fun Label(text: String) = Text(text, color = Pal.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)

@Composable
fun Body(text: String, color: Color = Pal.text2) = Text(text, color = color, fontSize = 13.sp, lineHeight = 17.sp)

/** Chip pequeño del HUD oscuro. */
@Composable
fun Chip(text: String, color: Color = Pal.panel2, textColor: Color = Pal.ink, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val m = modifier.clip(RoundedCornerShape(6.dp)).background(color).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 10.dp, vertical = 6.dp)
    Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = m, maxLines = 1)
}

/** Botón heredado: delega en CocButton con el borde oscuro que corresponde al color. */
@Composable
fun BlockButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color = Pal.green, textColor: Color = Color.White, enabled: Boolean = true, small: Boolean = false, sub: String? = null
) {
    CocButton(text, onClick, modifier, color = color, dark = darkOf(color), enabled = enabled, sub = sub, small = small, textColor = Color.White)
}

fun darkOf(color: Color): Color = when (color) {
    Pal.green -> Pal.greenDark; Pal.red -> Pal.redDark; Pal.gold -> Pal.goldDark; Pal.blue -> Pal.blueDark; Pal.purple -> Pal.purpleDark
    Pal.grey -> Pal.greyDark; Pal.darkBtn -> Pal.darkBtnDark; Pal.panel2, Pal.panel -> Color(0xFF141F18); else -> Color(0xFF3A3A3A)
}

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
