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
        part(fc, 0f, z + h, lenF * 0.7f, lenL * 0.3f, h * 0.06f, dark)                // franja dorsal
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
        val t1 = thick; val t2 = thick * 0.75f; val t3 = thick * 0.5f
        // centros verticales alineados (cada tramo mas fino se centra en el anterior) y caida suave
        part(fStart - seg * 0.5f, 0f, z, seg, t1, t1, color)
        part(fStart - seg * 1.5f, 0f, z + (t1 - t2) / 2f - drop, seg, t2, t2, color)
        part(fStart - seg * 2.5f, 0f, z + (t1 - t3) / 2f - drop * 2f, seg, t3, t3, detail)
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
        for (i in 0 until count) part(fc - (count - 1) * spacing / 2f + i * spacing, 0f, z, 0.07f, lenL * 0.92f, 0.04f, detail)
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

    /** Cadáver: tumbado, colores apagados y grises. */
    fun buildCorpse(x: Float, y: Float, facing: Int, def: com.momentadesunt.cretaceouspark.core.SpeciesDef, sink: BoxSink) {
        val grey = DinoBuilder.shade(blend(def.colorBody, 0xFF8A8F8A.toInt()), 0.85f)
        val detail = DinoBuilder.shade(blend(def.colorDetail, 0xFF8A8F8A.toInt()), 0.85f)
        val b = DinoBuilder(x, y, facing, scaleOf(def.size), 0f, grey, detail, sink)
        b.part(0f, 0f, 0f, 1.3f, 0.7f, 0.32f, grey)
        b.part(0.85f, 0.1f, 0f, 0.4f, 0.35f, 0.26f, grey)
        b.part(0.95f, 0.3f, 0.08f, 0.07f, 0.03f, 0.07f, 0xFF1B1B1B.toInt())   // ojo cerrado, mira hacia arriba
        b.tail(-0.65f, 0.04f, 0.8f, 0.16f, drop = 0f)
        b.part(-0.1f, 0.5f, 0f, 0.5f, 0.14f, 0.14f, DinoBuilder.shade(grey, 0.8f))   // pata caída
    }

    private fun blend(a: Int, b: Int): Int {
        fun ch(sh: Int) = ((((a shr sh) and 255) + ((b shr sh) and 255)) / 2)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
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
        b.head(0.62f, 1.35f, 0.34f, 0.18f, 0.18f)
        b.part(0.85f, 0f, 1.35f, 0.14f, 0.1f, 0.08f, b.detail)         // pico
        b.arms(0.3f, 0.7f, 0.19f, 0.14f)
        b.tail(-0.45f, 0.7f, 0.85f, 0.12f)
    }

    private fun dryosaurus(b: DinoBuilder) {
        b.legs(0.4f, 0.14f, 0.16f, -0.1f)
        b.torso(0f, 0.4f, 0.85f, 0.42f, 0.42f)
        b.stripes(-0.1f, 0.82f, 0.36f, 4, 0.16f)
        b.part(0.45f, 0f, 0.6f, 0.18f, 0.18f, 0.3f, b.body)
        b.head(0.6f, 0.9f, 0.3f, 0.22f, 0.22f)
        b.arms(0.3f, 0.5f, 0.21f, 0.15f)
        b.tail(-0.42f, 0.5f, 0.65f, 0.14f)
    }

    private fun parasaurolophus(b: DinoBuilder) {
        b.legs(0.5f, 0.18f, 0.24f, -0.45f, 0.35f)
        b.torso(0f, 0.5f, 1.3f, 0.6f, 0.6f)
        b.spots(0f, 0.8f, 0.6f, 5, 0.22f)
        b.part(0.75f, 0f, 0.9f, 0.3f, 0.3f, 0.45f, b.body)            // cuello
        b.head(0.95f, 1.35f, 0.4f, 0.26f, 0.26f)
        b.part(0.62f, 0f, 1.5f, 0.55f, 0.1f, 0.1f, b.detail)           // cresta hacia atrás
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
        b.head(1.05f, 0.55f, 0.55f, 0.55f, 0.45f)                                                  // cráneo 0,775..1,325
        b.part(0.71f, 0f, 0.72f, 0.12f, 0.95f, 0.6f, b.detail)                                    // gola, pegada detrás del cráneo
        b.part(0.71f, 0f, 1.32f, 0.12f, 0.6f, 0.08f, DinoBuilder.shade(b.detail, 0.85f))          // borde de la gola
        b.part(1.15f, -0.17f, 1.0f, 0.4f, 0.08f, 0.08f, horn); b.part(1.15f, 0.17f, 1.0f, 0.4f, 0.08f, 0.08f, horn)   // cuernos apoyados en lo alto del cráneo
        b.part(1.26f, 0f, 1.0f, 0.12f, 0.08f, 0.16f, horn)                                        // cuerno nasal sobre el hocico
        b.part(1.39f, 0f, 0.55f, 0.13f, 0.2f, 0.1f, b.jaw)                                        // pico, por delante de la mandíbula
        b.tail(-0.75f, 0.6f, 0.65f, 0.2f)
    }

    private fun brachiosaurus(b: DinoBuilder) {
        b.legs(0.9f, 0.24f, 0.3f, -0.5f, 0.45f)   // patas a ras del vientre
        b.torso(0f, 0.9f, 1.6f, 0.8f, 0.8f)
        b.part(0.7f, 0f, 1.6f, 0.34f, 0.34f, 0.65f, b.body)
        b.part(0.85f, 0f, 2.25f, 0.3f, 0.3f, 0.65f, b.body)
        b.part(1.0f, 0f, 2.9f, 0.26f, 0.26f, 0.55f, b.body)
        b.part(0.7f, 0f, 1.6f, 0.2f, 0.2f, 0.65f, b.belly); b.part(0.85f, 0f, 2.25f, 0.18f, 0.18f, 0.65f, b.belly)
        b.head(1.15f, 3.45f, 0.42f, 0.26f, 0.26f)
        b.part(1.05f, 0f, 3.71f, 0.16f, 0.16f, 0.1f, b.detail)          // bulto nasal
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
        b.head(0.5f, 0.67f, 0.26f, 0.14f, 0.14f)
        b.arms(0.2f, 0.32f, 0.13f, 0.1f)
        b.tail(-0.3f, 0.4f, 0.75f, 0.08f)
    }

    private fun velociraptor(b: DinoBuilder) {
        b.legs(0.42f, 0.11f, 0.13f, -0.1f)
        b.torso(0f, 0.42f, 0.95f, 0.32f, 0.32f)
        b.stripes(-0.05f, 0.74f, 0.3f, 5, 0.14f)                          // franjas sobre el lomo
        b.part(0.52f, 0f, 0.6f, 0.2f, 0.18f, 0.3f, b.body)
        b.head(0.8f, 0.9f, 0.45f, 0.2f, 0.18f)
        b.part(1.0f, 0f, 0.86f, 0.16f, 0.16f, 0.04f, cream)             // dientes bajo la mandibula
        b.part(0.6f, 0f, 1.08f, 0.25f, 0.06f, 0.08f, b.detail)          // cresta de plumas
        b.arms(0.35f, 0.45f, 0.16f, 0.22f)
        b.tail(-0.47f, 0.55f, 1.05f, 0.1f, drop = 0f)
    }

    /** Dilophosaurus: esbelto y erguido, gris pardo con garganta y vientre naranja, hocico claro, parche rojo en el ojo y doble cresta roja de puntas oscuras. */
    private fun dilophosaurus(b: DinoBuilder) {
        val red = b.detail
        val orange = 0xFFE39A3C.toInt()
        val face = 0xFFEADDC6.toInt()
        val eye = 0xFF1B1B1B.toInt()
        b.legs(0.7f, 0.14f, 0.18f, -0.1f)
        // cuerpo fino con vientre naranja y plumón oscuro en el lomo
        b.part(0f, 0f, 0.7f, 1.1f, 0.44f, 0.45f, b.body)
        b.part(0.1f, 0f, 0.62f, 0.85f, 0.36f, 0.08f, orange)
        b.part(0f, 0f, 1.15f, 0.8f, 0.14f, 0.05f, b.dark)
        for (i in 0 until 4) b.part(-0.35f + i * 0.25f, 0f, 1.2f, 0.04f, 0.2f, 0.06f, b.dark)
        // cuello en S: dos tramos que suben hacia delante, garganta naranja a ras del frente y crin oscura en la nuca
        b.part(0.65f, 0f, 0.85f, 0.26f, 0.26f, 0.4f, b.body)
        b.part(0.9f, 0f, 1.1f, 0.22f, 0.22f, 0.4f, b.body)
        b.part(0.79f, 0f, 0.9f, 0.02f, 0.2f, 0.3f, orange)
        b.part(1.02f, 0f, 1.13f, 0.02f, 0.16f, 0.26f, orange)
        b.part(0.6f, 0f, 1.25f, 0.14f, 0.16f, 0.14f, b.dark)
        b.part(0.88f, 0f, 1.5f, 0.1f, 0.14f, 0.12f, b.dark)
        // cabeza: cráneo oscuro detrás, hocico claro delante, mandíbula clara
        b.part(1.175f, 0f, 1.49f, 0.25f, 0.26f, 0.2f, b.body)
        b.part(1.45f, 0f, 1.49f, 0.3f, 0.24f, 0.17f, face)
        b.part(1.3f, 0f, 1.4f, 0.5f, 0.22f, 0.09f, DinoBuilder.shade(face, 0.9f))
        b.part(1.57f, 0f, 1.38f, 0.14f, 0.23f, 0.02f, cream)                                  // dientes
        for (sg in floatArrayOf(-1f, 1f)) {
            b.part(1.2f, sg * (0.13f + 0.015f), 1.53f, 0.12f, 0.03f, 0.12f, red)              // parche rojo del ojo, a ras
            b.part(1.2f, sg * (0.16f + 0.01f), 1.56f, 0.06f, 0.02f, 0.06f, eye)               // ojo sobre el parche
            // doble cresta: tramo trasero alto con punta oscura y tramo delantero más bajo
            b.part(0.98f, sg * 0.07f, 1.69f, 0.14f, 0.05f, 0.3f, red)
            b.part(0.98f, sg * 0.07f, 1.99f, 0.14f, 0.05f, 0.06f, b.dark)
            b.part(1.2f, sg * 0.07f, 1.69f, 0.3f, 0.05f, 0.18f, red)
        }
        b.arms(0.35f, 0.62f, 0.22f, 0.22f)
        // cola larga y fina que sube, con mechón oscuro
        b.part(-0.85f, 0f, 0.75f, 0.6f, 0.16f, 0.16f, b.body)
        b.part(-1.4f, 0f, 0.82f, 0.5f, 0.11f, 0.11f, b.body)
        b.part(-1.85f, 0f, 0.9f, 0.4f, 0.07f, 0.07f, b.body)
        b.part(-2.12f, 0f, 0.9f, 0.12f, 0.09f, 0.12f, b.dark)
    }

    private fun ceratosaurus(b: DinoBuilder) {
        b.legs(0.55f, 0.18f, 0.2f, -0.15f)
        b.torso(0f, 0.55f, 1.3f, 0.55f, 0.55f)
        b.part(0.7f, 0f, 0.9f, 0.3f, 0.3f, 0.3f, b.body)
        b.head(0.98f, 1.2f, 0.5f, 0.34f, 0.36f)
        b.part(1.15f, 0f, 1.56f, 0.12f, 0.1f, 0.22f, b.detail)          // cuerno nasal
        b.part(1.0f, -0.14f, 1.56f, 0.08f, 0.06f, 0.1f, b.detail); b.part(1.0f, 0.14f, 1.56f, 0.08f, 0.06f, 0.1f, b.detail)   // cuernos oculares sobre el craneo
        for (i in 0 until 5) b.part(-0.5f + i * 0.25f, 0f, 1.1f, 0.08f, 0.08f, 0.08f, b.detail)   // osteodermos
        b.arms(0.4f, 0.6f, 0.275f)
        b.tail(-0.65f, 0.65f, 1.05f, 0.2f)
    }

    private fun carnotaurus(b: DinoBuilder) {
        b.legs(0.6f, 0.18f, 0.2f, -0.15f)
        b.torso(0f, 0.6f, 1.3f, 0.55f, 0.6f)
        b.part(0.7f, 0f, 1.0f, 0.3f, 0.3f, 0.35f, b.body)
        b.head(1.0f, 1.35f, 0.5f, 0.38f, 0.4f)
        b.part(1.05f, -0.16f, 1.75f, 0.12f, 0.1f, 0.2f, b.detail); b.part(1.05f, 0.16f, 1.75f, 0.12f, 0.1f, 0.2f, b.detail) // cuernos frontales
        b.spots(-0.1f, 0.9f, 0.55f, 4, 0.25f)
        b.arms(0.45f, 0.7f, 0.275f, 0.1f)
        b.tail(-0.65f, 0.7f, 1.05f, 0.22f)
    }

    private fun allosaurus(b: DinoBuilder) {
        b.legs(0.8f, 0.24f, 0.26f, -0.2f)
        b.torso(0f, 0.8f, 1.8f, 0.8f, 0.8f)
        b.part(1.0f, 0f, 1.3f, 0.4f, 0.4f, 0.4f, b.body)
        b.head(1.35f, 1.7f, 0.7f, 0.45f, 0.45f)
        b.part(1.3f, -0.18f, 2.15f, 0.2f, 0.08f, 0.14f, b.detail); b.part(1.3f, 0.18f, 2.15f, 0.2f, 0.08f, 0.14f, b.detail) // crestas oculares sobre el craneo
        b.part(1.66f, 0f, 1.66f, 0.18f, 0.4f, 0.04f, cream)              // dientes bajo la mandibula
        b.stripes(-0.2f, 1.6f, 0.7f, 4, 0.3f)
        b.arms(0.6f, 0.9f, 0.4f, 0.3f)
        b.tail(-0.9f, 0.9f, 1.5f, 0.3f, drop = 0.1f)
    }

    /** Spinosaurus: cuerpo largo y bajo, vela alta oscura con anillos ocre, hocico de cocodrilo blanco y azul, cola-remo alta. */
    private fun spinosaurus(b: DinoBuilder) {
        val night = 0xFF221410.toInt()      // vela y franjas casi negras
        val ochre = b.detail
        val blue = 0xFF4E56D6.toInt()
        b.legs(0.55f, 0.26f, 0.28f, -0.35f)
        // cuerpo largo y bajo con pecho ocre
        b.torso(0f, 0.55f, 2.0f, 0.8f, 0.65f)
        b.part(0.5f, 0f, 0.46f, 0.9f, 0.72f, 0.1f, ochre)                                   // pecho y vientre ocre
        for (sg in floatArrayOf(-1f, 1f)) {                                                   // franjas oscuras a lo largo de los flancos
            b.part(-0.15f, sg * 0.41f, 0.98f, 1.5f, 0.02f, 0.06f, night)
            b.part(0.15f, sg * 0.41f, 0.78f, 1.1f, 0.02f, 0.05f, night)
        }
        // vela: bloque alto casi negro con dos jorobas y anillos ocre en ambas caras
        val sz = 1.2f
        b.part(-0.1f, 0f, sz, 1.5f, 0.12f, 0.95f, night)
        b.part(0.2f, 0f, sz + 0.95f, 0.45f, 0.1f, 0.12f, night)
        b.part(-0.45f, 0f, sz + 0.95f, 0.4f, 0.1f, 0.08f, night)
        for (sg in floatArrayOf(-1f, 1f)) {
            for (fc in floatArrayOf(0.25f, -0.4f)) {
                b.part(fc, sg * 0.075f, sz + 0.3f, 0.48f, 0.03f, 0.48f, ochre)                // anillo exterior
                b.part(fc, sg * 0.1f, sz + 0.44f, 0.2f, 0.02f, 0.2f, night)                  // centro oscuro del anillo
            }
            for (i in 0 until 4) b.part(-0.7f + i * 0.4f, sg * 0.075f, sz + 0.08f, 0.08f, 0.03f, 0.08f, ochre)   // motas en la base
        }
        // cuello bajo y adelantado en dos tramos
        b.part(1.2f, 0f, 0.75f, 0.4f, 0.5f, 0.45f, b.body)
        b.part(1.55f, 0f, 0.9f, 0.3f, 0.4f, 0.36f, b.body)
        b.part(1.55f, 0f, 1.26f, 0.22f, 0.12f, 0.16f, night)                                  // penacho oscuro de la nuca
        // cabeza: cráneo largo y estrecho, punta blanca, mandíbula azul, mancha azul sobre el hocico
        b.part(2.0f, 0f, 1.09f, 0.6f, 0.28f, 0.21f, b.body)
        b.part(2.45f, 0f, 1.09f, 0.3f, 0.28f, 0.21f, cream)
        b.part(2.15f, 0f, 1.0f, 0.9f, 0.26f, 0.09f, blue)                                     // mandíbula azul
        b.part(2.1f, 0f, 1.3f, 0.3f, 0.18f, 0.04f, blue)                                      // mancha azul sobre el hocico
        b.part(2.5f, 0f, 0.97f, 0.24f, 0.27f, 0.03f, cream)                                   // dientes bajo la mandíbula
        b.part(1.8f, -(0.14f + 0.015f), 1.2f, 0.06f, 0.03f, 0.06f, 0xFF1B1B1B.toInt())        // ojos a ras del costado
        b.part(1.8f, 0.14f + 0.015f, 1.2f, 0.06f, 0.03f, 0.06f, 0xFF1B1B1B.toInt())
        b.arms(0.7f, 0.5f, 0.4f, 0.4f)
        // cola-remo: alta y estrecha, se afina hacia atrás, punta oscura y franjas laterales
        val segs = arrayOf(floatArrayOf(-1.3f, 0.6f, 0.3f, 0.55f), floatArrayOf(-1.9f, 0.62f, 0.2f, 0.6f), floatArrayOf(-2.5f, 0.7f, 0.12f, 0.55f))
        for (sg2 in segs) {
            val (fc, z, w, h) = sg2
            b.part(fc, 0f, z, 0.6f, w, h, b.body)
            for (sg in floatArrayOf(-1f, 1f)) {
                b.part(fc, sg * (w / 2f + 0.01f), z + h * 0.3f, 0.5f, 0.02f, 0.05f, night)
                b.part(fc, sg * (w / 2f + 0.01f), z + h * 0.65f, 0.4f, 0.02f, 0.05f, night)
            }
        }
        b.part(-2.88f, 0f, 0.75f, 0.16f, 0.1f, 0.45f, night)                                  // punta de la cola
    }

    private fun tyrannosaurus(b: DinoBuilder) {
        b.legs(0.9f, 0.3f, 0.3f, -0.25f)
        b.torso(0f, 0.9f, 1.8f, 0.9f, 0.9f)
        b.part(1.0f, 0f, 1.45f, 0.45f, 0.55f, 0.45f, b.body)
        b.head(1.45f, 1.9f, 0.85f, 0.65f, 0.6f)
        b.part(1.8f, 0f, 1.86f, 0.24f, 0.58f, 0.04f, cream)             // dientes bajo la mandibula
        b.part(1.4f, 0f, 2.5f, 0.5f, 0.3f, 0.1f, b.detail)              // ceja
        b.part(1.2f, -0.345f, 2.15f, 0.2f, 0.04f, 0.15f, b.dark); b.part(1.2f, 0.345f, 2.15f, 0.2f, 0.04f, 0.15f, b.dark)   // pomulos, a ras
        b.arms(0.75f, 1.05f, 0.45f, 0.18f)
        b.tail(-0.9f, 1.0f, 1.9f, 0.42f, drop = 0.1f)
    }
}
