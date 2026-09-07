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
 */
class DinoBuilder(
    private val cx: Float, private val cy: Float, private val facing: Int, private val scale: Float,
    private val lift: Float, private val squash: Float, val body: Int, val detail: Int, private val sink: BoxSink
) {
    private val alongX = facing == 1 || facing == 3
    private val sgn = if (facing == 1 || facing == 2) 1f else -1f
    var top = 0f
    private val eye = 0xFF1B1B1B.toInt()

    /** Caja centrada en (fc, lc) del marco local, base en z, tamaños lenF × lenL × h. */
    fun part(fc: Float, lc: Float, z: Float, lenF: Float, lenL: Float, h: Float, color: Int, grounded: Boolean = false) {
        val s = scale
        val zz = (if (grounded) 0f else z * s + lift)
        val hh = if (grounded) h * s + lift else h * s
        val (wx, wy) = if (alongX) Pair(cx + sgn * fc * s, cy + lc * s) else Pair(cx + lc * s, cy + sgn * fc * s)
        val w = (if (alongX) lenF else lenL) * s
        val d = (if (alongX) lenL else lenF) * s
        sink.box(wx - w / 2f, wy - d / 2f, zz, w, d, hh, color)
        if (zz + hh > top) top = zz + hh
    }

    /** Patas: n pares (o una pareja) de altura h en las posiciones f dadas. */
    fun legs(h: Float, thick: Float, spread: Float, vararg fPos: Float) {
        for (f in fPos) { part(f, -spread, 0f, thick, thick, h, shade(body, 0.8f), grounded = true); part(f, spread, 0f, thick, thick, h, shade(body, 0.8f), grounded = true) }
    }

    /** Cuerpo con aplastamiento de animación. */
    fun torso(fc: Float, z: Float, lenF: Float, lenL: Float, h: Float, color: Int = body) = part(fc, 0f, z, lenF, lenL, h * squash, color)

    /** Cabeza con dos ojos. */
    fun head(fc: Float, z: Float, lenF: Float, lenL: Float, h: Float, color: Int = body) {
        part(fc, 0f, z, lenF, lenL, h, color)
        val ez = z + h * 0.6f
        part(fc + lenF * 0.15f, -lenL / 2f, ez, 0.07f, 0.03f, 0.07f, eye)
        part(fc + lenF * 0.15f, lenL / 2f, ez, 0.07f, 0.03f, 0.07f, eye)
    }

    /** Cola en uno o dos tramos, del cuerpo hacia atrás. */
    fun tail(fStart: Float, z: Float, len: Float, thick: Float, color: Int = detail, drop: Float = 0f) {
        part(fStart - len / 2f, 0f, z, len, thick, thick, color)
        if (drop > 0f) part(fStart - len - len * 0.3f, 0f, z - drop, len * 0.6f, thick * 0.6f, thick * 0.6f, color)
    }

    fun arms(fc: Float, z: Float, spread: Float, len: Float = 0.2f) {
        part(fc, -spread, z, len, 0.08f, 0.08f, shade(body, 0.85f)); part(fc, spread, z, len, 0.08f, 0.08f, shade(body, 0.85f))
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

    fun scaleOf(size: Size) = when (size) { Size.S -> 0.55f; Size.M -> 1.0f; Size.L -> 1.6f }

    /** Construye el modelo del dino y devuelve la altura máxima (para burbujas de estado). */
    fun build(d: Dino, sink: BoxSink): Float {
        val def = d.def
        val body = if (d.skin == 1) def.colorDetail else def.colorBody
        val detail = if (d.skin == 1) def.colorBody else def.colorDetail
        val moving = abs(d.tx - d.x) + abs(d.ty - d.y) > 0.1f && d.sleep <= 0f
        val hop = if (moving) abs(sin(d.hop)) else 0f
        val squash = if (moving) 1f - 0.10f * (1f - hop) else 1f
        val scale = scaleOf(def.size)
        val lift = if (d.sleep > 0f) 0f else hop * 0.10f * scale
        val b = DinoBuilder(d.x, d.y, d.facing, scale, lift, squash, body, detail, sink)
        if (d.sleep > 0f) { asleep(b, def.size); return b.top }
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
    private fun asleep(b: DinoBuilder, size: Size) {
        b.torso(0f, 0f, 1.3f, 0.7f, 0.35f)
        b.head(0.85f, 0f, 0.4f, 0.35f, 0.3f)
        b.tail(-0.65f, 0.05f, 0.8f, 0.18f)
    }

    // ---------------------------------------------------------------- herbívoros
    private fun gallimimus(b: DinoBuilder) {
        b.legs(0.55f, 0.12f, 0.15f, -0.05f)
        b.torso(0f, 0.55f, 0.9f, 0.38f, 0.38f)
        b.part(0.5f, 0f, 0.8f, 0.16f, 0.16f, 0.55f, b.body)          // cuello largo vertical
        b.head(0.62f, 1.3f, 0.34f, 0.18f, 0.18f)
        b.part(0.85f, 0f, 1.3f, 0.14f, 0.1f, 0.08f, b.detail)          // pico
        b.tail(-0.45f, 0.7f, 0.8f, 0.12f)
    }

    private fun dryosaurus(b: DinoBuilder) {
        b.legs(0.4f, 0.14f, 0.16f, -0.1f)
        b.torso(0f, 0.4f, 0.85f, 0.42f, 0.42f)
        b.part(0.45f, 0f, 0.6f, 0.18f, 0.18f, 0.3f, b.body)
        b.head(0.6f, 0.85f, 0.3f, 0.22f, 0.22f)
        b.arms(0.3f, 0.5f, 0.24f, 0.15f)
        b.tail(-0.42f, 0.5f, 0.6f, 0.14f)
    }

    private fun parasaurolophus(b: DinoBuilder) {
        b.legs(0.5f, 0.18f, 0.24f, -0.45f, 0.35f)
        b.torso(0f, 0.5f, 1.3f, 0.6f, 0.6f)
        b.part(0.75f, 0f, 0.9f, 0.3f, 0.3f, 0.45f, b.body)            // cuello
        b.head(0.95f, 1.3f, 0.4f, 0.26f, 0.26f)
        b.part(0.62f, 0f, 1.45f, 0.55f, 0.1f, 0.1f, b.detail)          // cresta hacia atrás
        b.tail(-0.65f, 0.65f, 1.0f, 0.2f, drop = 0.1f)
    }

    private fun stegosaurus(b: DinoBuilder) {
        b.legs(0.45f, 0.2f, 0.26f, -0.5f, 0.4f)
        b.torso(0f, 0.45f, 1.45f, 0.7f, 0.65f)
        b.head(0.9f, 0.4f, 0.32f, 0.26f, 0.22f)
        var f = -0.55f
        var i = 0
        while (f <= 0.55f) { b.part(f, if (i % 2 == 0) -0.09f else 0.09f, 1.1f, 0.14f, 0.08f, 0.32f + 0.08f * (1f - abs(f)), b.detail); f += 0.22f; i++ }
        b.tail(-0.72f, 0.55f, 0.9f, 0.2f, b.body)
        b.part(-1.25f, -0.12f, 0.75f, 0.08f, 0.06f, 0.25f, b.detail); b.part(-1.25f, 0.12f, 0.75f, 0.08f, 0.06f, 0.25f, b.detail) // púas
    }

    private fun ankylosaurus(b: DinoBuilder) {
        b.legs(0.3f, 0.2f, 0.3f, -0.5f, 0.4f)
        b.torso(0f, 0.3f, 1.4f, 0.95f, 0.45f)
        for (fi in -2..2) for (li in -1..1) b.part(fi * 0.28f, li * 0.28f, 0.75f, 0.14f, 0.14f, 0.12f, b.detail)   // coraza
        b.head(0.88f, 0.3f, 0.36f, 0.4f, 0.28f)
        b.tail(-0.7f, 0.4f, 0.7f, 0.18f, b.body)
        b.part(-1.25f, 0f, 0.35f, 0.32f, 0.32f, 0.3f, b.detail)          // maza
    }

    private fun triceratops(b: DinoBuilder) {
        b.legs(0.5f, 0.22f, 0.3f, -0.5f, 0.45f)
        b.torso(0f, 0.5f, 1.5f, 0.85f, 0.7f)
        b.head(1.0f, 0.55f, 0.55f, 0.55f, 0.45f)
        b.part(0.72f, 0f, 0.7f, 0.14f, 0.95f, 0.6f, b.detail)          // gola
        b.part(1.25f, -0.17f, 0.95f, 0.4f, 0.08f, 0.08f, cream); b.part(1.25f, 0.17f, 0.95f, 0.4f, 0.08f, 0.08f, cream) // cuernos
        b.part(1.3f, 0f, 0.75f, 0.14f, 0.08f, 0.16f, cream)              // cuerno nasal
        b.tail(-0.75f, 0.6f, 0.6f, 0.2f, b.body)
    }

    private fun brachiosaurus(b: DinoBuilder) {
        b.legs(0.85f, 0.24f, 0.3f, -0.5f); b.legs(0.95f, 0.24f, 0.3f, 0.45f)
        b.torso(0f, 0.9f, 1.6f, 0.8f, 0.8f)
        b.part(0.7f, 0f, 1.6f, 0.34f, 0.34f, 0.65f, b.body)
        b.part(0.85f, 0f, 2.2f, 0.3f, 0.3f, 0.65f, b.body)
        b.part(1.0f, 0f, 2.8f, 0.26f, 0.26f, 0.55f, b.body)
        b.head(1.15f, 3.3f, 0.42f, 0.26f, 0.26f)
        b.part(1.05f, 0f, 3.55f, 0.16f, 0.16f, 0.1f, b.detail)          // bulto nasal
        b.tail(-0.8f, 1.1f, 1.3f, 0.25f, b.body, drop = 0.35f)
    }

    private fun diplodocus(b: DinoBuilder) {
        b.legs(0.6f, 0.22f, 0.28f, -0.5f, 0.45f)
        b.torso(0f, 0.6f, 1.6f, 0.7f, 0.6f)
        b.part(1.3f, 0f, 0.95f, 1.3f, 0.24f, 0.24f, b.body)            // cuello horizontal largo
        b.head(2.05f, 0.95f, 0.36f, 0.2f, 0.2f)
        b.tail(-0.8f, 0.75f, 1.6f, 0.16f, b.body)
        b.part(-2.4f, 0f, 0.7f, 0.8f, 0.08f, 0.08f, b.detail)          // látigo
    }

    // ---------------------------------------------------------------- carnívoros
    private fun compsognathus(b: DinoBuilder) {
        b.legs(0.3f, 0.09f, 0.1f, -0.05f)
        b.torso(0f, 0.3f, 0.6f, 0.26f, 0.26f)
        b.part(0.35f, 0f, 0.45f, 0.12f, 0.12f, 0.22f, b.body)
        b.head(0.5f, 0.62f, 0.26f, 0.14f, 0.14f)
        b.tail(-0.3f, 0.4f, 0.7f, 0.08f)
    }

    private fun velociraptor(b: DinoBuilder) {
        b.legs(0.42f, 0.11f, 0.13f, -0.1f)
        b.torso(0f, 0.42f, 0.95f, 0.32f, 0.32f)
        b.part(0.0f, 0f, 0.74f, 0.8f, 0.1f, 0.06f, b.detail)           // franja dorsal
        b.part(0.52f, 0f, 0.6f, 0.2f, 0.18f, 0.3f, b.body)
        b.head(0.8f, 0.75f, 0.45f, 0.2f, 0.18f)
        b.part(0.98f, 0f, 0.7f, 0.2f, 0.16f, 0.06f, cream)             // dientes
        b.arms(0.35f, 0.45f, 0.2f, 0.22f)
        b.tail(-0.47f, 0.55f, 1.0f, 0.1f, b.body)
    }

    private fun dilophosaurus(b: DinoBuilder) {
        b.legs(0.5f, 0.15f, 0.18f, -0.15f)
        b.torso(0f, 0.5f, 1.2f, 0.5f, 0.5f)
        b.part(0.65f, 0f, 0.85f, 0.26f, 0.26f, 0.4f, b.body)
        b.head(0.92f, 1.15f, 0.48f, 0.3f, 0.26f)
        b.part(0.9f, -0.09f, 1.4f, 0.28f, 0.06f, 0.22f, b.detail); b.part(0.9f, 0.09f, 1.4f, 0.28f, 0.06f, 0.22f, b.detail) // dos crestas
        b.part(0.72f, 0f, 0.95f, 0.08f, 0.8f, 0.34f, b.detail)          // gorguera
        b.arms(0.4f, 0.55f, 0.28f)
        b.tail(-0.6f, 0.6f, 0.9f, 0.16f, b.body)
    }

    private fun ceratosaurus(b: DinoBuilder) {
        b.legs(0.55f, 0.18f, 0.2f, -0.15f)
        b.torso(0f, 0.55f, 1.3f, 0.55f, 0.55f)
        b.part(0.7f, 0f, 0.9f, 0.3f, 0.3f, 0.3f, b.body)
        b.head(0.98f, 0.95f, 0.5f, 0.34f, 0.36f)
        b.part(1.15f, 0f, 1.3f, 0.12f, 0.1f, 0.22f, b.detail)          // cuerno nasal
        b.part(-0.1f, 0f, 1.1f, 1.0f, 0.08f, 0.08f, b.detail)          // osteodermos
        b.arms(0.4f, 0.6f, 0.3f)
        b.tail(-0.65f, 0.65f, 1.0f, 0.2f, b.body)
    }

    private fun carnotaurus(b: DinoBuilder) {
        b.legs(0.6f, 0.18f, 0.2f, -0.15f)
        b.torso(0f, 0.6f, 1.3f, 0.55f, 0.6f)
        b.part(0.7f, 0f, 1.0f, 0.3f, 0.3f, 0.35f, b.body)
        b.head(1.0f, 1.1f, 0.5f, 0.38f, 0.4f)
        b.part(1.05f, -0.16f, 1.5f, 0.12f, 0.1f, 0.2f, b.detail); b.part(1.05f, 0.16f, 1.5f, 0.12f, 0.1f, 0.2f, b.detail) // cuernos frontales
        b.arms(0.45f, 0.7f, 0.3f, 0.12f)
        b.tail(-0.65f, 0.7f, 1.0f, 0.22f, b.body)
    }

    private fun allosaurus(b: DinoBuilder) {
        b.legs(0.8f, 0.24f, 0.26f, -0.2f)
        b.torso(0f, 0.8f, 1.8f, 0.8f, 0.8f)
        b.part(1.0f, 0f, 1.3f, 0.4f, 0.4f, 0.4f, b.body)
        b.head(1.35f, 1.35f, 0.7f, 0.45f, 0.45f)
        b.part(1.3f, -0.2f, 1.8f, 0.2f, 0.08f, 0.14f, b.detail); b.part(1.3f, 0.2f, 1.8f, 0.2f, 0.08f, 0.14f, b.detail) // crestas oculares
        b.part(1.65f, 0f, 1.3f, 0.2f, 0.4f, 0.06f, cream)               // dientes
        b.arms(0.6f, 0.9f, 0.42f, 0.3f)
        b.tail(-0.9f, 0.9f, 1.4f, 0.3f, b.body, drop = 0.2f)
    }

    private fun spinosaurus(b: DinoBuilder) {
        b.legs(0.7f, 0.24f, 0.28f, -0.3f)
        b.torso(0f, 0.7f, 1.9f, 0.8f, 0.7f)
        b.part(0.0f, 0f, 1.4f, 1.4f, 0.12f, 0.85f, b.detail)           // vela
        b.part(1.0f, 0f, 1.05f, 0.4f, 0.4f, 0.35f, b.body)
        b.head(1.45f, 1.05f, 0.95f, 0.3f, 0.3f)
        b.part(1.75f, 0f, 1.0f, 0.4f, 0.28f, 0.06f, cream)             // mandíbula
        b.arms(0.55f, 0.8f, 0.42f, 0.35f)
        b.tail(-0.95f, 0.8f, 1.4f, 0.28f, b.body)
    }

    private fun tyrannosaurus(b: DinoBuilder) {
        b.legs(0.9f, 0.3f, 0.3f, -0.25f)
        b.torso(0f, 0.9f, 1.8f, 0.9f, 0.9f)
        b.part(1.0f, 0f, 1.45f, 0.45f, 0.55f, 0.45f, b.body)
        b.head(1.45f, 1.5f, 0.85f, 0.65f, 0.6f)
        b.part(1.75f, 0f, 1.45f, 0.3f, 0.6f, 0.08f, cream)              // dientes
        b.part(1.4f, 0f, 2.1f, 0.5f, 0.3f, 0.1f, b.detail)              // ceja
        b.arms(0.75f, 1.05f, 0.48f, 0.18f)
        b.tail(-0.9f, 1.0f, 1.2f, 0.4f, b.body)
        b.part(-1.9f, 0f, 0.95f, 0.8f, 0.22f, 0.22f, b.body)
    }
}
