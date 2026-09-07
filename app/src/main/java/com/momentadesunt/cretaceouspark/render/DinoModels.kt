package com.momentadesunt.cretaceouspark.render

import com.momentadesunt.cretaceouspark.core.Dino
import com.momentadesunt.cretaceouspark.core.Size
import kotlin.math.abs
import kotlin.math.sin

/** Receptor de cajas en coordenadas de mundo. */
fun interface BoxSink { fun box(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float, color: Int) }

/**
 * Modelos voxel por especie. Cada receta se describe en un marco local:
 * f = adelante (cola → cabeza), l = lateral, z = altura. Unidades para un dino mediano; se escalan por tamaño.
 *
 * Los modelos son rígidos: la animación de paso solo eleva el cuerpo (lift), nunca deforma las piezas,
 * para que el orden de pintado entre cajas sea el mismo en todos los fotogramas.
 */
class DinoBuilder(
    private val cx: Float, private val cy: Float, private val facing: Int, private val scale: Float,
    private val lift: Float, val body: Int, val detail: Int, private val sink: BoxSink
) {
    private val alongX = facing == 1 || facing == 3
    private val sgn = if (facing == 1 || facing == 2) 1f else -1f
    var top = 0f
    private val eye = 0xFF1B1B1B.toInt()
    val belly = shade(body, 1.18f)
    val dark = shade(body, 0.72f)
    val jaw = shade(body, 1.3f)

    /** Caja centrada en (fc, lc) del marco local, base en z, tamaños lenF × lenL × h. */
    fun part(fc: Float, lc: Float, z: Float, lenF: Float, lenL: Float, h: Float, color: Int, grounded: Boolean = false) {
        val s = scale
        val zz = if (grounded) 0f else z * s + lift
        val hh = if (grounded) h * s + lift else h * s
        val (wx, wy) = if (alongX) Pair(cx + sgn * fc * s, cy + lc * s) else Pair(cx + lc * s, cy + sgn * fc * s)
        val w = (if (alongX) lenF else lenL) * s
        val d = (if (alongX) lenL else lenF) * s
        sink.box(wx - w / 2f, wy - d / 2f, zz, w, d, hh, color)
        if (zz + hh > top) top = zz + hh
    }

    /** Patas con pie y dedos, en las posiciones f dadas (pares izquierda/derecha). */
    fun legs(h: Float, thick: Float, spread: Float, vararg fPos: Float) {
        for (f in fPos) for (side in floatArrayOf(-spread, spread)) {
            part(f, side, 0f, thick, thick, h, dark, grounded = true)
            part(f + thick * 0.95f, side, 0f, thick * 0.9f, thick * 1.1f, thick * 0.45f, dark, grounded = true)   // pie, a ras del frente de la pata
            part(f + thick * 1.55f, side, 0f, thick * 0.3f, thick * 0.7f, thick * 0.3f, 0xFFE8E1C9.toInt(), grounded = true)   // garras
        }
    }

    /** Cuerpo: lomo, vientre más claro y una franja dorsal más oscura. */
    fun torso(fc: Float, z: Float, lenF: Float, lenL: Float, h: Float) {
        part(fc, 0f, z, lenF, lenL, h, body)
        part(fc, 0f, z - h * 0.18f, lenF * 0.82f, lenL * 0.8f, h * 0.18f, belly)      // vientre
        part(fc, 0f, z + h, lenF * 0.7f, lenL * 0.35f, h * 0.08f, dark)               // franja dorsal
    }

    /** Cabeza: cráneo, mandíbula clara, ojos y orificios nasales. */
    fun head(fc: Float, z: Float, lenF: Float, lenL: Float, h: Float) {
        part(fc, 0f, z + h * 0.3f, lenF, lenL, h * 0.7f, body)
        part(fc + lenF * 0.08f, 0f, z, lenF * 0.85f, lenL * 0.9f, h * 0.3f, jaw)      // mandíbula
        val ez = z + h * 0.65f
        part(fc + lenF * 0.12f, -(lenL / 2f + 0.015f), ez, 0.07f, 0.03f, 0.07f, eye)   // a ras del costado
        part(fc + lenF * 0.12f, lenL / 2f + 0.015f, ez, 0.07f, 0.03f, 0.07f, eye)
        part(fc + lenF * 0.42f, -lenL * 0.22f, z + h, 0.05f, 0.04f, 0.03f, dark)        // narinas
        part(fc + lenF * 0.42f, lenL * 0.22f, z + h, 0.05f, 0.04f, 0.03f, dark)
    }

    /** Cola en tres segmentos que se afinan y caen ligeramente. */
    fun tail(fStart: Float, z: Float, len: Float, thick: Float, color: Int = body, drop: Float = 0.05f) {
        val seg = len / 3f
        part(fStart - seg * 0.5f, 0f, z, seg, thick, thick, color)
        part(fStart - seg * 1.5f, 0f, z - drop, seg, thick * 0.75f, thick * 0.75f, color)
        part(fStart - seg * 2.5f, 0f, z - drop * 2f, seg, thick * 0.5f, thick * 0.5f, detail)
    }

    /** Brazos pegados al costado (spread = mitad del ancho del cuerpo). */
    fun arms(fc: Float, z: Float, spread: Float, len: Float = 0.2f) {
        for (sg in floatArrayOf(-1f, 1f)) {
            val side = sg * (spread + 0.04f)
            part(fc, side, z, len, 0.08f, 0.08f, dark)
            part(fc + len * 0.5f + 0.03f, side, z - 0.04f, 0.06f, 0.06f, 0.06f, 0xFFE8E1C9.toInt())   // garra
        }
    }

    /** Franjas transversales sobre el lomo. */
    fun stripes(fc: Float, z: Float, lenL: Float, count: Int, spacing: Float) {
        for (i in 0 until count) part(fc - (count - 1) * spacing / 2f + i * spacing, 0f, z, 0.06f, lenL, 0.03f, detail)
    }

    /** Manchas alternas a los lados del cuerpo. */
    fun spots(fc: Float, z: Float, lenL: Float, count: Int, spacing: Float) {
        for (i in 0 until count) part(fc - (count - 1) * spacing / 2f + i * spacing, if (i % 2 == 0) -(lenL / 2f + 0.01f) else lenL / 2f + 0.01f, z, 0.1f, 0.02f, 0.1f, detail)
    }

    companion object {
        fun shade(c: Int, f: Float): Int {
            val r = (((c shr 16) and 255) * f).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 255) * f).toInt().coerceIn(0, 255)
            val b = ((c and 255) * f).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

object DinoModels {
    private val cream = 0xFFF4F1EA.toInt()
    private val horn = 0xFFE8E1C9.toInt()

    /** Escala por tamaño, ajustada para que las vallas (0,7–1,35 de alto) se lean como contención. */
    fun scaleOf(size: Size) = when (size) { Size.S -> 0.45f; Size.M -> 0.75f; Size.L -> 1.15f }

    /** Construye el modelo del dino y devuelve la altura máxima (para burbujas de estado). */
    fun build(d: Dino, sink: BoxSink): Float {
        val def = d.def
        val body = if (d.skin == 1) def.colorDetail else def.colorBody
        val detail = if (d.skin == 1) def.colorBody else def.colorDetail
        val moving = abs(d.tx - d.x) + abs(d.ty - d.y) > 0.1f && d.sleep <= 0f
        val hop = if (moving) abs(sin(d.hop)) else 0f
        val scale = scaleOf(def.size)
        val lift = if (d.sleep > 0f) 0f else hop * 0.08f * scale
        val b = DinoBuilder(d.x, d.y, d.facing, scale, lift, body, detail, sink)
        if (d.sleep > 0f) { asleep(b); return b.top }
        when (def.id) {
            "gallimimus" -> gallimimus(b)
            "dryosaurus" -> dryosaurus(b)
            "parasaurolophus" -> parasaurolophus(b)
            "stegosaurus" -> stegosaurus(b)
            "ankylosaurus" -> ankylosaurus(b)
            "triceratops" -> triceratops(b)
            "brachiosaurus" -> brachiosaurus(b)
            "diplodocus" -> diplodocus(b)
            "compsognathus" -> compsognathus(b)
            "velociraptor" -> velociraptor(b)
            "dilophosaurus" -> dilophosaurus(b)
            "ceratosaurus" -> ceratosaurus(b)
            "carnotaurus" -> carnotaurus(b)
            "allosaurus" -> allosaurus(b)
            "spinosaurus" -> spinosaurus(b)
            else -> tyrannosaurus(b)
        }
        return b.top
    }

    /** Dormido: tumbado, sin patas visibles. */
    private fun asleep(b: DinoBuilder) {
        b.part(0f, 0f, 0f, 1.3f, 0.7f, 0.35f, b.body)
        b.part(0.85f, 0f, 0f, 0.4f, 0.35f, 0.3f, b.body)
        b.tail(-0.65f, 0.05f, 0.8f, 0.18f, drop = 0f)
        b.part(0.9f, 0f, 0.45f, 0.2f, 0.2f, 0.12f, 0xFF9AA5B1.toInt())   // "z"
    }

    // ---------------------------------------------------------------- herbívoros
    private fun gallimimus(b: DinoBuilder) {
        b.legs(0.55f, 0.12f, 0.15f, -0.05f)
        b.torso(0f, 0.55f, 0.9f, 0.38f, 0.38f)
        b.part(0.5f, 0f, 0.8f, 0.16f, 0.16f, 0.55f, b.body)          // cuello largo vertical
        b.part(0.5f, 0f, 0.8f, 0.1f, 0.1f, 0.55f, b.belly)
        b.head(0.62f, 1.3f, 0.34f, 0.18f, 0.18f)
        b.part(0.85f, 0f, 1.3f, 0.14f, 0.1f, 0.08f, b.detail)          // pico
        b.arms(0.3f, 0.7f, 0.19f, 0.14f)
        b.tail(-0.45f, 0.7f, 0.85f, 0.12f)
    }

    private fun dryosaurus(b: DinoBuilder) {
        b.legs(0.4f, 0.14f, 0.16f, -0.1f)
        b.torso(0f, 0.4f, 0.85f, 0.42f, 0.42f)
        b.stripes(-0.1f, 0.82f, 0.36f, 4, 0.16f)
        b.part(0.45f, 0f, 0.6f, 0.18f, 0.18f, 0.3f, b.body)
        b.head(0.6f, 0.85f, 0.3f, 0.22f, 0.22f)
        b.arms(0.3f, 0.5f, 0.21f, 0.15f)
        b.tail(-0.42f, 0.5f, 0.65f, 0.14f)
    }

    private fun parasaurolophus(b: DinoBuilder) {
        b.legs(0.5f, 0.18f, 0.24f, -0.45f, 0.35f)
        b.torso(0f, 0.5f, 1.3f, 0.6f, 0.6f)
        b.spots(0f, 0.8f, 0.6f, 5, 0.22f)
        b.part(0.75f, 0f, 0.9f, 0.3f, 0.3f, 0.45f, b.body)            // cuello
        b.head(0.95f, 1.3f, 0.4f, 0.26f, 0.26f)
        b.part(0.62f, 0f, 1.45f, 0.55f, 0.1f, 0.1f, b.detail)          // cresta hacia atrás
        b.part(0.35f, 0f, 1.5f, 0.12f, 0.08f, 0.06f, b.detail)
        b.tail(-0.65f, 0.65f, 1.05f, 0.2f)
    }

    private fun stegosaurus(b: DinoBuilder) {
        b.legs(0.45f, 0.2f, 0.26f, -0.5f, 0.4f)
        b.torso(0f, 0.45f, 1.45f, 0.7f, 0.65f)
        b.head(0.9f, 0.4f, 0.32f, 0.26f, 0.22f)
        var f = -0.55f
        var i = 0
        while (f <= 0.55f) {
            val h = 0.32f + 0.1f * (1f - abs(f))
            b.part(f, if (i % 2 == 0) -0.09f else 0.09f, 1.15f, 0.14f, 0.08f, h, b.detail)
            b.part(f, if (i % 2 == 0) -0.09f else 0.09f, 1.15f + h, 0.08f, 0.05f, 0.06f, DinoBuilder.shade(b.detail, 0.8f))   // punta
            f += 0.22f; i++
        }
        b.tail(-0.72f, 0.55f, 0.95f, 0.2f)
        for (side in floatArrayOf(-0.12f, 0.12f)) { b.part(-1.25f, side, 0.72f, 0.08f, 0.06f, 0.25f, horn); b.part(-1.05f, side, 0.72f, 0.08f, 0.06f, 0.2f, horn) }   // púas
    }

    private fun ankylosaurus(b: DinoBuilder) {
        b.legs(0.3f, 0.2f, 0.3f, -0.5f, 0.4f)
        b.torso(0f, 0.3f, 1.4f, 0.95f, 0.45f)
        for (fi in -2..2) for (li in -1..1) b.part(fi * 0.28f, li * 0.28f, 0.75f, 0.14f, 0.14f, 0.12f, b.detail)   // coraza
        for (fi in -2..2) b.part(fi * 0.28f, -0.505f, 0.45f, 0.1f, 0.06f, 0.14f, horn)                               // puas laterales, a ras
        for (fi in -2..2) b.part(fi * 0.28f, 0.505f, 0.45f, 0.1f, 0.06f, 0.14f, horn)
        b.head(0.88f, 0.3f, 0.36f, 0.4f, 0.28f)
        b.part(0.9f, -0.23f, 0.5f, 0.08f, 0.06f, 0.1f, horn); b.part(0.9f, 0.23f, 0.5f, 0.08f, 0.06f, 0.1f, horn)  // cuernos, a ras
        b.tail(-0.7f, 0.4f, 0.75f, 0.18f, drop = 0f)
        b.part(-1.3f, 0f, 0.33f, 0.34f, 0.34f, 0.3f, b.detail)          // maza
    }

    private fun triceratops(b: DinoBuilder) {
        b.legs(0.5f, 0.22f, 0.3f, -0.5f, 0.45f)
        b.torso(0f, 0.5f, 1.5f, 0.85f, 0.7f)
        b.head(1.0f, 0.55f, 0.55f, 0.55f, 0.45f)
        b.part(0.72f, 0f, 0.7f, 0.14f, 0.95f, 0.6f, b.detail)          // gola
        b.part(0.72f, 0f, 1.3f, 0.12f, 0.6f, 0.08f, DinoBuilder.shade(b.detail, 0.85f))   // borde de la gola
        b.part(1.25f, -0.17f, 0.95f, 0.4f, 0.08f, 0.08f, horn); b.part(1.25f, 0.17f, 0.95f, 0.4f, 0.08f, 0.08f, horn) // cuernos
        b.part(1.3f, 0f, 0.72f, 0.14f, 0.08f, 0.16f, horn)              // cuerno nasal
        b.part(1.3f, 0f, 0.55f, 0.14f, 0.2f, 0.1f, b.jaw)               // pico
        b.tail(-0.75f, 0.6f, 0.65f, 0.2f)
    }

    private fun brachiosaurus(b: DinoBuilder) {
        b.legs(0.9f, 0.24f, 0.3f, -0.5f, 0.45f)   // patas a ras del vientre
        b.torso(0f, 0.9f, 1.6f, 0.8f, 0.8f)
        b.part(0.7f, 0f, 1.6f, 0.34f, 0.34f, 0.65f, b.body)
        b.part(0.85f, 0f, 2.2f, 0.3f, 0.3f, 0.65f, b.body)
        b.part(1.0f, 0f, 2.8f, 0.26f, 0.26f, 0.55f, b.body)
        b.part(0.7f, 0f, 1.6f, 0.2f, 0.2f, 0.65f, b.belly); b.part(0.85f, 0f, 2.2f, 0.18f, 0.18f, 0.65f, b.belly)
        b.head(1.15f, 3.3f, 0.42f, 0.26f, 0.26f)
        b.part(1.05f, 0f, 3.56f, 0.16f, 0.16f, 0.1f, b.detail)          // bulto nasal
        b.tail(-0.8f, 1.1f, 1.4f, 0.25f, drop = 0.15f)
    }

    private fun diplodocus(b: DinoBuilder) {
        b.legs(0.6f, 0.22f, 0.28f, -0.5f, 0.45f)
        b.torso(0f, 0.6f, 1.6f, 0.7f, 0.6f)
        b.part(1.3f, 0f, 0.95f, 1.3f, 0.24f, 0.24f, b.body)            // cuello horizontal largo
        b.part(1.3f, 0f, 1.19f, 1.0f, 0.1f, 0.04f, b.dark)
        b.head(2.05f, 0.95f, 0.36f, 0.2f, 0.2f)
        b.tail(-0.8f, 0.75f, 1.6f, 0.16f)
        b.part(-2.4f, 0f, 0.62f, 0.8f, 0.07f, 0.07f, b.detail)          // látigo
    }

    // ---------------------------------------------------------------- carnívoros
    private fun compsognathus(b: DinoBuilder) {
        b.legs(0.3f, 0.09f, 0.1f, -0.05f)
        b.torso(0f, 0.3f, 0.6f, 0.26f, 0.26f)
        b.part(0.35f, 0f, 0.45f, 0.12f, 0.12f, 0.22f, b.body)
        b.head(0.5f, 0.62f, 0.26f, 0.14f, 0.14f)
        b.arms(0.2f, 0.32f, 0.13f, 0.1f)
        b.tail(-0.3f, 0.4f, 0.75f, 0.08f)
    }

    private fun velociraptor(b: DinoBuilder) {
        b.legs(0.42f, 0.11f, 0.13f, -0.1f)
        b.torso(0f, 0.42f, 0.95f, 0.32f, 0.32f)
        b.stripes(-0.05f, 0.77f, 0.3f, 5, 0.14f)                          // franjas
        b.part(0.52f, 0f, 0.6f, 0.2f, 0.18f, 0.3f, b.body)
        b.head(0.8f, 0.75f, 0.45f, 0.2f, 0.18f)
        b.part(0.98f, 0f, 0.72f, 0.2f, 0.16f, 0.05f, cream)             // dientes
        b.part(0.6f, 0f, 0.93f, 0.25f, 0.06f, 0.08f, b.detail)          // cresta de plumas
        b.arms(0.35f, 0.45f, 0.16f, 0.22f)
        b.tail(-0.47f, 0.55f, 1.05f, 0.1f, drop = 0f)
    }

    private fun dilophosaurus(b: DinoBuilder) {
        b.legs(0.5f, 0.15f, 0.18f, -0.15f)
        b.torso(0f, 0.5f, 1.2f, 0.5f, 0.5f)
        b.spots(0f, 0.75f, 0.5f, 5, 0.2f)
        b.part(0.65f, 0f, 0.85f, 0.26f, 0.26f, 0.4f, b.body)
        b.head(0.92f, 1.15f, 0.48f, 0.3f, 0.26f)
        b.part(0.9f, -0.09f, 1.41f, 0.28f, 0.06f, 0.22f, b.detail); b.part(0.9f, 0.09f, 1.41f, 0.28f, 0.06f, 0.22f, b.detail) // dos crestas
        b.part(0.72f, 0f, 0.95f, 0.08f, 0.8f, 0.34f, b.detail)          // gorguera
        b.part(0.72f, 0f, 1.29f, 0.06f, 0.5f, 0.06f, DinoBuilder.shade(b.detail, 0.8f))
        b.arms(0.4f, 0.55f, 0.25f)
        b.tail(-0.6f, 0.6f, 0.95f, 0.16f)
    }

    private fun ceratosaurus(b: DinoBuilder) {
        b.legs(0.55f, 0.18f, 0.2f, -0.15f)
        b.torso(0f, 0.55f, 1.3f, 0.55f, 0.55f)
        b.part(0.7f, 0f, 0.9f, 0.3f, 0.3f, 0.3f, b.body)
        b.head(0.98f, 0.95f, 0.5f, 0.34f, 0.36f)
        b.part(1.15f, 0f, 1.31f, 0.12f, 0.1f, 0.22f, b.detail)          // cuerno nasal
        b.part(1.0f, -0.14f, 1.31f, 0.08f, 0.06f, 0.1f, b.detail); b.part(1.0f, 0.14f, 1.31f, 0.08f, 0.06f, 0.1f, b.detail)   // cuernos oculares sobre el craneo
        for (i in 0 until 5) b.part(-0.5f + i * 0.25f, 0f, 1.1f, 0.08f, 0.08f, 0.08f, b.detail)   // osteodermos
        b.arms(0.4f, 0.6f, 0.275f)
        b.tail(-0.65f, 0.65f, 1.05f, 0.2f)
    }

    private fun carnotaurus(b: DinoBuilder) {
        b.legs(0.6f, 0.18f, 0.2f, -0.15f)
        b.torso(0f, 0.6f, 1.3f, 0.55f, 0.6f)
        b.part(0.7f, 0f, 1.0f, 0.3f, 0.3f, 0.35f, b.body)
        b.head(1.0f, 1.1f, 0.5f, 0.38f, 0.4f)
        b.part(1.05f, -0.16f, 1.5f, 0.12f, 0.1f, 0.2f, b.detail); b.part(1.05f, 0.16f, 1.5f, 0.12f, 0.1f, 0.2f, b.detail) // cuernos frontales
        b.spots(-0.1f, 0.9f, 0.55f, 4, 0.25f)
        b.arms(0.45f, 0.7f, 0.275f, 0.1f)
        b.tail(-0.65f, 0.7f, 1.05f, 0.22f)
    }

    private fun allosaurus(b: DinoBuilder) {
        b.legs(0.8f, 0.24f, 0.26f, -0.2f)
        b.torso(0f, 0.8f, 1.8f, 0.8f, 0.8f)
        b.part(1.0f, 0f, 1.3f, 0.4f, 0.4f, 0.4f, b.body)
        b.head(1.35f, 1.35f, 0.7f, 0.45f, 0.45f)
        b.part(1.3f, -0.18f, 1.8f, 0.2f, 0.08f, 0.14f, b.detail); b.part(1.3f, 0.18f, 1.8f, 0.2f, 0.08f, 0.14f, b.detail) // crestas oculares sobre el craneo
        b.part(1.65f, 0f, 1.32f, 0.2f, 0.4f, 0.05f, cream)               // dientes
        b.stripes(-0.2f, 1.6f, 0.7f, 4, 0.3f)
        b.arms(0.6f, 0.9f, 0.4f, 0.3f)
        b.tail(-0.9f, 0.9f, 1.5f, 0.3f, drop = 0.1f)
    }

    private fun spinosaurus(b: DinoBuilder) {
        b.legs(0.7f, 0.24f, 0.28f, -0.3f)
        b.torso(0f, 0.7f, 1.9f, 0.8f, 0.7f)
        b.part(0.0f, 0f, 1.4f, 1.4f, 0.12f, 0.85f, b.detail)           // vela
        for (i in 0 until 5) b.part(-0.56f + i * 0.28f, 0f, 1.4f, 0.06f, 0.16f, 0.85f - abs(i - 2) * 0.12f, DinoBuilder.shade(b.detail, 0.85f))   // espinas de la vela
        b.part(1.0f, 0f, 1.05f, 0.4f, 0.4f, 0.35f, b.body)
        b.head(1.45f, 1.05f, 0.95f, 0.3f, 0.3f)
        b.part(1.75f, 0f, 1.02f, 0.4f, 0.28f, 0.05f, cream)             // dientes
        b.arms(0.55f, 0.8f, 0.4f, 0.35f)
        b.tail(-0.95f, 0.8f, 1.5f, 0.28f)
    }

    private fun tyrannosaurus(b: DinoBuilder) {
        b.legs(0.9f, 0.3f, 0.3f, -0.25f)
        b.torso(0f, 0.9f, 1.8f, 0.9f, 0.9f)
        b.part(1.0f, 0f, 1.45f, 0.45f, 0.55f, 0.45f, b.body)
        b.head(1.45f, 1.5f, 0.85f, 0.65f, 0.6f)
        b.part(1.75f, 0f, 1.47f, 0.3f, 0.6f, 0.06f, cream)              // dientes
        b.part(1.4f, 0f, 2.1f, 0.5f, 0.3f, 0.1f, b.detail)              // ceja
        b.part(1.2f, -0.345f, 1.75f, 0.2f, 0.04f, 0.15f, b.dark); b.part(1.2f, 0.345f, 1.75f, 0.2f, 0.04f, 0.15f, b.dark)   // pomulos, a ras
        b.arms(0.75f, 1.05f, 0.45f, 0.18f)
        b.tail(-0.9f, 1.0f, 1.9f, 0.42f, drop = 0.1f)
    }
}
