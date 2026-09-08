package com.momentadesunt.cretaceouspark.render

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Caja alineada a los ejes del mundo (x, y, z, ancho, fondo, alto). */
class RBox(val x: Float, val y: Float, val z: Float, val w: Float, val d: Float, val h: Float, val color: Int, val alpha: Int = 255) {
    // límites en espacio de vista, columna y orden de autoría (los rellena BoxSorter)
    var vx0 = 0f; var vx1 = 0f; var vy0 = 0f; var vy1 = 0f
    var col = 0
    var author = 0
    val opaque: Boolean get() = alpha >= 255 && h > 0f
}

/** Transformación mundo ↔ vista que necesita el ordenador (la implementa IsoCamera). */
interface ViewMap {
    /** Altura de una unidad z medida en medias alturas de tile (zUnit / tileH). */
    val zScale: Float
    fun toViewX(x: Float, y: Float): Float
    fun toViewY(x: Float, y: Float): Float
    fun viewToWorldX(vx: Float, vy: Float): Float
    fun viewToWorldY(vx: Float, vy: Float): Float
}

/**
 * Cajas de un mismo objeto. Al añadir una caja opaca se le resta el volumen de las opacas ya presentes,
 * de modo que en el grupo nunca hay dos cajas opacas que se crucen: lo que se ve es idéntico (la parte
 * eliminada estaba dentro de otra caja) pero ahora existe un orden de pintado correcto para cualquier par.
 */
class BoxGroup {
    val boxes = ArrayList<RBox>(12)
    private var scratchA = ArrayList<RBox>(8)
    private var scratchB = ArrayList<RBox>(8)

    /** Añade sin recortar (sombras, anillos y cajas translúcidas). */
    fun addRaw(b: RBox) { boxes.add(b) }

    fun add(b: RBox) {
        if (!b.opaque) { boxes.add(b); return }
        var pieces = scratchA; var next = scratchB
        pieces.clear(); pieces.add(b)
        for (a in boxes) {
            if (!a.opaque) continue
            next.clear()
            for (p in pieces) subtract(p, a, next)
            val t = pieces; pieces = next; next = t
            if (pieces.isEmpty()) break
        }
        boxes.addAll(pieces)
        pieces.clear(); next.clear()
    }

    companion object {
        const val EPS = 1e-4f

        fun intersects(a: RBox, b: RBox): Boolean =
            min(a.x + a.w, b.x + b.w) - max(a.x, b.x) > EPS &&
            min(a.y + a.d, b.y + b.d) - max(a.y, b.y) > EPS &&
            min(a.z + a.h, b.z + b.h) - max(a.z, b.z) > EPS

        /** b menos a: hasta seis trozos de b que quedan fuera de a (o b entera si no se cruzan). */
        fun subtract(b: RBox, a: RBox, out: MutableList<RBox>) {
            val bx1 = b.x + b.w; val by1 = b.y + b.d; val bz1 = b.z + b.h
            val ix0 = max(b.x, a.x); val ix1 = min(bx1, a.x + a.w)
            val iy0 = max(b.y, a.y); val iy1 = min(by1, a.y + a.d)
            val iz0 = max(b.z, a.z); val iz1 = min(bz1, a.z + a.h)
            if (ix1 - ix0 <= EPS || iy1 - iy0 <= EPS || iz1 - iz0 <= EPS) { out.add(b); return }
            fun piece(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float) {
                if (w > EPS && d > EPS && h > EPS) out.add(RBox(x, y, z, w, d, h, b.color, b.alpha))
            }
            piece(b.x, b.y, b.z, ix0 - b.x, b.d, b.h)                     // lado x-
            piece(ix1, b.y, b.z, bx1 - ix1, b.d, b.h)                     // lado x+
            piece(ix0, b.y, b.z, ix1 - ix0, iy0 - b.y, b.h)               // lado y-
            piece(ix0, iy1, b.z, ix1 - ix0, by1 - iy1, b.h)               // lado y+
            piece(ix0, iy0, b.z, ix1 - ix0, iy1 - iy0, iz0 - b.z)         // debajo
            piece(ix0, iy0, iz1, ix1 - ix0, iy1 - iy0, bz1 - iz1)         // encima
        }
    }
}

/**
 * Orden de pintor exacto para cajas que no se cruzan:
 *  1. cada caja se parte en trozos contenidos en una sola columna 1×1 de vista;
 *  2. las columnas se pintan por suma de coordenadas de vista (atrás → delante): dos columnas con
 *     distinta suma solo se solapan en pantalla si una está estrictamente delante de la otra;
 *  3. dentro de una columna se aplica un orden topológico con la relación "A detrás de B si hay
 *     separación en x, y o altura con B del lado de la cámara". Empates y ciclos se resuelven por
 *     autoría, así que el resultado no depende de la posición ni del fotograma.
 */
class BoxSorter(private val view: ViewMap) {
    private val k = view.zScale
    private var indeg = IntArray(64)
    private var done = BooleanArray(64)
    private var tmp = arrayOfNulls<RBox>(64)
    private var edges = BooleanArray(64 * 64)

