package com.momentadesunt.cretaceouspark.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momentadesunt.cretaceouspark.R
import kotlinx.coroutines.delay

/**
 * Presentación del estudio, una vez al arrancar: "UNA PRODUCCIÓN DE — MOMENTA DESUNT" con el logo.
 * Mismo ritmo que en Forum Domini: fundido de entrada (0,75 s), pausa (1,5 s) y fundido de salida (0,75 s).
 * Tocar en cualquier sitio la salta. La tipografía es fija (serif clásica), independiente del estilo del juego.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(750))
        delay(1500)
        fade.animateTo(0f, tween(750))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pal.bg, Color(0xFF0B120E))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp).alpha(fade.value)) {
            Text("UNA PRODUCCIÓN DE", color = Pal.ink3, fontSize = 12.sp, fontFamily = FontFamily.SansSerif, letterSpacing = 3.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "MOMENTA DESUNT", color = Pal.gold, fontSize = 30.sp, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Image(painter = painterResource(R.drawable.momenta_logo), contentDescription = "Logo de Momenta Desunt", modifier = Modifier.size(190.dp))
        }
    }
}

/** Sello pequeño del estudio para el pie del menú: logo y nombre. */
@Composable
fun StudioMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painterResource(R.drawable.momenta_logo), contentDescription = null, modifier = Modifier.size(28.dp).alpha(0.85f))
        Spacer(Modifier.width(6.dp))
        Text("Momenta Desunt", color = Pal.ink3, fontSize = 11.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
