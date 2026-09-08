package com.momentadesunt.cretaceouspark

import com.momentadesunt.cretaceouspark.core.Dino
import com.momentadesunt.cretaceouspark.core.GameData
import com.momentadesunt.cretaceouspark.render.BoxGroup
import com.momentadesunt.cretaceouspark.render.BoxSorter
import com.momentadesunt.cretaceouspark.render.DinoModels
import com.momentadesunt.cretaceouspark.render.IsoCamera
import com.momentadesunt.cretaceouspark.render.RBox
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Garantía del orden de pintado: para cada especie, orientación, giro de cámara y varias posiciones
 * sub-tile, el modelo no contiene cajas opacas que se crucen y ningún par de trozos que se solape
 * en pantalla queda pintado con el de delante antes que el de detrás.
 */
class RenderOrderTest {

    private val cam = IsoCamera(63).apply { setViewport(2400, 1080); zoom = 0 }

    private fun buildAndSort(speciesId: String, facing: Int, x: Float, y: Float, skin: Int): ArrayList<RBox> {
        val d = Dino(1, speciesId, x, y, x, y).also { it.facing = facing; it.skin = skin }
        val g = BoxGroup()
        DinoModels.build(d) { bx, by, bz, bw, bd, bh, color -> g.add(RBox(bx, by, bz, bw, bd, bh, color)) }
        val out = ArrayList<RBox>()
        BoxSorter(cam).sort(listOf(g), out)
        return out
    }

    /** Hexágono de la silueta de una caja isométrica en pantalla. */
    private fun hexagon(b: RBox): FloatArray {
        val zt = b.z + b.h
        val pts = arrayOf(
            floatArrayOf(b.vx0, b.vy0, zt), floatArrayOf(b.vx1, b.vy0, zt), floatArrayOf(b.vx1, b.vy0, b.z),
            floatArrayOf(b.vx1, b.vy1, b.z), floatArrayOf(b.vx0, b.vy1, b.z), floatArrayOf(b.vx0, b.vy1, zt)
        )
        val out = FloatArray(12)
        for ((i, p) in pts.withIndex()) { out[i * 2] = cam.sx(p[0], p[1]); out[i * 2 + 1] = cam.sy(p[0], p[1], p[2]) }
        return out
    }

    /** Solape estricto de dos polígonos convexos (separación por ejes de las aristas). */
    private fun overlap(a: FloatArray, b: FloatArray): Boolean {
        val margin = 0.5f   // medio píxel: el contacto en un borde no cuenta
        for (poly in arrayOf(a, b)) {
            val n = poly.size / 2
            for (i in 0 until n) {
                val x0 = poly[i * 2]; val y0 = poly[i * 2 + 1]; val x1 = poly[((i + 1) % n) * 2]; val y1 = poly[((i + 1) % n) * 2 + 1]
                val nx = -(y1 - y0); val ny = x1 - x0
                val len = kotlin.math.sqrt(nx * nx + ny * ny); if (len < 1e-6f) continue
                var aMin = Float.MAX_VALUE; var aMax = -Float.MAX_VALUE; var bMin = Float.MAX_VALUE; var bMax = -Float.MAX_VALUE
                for (k in 0 until a.size / 2) { val p = (a[k * 2] * nx + a[k * 2 + 1] * ny) / len; aMin = min(aMin, p); aMax = max(aMax, p) }
                for (k in 0 until b.size / 2) { val p = (b[k * 2] * nx + b[k * 2 + 1] * ny) / len; bMin = min(bMin, p); bMax = max(bMax, p) }
                if (aMax <= bMin + margin || bMax <= aMin + margin) return false
            }
        }
        return true
    }

    private fun desc(b: RBox) = "col=${b.col} a=${b.author} vx=${b.vx0}..${b.vx1} vy=${b.vy0}..${b.vy1} z=${b.z}..${b.z + b.h}"

    private fun check(speciesId: String, facing: Int, rot: Int, x: Float, y: Float, skin: Int): Int {
        cam.rot = rot
        cam.focus(x, y)
        cam.prepare()
        val out = buildAndSort(speciesId, facing, x, y, skin)
        val opaque = out.filter { it.opaque }
        for (i in opaque.indices) for (j in i + 1 until opaque.size) {
            if (BoxGroup.intersects(opaque[i], opaque[j])) fail("$speciesId f$facing r$rot: cajas opacas cruzadas ($i, $j)")
        }
        val hex = opaque.map { hexagon(it) }
        var checked = 0
        for (i in opaque.indices) for (j in i + 1 until opaque.size) {
            if (!overlap(hex[i], hex[j])) continue
            checked++
            // opaque[i] se pinta antes: nunca puede estar estrictamente delante de opaque[j]
            if (BoxSorter.before(opaque[j], opaque[i])) fail("$speciesId f$facing r$rot pos($x,$y): el trozo $j (detrás) se pinta después del $i (delante) :: ${desc(opaque[i])} | ${desc(opaque[j])}")
        }
        return checked
    }

    @Test
    fun everySpeciesPaintsBackToFront() {
        val offsets = listOf(0.0f to 0.0f, 0.37f to 0.61f, 0.5f to 0.5f, 0.93f to 0.08f, 0.25f to 0.75f)
        var pairs = 0
        for (sp in GameData.species) for (facing in 0..3) for (rot in 0..3) for ((ox, oy) in offsets) for (skin in 0..1) {
            pairs += check(sp.id, facing, rot, 30f + ox, 30f + oy, skin)
        }
        assertTrue("se comprobaron pares solapados", pairs > 10_000)
    }

    @Test
    fun subtractionRemovesOverlapAndKeepsOutside() {
        val g = BoxGroup()
        g.add(RBox(0f, 0f, 0f, 1f, 1f, 1f, 1))
        g.add(RBox(0.5f, 0.5f, 0.5f, 1f, 1f, 1f, 2))     // esquina cruzada: quedan 3 trozos fuera
        g.add(RBox(0.2f, 0.2f, 0.2f, 0.1f, 0.1f, 0.1f, 3)) // completamente dentro: desaparece
        val opaque = g.boxes
        assertTrue(opaque.size == 4)
        for (i in opaque.indices) for (j in i + 1 until opaque.size) assertTrue(!BoxGroup.intersects(opaque[i], opaque[j]))
        // el volumen visible de la segunda caja se conserva: 1 - 0.125 = 0.875
        val vol = opaque.filter { it.color == 2 }.sumOf { (it.w * it.d * it.h).toDouble() }
        assertTrue(kotlin.math.abs(vol - 0.875) < 1e-4)
    }
}