    fun sort(groups: List<BoxGroup>, out: ArrayList<RBox>) {
        out.clear()
        var author = 0
        for (g in groups) for (b0 in g.boxes) {
            author++
            val vxA = view.toViewX(b0.x, b0.y); val vyA = view.toViewY(b0.x, b0.y)
            val vxB = view.toViewX(b0.x + b0.w, b0.y + b0.d); val vyB = view.toViewY(b0.x + b0.w, b0.y + b0.d)
            val vx0 = min(vxA, vxB); val vx1 = max(vxA, vxB); val vy0 = min(vyA, vyB); val vy1 = max(vyA, vyB)
            val cx0 = floor(vx0 + TOL).toInt(); val cx1 = ceil(vx1 - TOL).toInt() - 1
            val cy0 = floor(vy0 + TOL).toInt(); val cy1 = ceil(vy1 - TOL).toInt() - 1
            if (b0.alpha < 255 || (cx1 <= cx0 && cy1 <= cy0)) {
                setBounds(b0, vx0, vx1, vy0, vy1, author); out.add(b0); continue
            }
            for (cx in cx0..max(cx0, cx1)) for (cy in cy0..max(cy0, cy1)) {
                val px0 = max(vx0, cx.toFloat()); val px1 = min(vx1, cx + 1f)
                val py0 = max(vy0, cy.toFloat()); val py1 = min(vy1, cy + 1f)
                if (px1 - px0 < TOL || py1 - py0 < TOL) continue
                val wxA = view.viewToWorldX(px0, py0); val wyA = view.viewToWorldY(px0, py0)
                val wxB = view.viewToWorldX(px1, py1); val wyB = view.viewToWorldY(px1, py1)
                val b = RBox(min(wxA, wxB), min(wyA, wyB), b0.z, abs(wxB - wxA), abs(wyB - wyA), b0.h, b0.color, b0.alpha)
                setBounds(b, px0, px1, py0, py1, author)
                out.add(b)
            }
        }
        out.sortWith(compareBy<RBox>({ it.col }, { it.z }, { it.vx0 + it.vy0 }, { it.author }))
        var start = 0
        while (start < out.size) {
            var end = start + 1
            while (end < out.size && out[end].col == out[start].col) end++
            if (end - start > 1) topo(out, start, end)
            start = end
        }
    }

    private fun setBounds(b: RBox, vx0: Float, vx1: Float, vy0: Float, vy1: Float, author: Int) {
        b.vx0 = vx0; b.vx1 = vx1; b.vy0 = vy0; b.vy1 = vy1
        val cx = floor((vx0 + vx1) / 2f + 1e-3f).toInt(); val cy = floor((vy0 + vy1) / 2f + 1e-3f).toInt()
        b.col = cx + cy
        b.author = author
    }

    /** Orden topológico (Kahn) del tramo [from, to) de `list`, estable respecto al orden previo. */
    private fun topo(list: ArrayList<RBox>, from: Int, to: Int) {
        val n = to - from
        if (indeg.size < n) { indeg = IntArray(n * 2); done = BooleanArray(n * 2); tmp = arrayOfNulls(n * 2) }
        if (edges.size < n * n) edges = BooleanArray(n * n * 2)
        for (i in 0 until n) { indeg[i] = 0; done[i] = false }
        for (i in 0 until n) for (j in 0 until n) {
            val e = i != j && edge(list[from + i], list[from + j])
            edges[i * n + j] = e
            if (e) indeg[j]++
        }
        for (step in 0 until n) {
            var pick = -1
            for (i in 0 until n) if (!done[i] && indeg[i] == 0) { pick = i; break }
            if (pick < 0) for (i in 0 until n) if (!done[i]) { pick = i; break }   // ciclo visual real: rompe por orden previo
            done[pick] = true
            tmp[step] = list[from + pick]
            val row = pick * n
            for (j in 0 until n) if (edges[row + j] && !done[j]) indeg[j]--
        }
        for (i in 0 until n) list[from + i] = tmp[i]!!
    }

    /** Arista a → b: a debe ir antes que b y además sus siluetas se solapan en pantalla. */
    private fun edge(a: RBox, b: RBox): Boolean = before(a, b) && overlaps(a, b, k)

    companion object {
        const val TOL = 1e-3f

        /** a queda detrás de b si hay separación (o contacto) en x, y o altura con b del lado de la cámara. */
        fun behind(a: RBox, b: RBox): Boolean = a.vx1 <= b.vx0 + TOL || a.vy1 <= b.vy0 + TOL || a.z + a.h <= b.z + TOL

        /** a debe pintarse antes que b. Si ambos están "detrás" del otro no se solapan en pantalla. */
        fun before(a: RBox, b: RBox): Boolean = behind(a, b) && !behind(b, a)

        /**
         * Solape estricto de las siluetas en pantalla. La silueta de una caja isométrica es un hexágono cuyos
         * lados siguen las tres direcciones proyectadas de los ejes, así que basta comparar tres intervalos:
         * horizontal (vx − vy), y las dos normales oblicuas (−4·vy + 4k·z) y (4·vx − 4k·z), con k = zUnit / tileH
         * (en medias alturas de tile una unidad z mide 2k, y las normales llevan factor 2).
         */
        fun overlaps(a: RBox, b: RBox, k: Float): Boolean {
            val m = 1e-3f
            val zk = 4f * k
            if (a.vx1 - a.vy0 <= b.vx0 - b.vy1 + m || b.vx1 - b.vy0 <= a.vx0 - a.vy1 + m) return false
            val a1lo = -4f * a.vy1 + zk * a.z; val a1hi = -4f * a.vy0 + zk * (a.z + a.h)
            val b1lo = -4f * b.vy1 + zk * b.z; val b1hi = -4f * b.vy0 + zk * (b.z + b.h)
            if (a1hi <= b1lo + m || b1hi <= a1lo + m) return false
            val a2lo = 4f * a.vx0 - zk * (a.z + a.h); val a2hi = 4f * a.vx1 - zk * a.z
            val b2lo = 4f * b.vx0 - zk * (b.z + b.h); val b2hi = 4f * b.vx1 - zk * b.z
            return !(a2hi <= b2lo + m || b2hi <= a2lo + m)
        }
    }
}
