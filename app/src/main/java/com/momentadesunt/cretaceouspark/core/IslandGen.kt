package com.momentadesunt.cretaceouspark.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Ruido de gradiente 2D (estilo Perlin) con varias octavas. Determinista a partir de la semilla,
 * sin dependencias de Android para que el generador corra en los tests de JVM.
 */
class Noise(seed: Long) {
    private val perm = IntArray(512)
    init {
        val p = IntArray(256) { it }
        val r = Random(seed)
        for (i in 255 downTo 1) { val j = r.nextInt(i + 1); val t = p[i]; p[i] = p[j]; p[j] = t }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }
    private fun fade(t: Float) = t * t * t * (t * (t * 6f - 15f) + 10f)
    private fun grad(h: Int, x: Float, y: Float): Float = when (h and 7) {
        0 -> x + y; 1 -> -x + y; 2 -> x - y; 3 -> -x - y
        4 -> x; 5 -> -x; 6 -> y; else -> -y
    }
    /** Valor en [-1, 1] aproximadamente. */
    fun at(x: Float, y: Float): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val xf = x - xi; val yf = y - yi
        val u = fade(xf); val v = fade(yf)
        val a = xi and 255; val b = yi and 255
        val aa = perm[perm[a] + b]; val ab = perm[perm[a] + b + 1]
        val ba = perm[perm[a + 1] + b]; val bb = perm[perm[a + 1] + b + 1]
        val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1f, yf), u)
        val x2 = lerp(grad(ab, xf, yf - 1f), grad(bb, xf - 1f, yf - 1f), u)
        return lerp(x1, x2, v) * 1.41f
    }
    /** Suma de octavas (fBm): wavelength = tamaño en tiles del rasgo más grande. */
    fun fbm(x: Float, y: Float, wavelength: Float, octaves: Int, gain: Float = 0.5f): Float {
        var sum = 0f; var amp = 1f; var norm = 0f; var f = 1f / wavelength
        for (o in 0 until octaves) { sum += amp * at(x * f + o * 17.3f, y * f + o * 11.7f); norm += amp; amp *= gain; f *= 2f }
        return sum / norm
    }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/**
 * Generación procedural de la isla: un campo de altura continuo (fBm) decide lagos y niveles de relieve,
 * un campo de humedad reparte el bosque y la roca se concentra en las cotas altas y los cortados.
 * Los porcentajes de la isla se respetan por cuantiles, así que cada isla conserva su carácter.
 */
object IslandGen {

    fun generate(def: IslandDef, seed: Long): GameState {
        val n = def.size
        val rnd = Random(seed)
        val terrain = IntArray(n * n) { Terrain.GRASS }
        val height = IntArray(n * n)
        fun idx(x: Int, y: Int) = y * n + x
        fun inb(x: Int, y: Int) = x in 0 until n && y in 0 until n

        // Zona reservada para la entrada (centro inferior): llana, nivel 0 y sin obstáculos
        val ex = n / 2 - 1
        val ey = n - 2
        val rx0 = ex - 4; val rx1 = ex + 6; val ry0 = n - 8
        fun reserved(x: Int, y: Int) = y >= ry0 && x >= rx0 && x <= rx1
        /** 0 dentro de la zona reservada, 1 a partir de 10 tiles: suaviza el relieve alrededor del muelle. */
        fun entranceRamp(x: Int, y: Int): Float {
            val dx = max(0, max(rx0 - x, x - rx1)); val dy = max(0, ry0 - y)
            val d = sqrt((dx * dx + dy * dy).toFloat())
            val t = (d / 10f).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        val nH = Noise(seed)
        val nD = Noise(seed xor 0x5DEECE66DL)
        val nM = Noise(seed xor 0x2545F491L)
        val nR = Noise(seed xor 0x7A3B9C1DL)
        val nP = Noise(seed xor 0x3C6EF372L)
        // Mesetas: un escalón duro en el campo de relieve que produce cortados de uno o dos niveles
        val plateau = when (def.relief) { 1 -> 0.35f; 2 -> 0.65f; else -> 0.9f }

        // ---- campo base: lagos en las zonas más bajas -------------------------------------------------------
        val base = FloatArray(n * n)
        val relief = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val fx = x.toFloat(); val fy = y.toFloat()
            val b = nH.fbm(fx, fy, 22f, 4)
            base[idx(x, y)] = b
            // relieve: el campo base (colinas amplias) más detalle fino y crestas que cortan la pendiente
            val detail = nD.fbm(fx, fy, 7f, 2)
            val ridge = 1f - abs(nR.fbm(fx, fy, 15f, 2))
            val pm = ((nP.fbm(fx, fy, 19f, 2) - 0.12f) / 0.08f).coerceIn(0f, 1f)   // borde de meseta de ~1 tile
            relief[idx(x, y)] = (b * 0.9f + detail * 0.35f + ridge * 0.45f + pm * plateau) * entranceRamp(x, y)
        }
        fun quantile(values: FloatArray, mask: (Int) -> Boolean, frac: Float): Float {
            val list = ArrayList<Float>(values.size)
            for (i in values.indices) if (mask(i)) list.add(values[i])
            if (list.isEmpty()) return Float.MAX_VALUE
            list.sort()
            return list[(frac * (list.size - 1)).toInt().coerceIn(0, list.size - 1)]
        }

        // Agua: el pctWater % más bajo del campo base (nunca en la zona de la entrada)
        val qWater = quantile(base, { !reserved(it % n, it / n) }, def.pctWater / 100f)
        for (i in terrain.indices) if (base[i] < qWater && !reserved(i % n, i / n)) terrain[i] = Terrain.WATER
        // charcos de un solo tile: se rellenan
        for (i in terrain.indices) if (terrain[i] == Terrain.WATER) {
            val x = i % n; val y = i / n
            var w = 0
            for ((dx, dy) in dirs4) if (inb(x + dx, y + dy) && terrain[idx(x + dx, y + dy)] == Terrain.WATER) w++
            if (w == 0) terrain[i] = Terrain.GRASS
        }

        // Distancia (Chebyshev, hasta 3) a la orilla más cercana
        val distWater = IntArray(n * n) { 9 }
        for (i in terrain.indices) if (terrain[i] == Terrain.WATER) distWater[i] = 0
        for (pass in 1..3) for (y in 0 until n) for (x in 0 until n) {
            val i = idx(x, y)
            if (distWater[i] < 9) continue
            var near = false
            for (dy in -1..1) for (dx in -1..1) if (inb(x + dx, y + dy) && distWater[idx(x + dx, y + dy)] == pass - 1) near = true
            if (near) distWater[i] = pass
        }

        // ---- niveles de relieve --------------------------------------------------------------------------
        // Fracción de tierra en cada nivel según el relieve de la isla (1 suave, 3 abrupto)
        val high = when (def.relief) { 1 -> floatArrayOf(0.62f, 0.26f, 0.09f, 0.03f); 2 -> floatArrayOf(0.48f, 0.28f, 0.16f, 0.08f); else -> floatArrayOf(0.38f, 0.28f, 0.20f, 0.14f) }
        val land: (Int) -> Boolean = { terrain[it] != Terrain.WATER }
        val q1 = quantile(relief, land, high[0])
        val q2 = quantile(relief, land, high[0] + high[1])
        val q3 = quantile(relief, land, high[0] + high[1] + high[2])
        for (i in terrain.indices) {
            if (terrain[i] == Terrain.WATER) { height[i] = 0; continue }
            val r = relief[i]
            var lv = when { r >= q3 -> 3; r >= q2 -> 2; r >= q1 -> 1; else -> 0 }
            // orillas suaves: como mucho un nivel por tile de distancia al agua
            lv = min(lv, distWater[i])
            if (reserved(i % n, i / n)) lv = 0
            height[i] = lv
        }
        // Se eliminan los picos y hoyos de un solo tile (no aportan y estorban al construir)
        for (pass in 0 until 2) for (y in 0 until n) for (x in 0 until n) {
            val i = idx(x, y)
            if (terrain[i] == Terrain.WATER) continue
            var lo = 9; var hi = -1; var cnt = 0
            for ((dx, dy) in dirs4) if (inb(x + dx, y + dy) && terrain[idx(x + dx, y + dy)] != Terrain.WATER) { val h = height[idx(x + dx, y + dy)]; lo = min(lo, h); hi = max(hi, h); cnt++ }
            if (cnt == 0) continue
            if (height[i] > hi) height[i] = hi
            if (height[i] < lo) height[i] = lo
        }

        // ---- tipos de terreno sobre la tierra ------------------------------------------------------------
        val steep = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val i = idx(x, y)
            var s = 0
            for ((dx, dy) in dirs4) if (inb(x + dx, y + dy)) s = max(s, abs(height[idx(x + dx, y + dy)] - height[i]))
            steep[i] = s.toFloat()
        }
        val grassLand: (Int) -> Boolean = { terrain[it] == Terrain.GRASS && !reserved(it % n, it / n) }
        /** Convierte el pct % del total de la isla en el umbral que deja esa cantidad de tiles de hierba por encima. */
        fun topThreshold(score: FloatArray, pct: Int): Float {
            var grass = 0
            for (i in terrain.indices) if (grassLand(i)) grass++
            val want = (n * n * pct / 100f).coerceAtMost(grass.toFloat())
            return if (want <= 0f || grass == 0) Float.MAX_VALUE else quantile(score, grassLand, 1f - want / grass)
        }
        // Roca: cotas altas, cortados y un ruido propio
        val rockScore = FloatArray(n * n)
        for (i in terrain.indices) rockScore[i] = height[i] * 0.35f + steep[i] * 0.4f + nR.fbm((i % n).toFloat() + 300f, (i / n).toFloat(), 9f, 3) * 0.8f
        val qRock = topThreshold(rockScore, def.pctRock)
        for (i in terrain.indices) if (grassLand(i) && rockScore[i] >= qRock) terrain[i] = Terrain.ROCK
        // Bosque: humedad (ruido) con bonificación cerca del agua y en las cotas bajas
        val forestScore = FloatArray(n * n)
        for (i in terrain.indices) forestScore[i] = nM.fbm((i % n).toFloat(), (i / n).toFloat(), 12f, 3) + (if (distWater[i] <= 2) 0.25f else 0f) - height[i] * 0.12f
        val qForest = topThreshold(forestScore, def.pctForest)
        for (i in terrain.indices) if (grassLand(i) && forestScore[i] >= qForest) terrain[i] = Terrain.FOREST
        // Arena: orillas de lagos a nivel 0 y franja de la costa exterior
        val pSand = (def.pctSand / 7f).coerceIn(0f, 1f)
        val coast = (1 + n / 20f).toInt().coerceIn(1, 3)
        for (y in 0 until n) for (x in 0 until n) {
            val i = idx(x, y)
            if (terrain[i] != Terrain.GRASS || reserved(x, y) || height[i] > 0) continue
            val d = minOf(x, y, n - 1 - x, n - 1 - y)
            val pCoast = when { d == 0 -> 0.6f; d < coast -> 0.35f; d == coast -> 0.15f; else -> 0f }
            val pShore = when (distWater[i]) { 1 -> 0.85f; 2 -> 0.3f; else -> 0f }
            if (rnd.nextFloat() < max(pCoast, pShore) * pSand) terrain[i] = Terrain.SAND
        }

        // Limpiar zona de entrada y colocar camino inicial
        for (y in n - 8 until n) for (x in ex - 4..ex + 6) if (inb(x, y)) { terrain[idx(x, y)] = Terrain.GRASS; height[idx(x, y)] = 0 }
        for (x in ex..ex + 2) terrain[idx(x, ey - 1)] = Terrain.PATH

        val hCount = (n + 1) * n
        val vCount = n * (n + 1)
        val s = GameState(
            island = def.id, size = n, seed = seed, terrain = terrain,
            hType = IntArray(hCount), hHp = IntArray(hCount), hFlags = IntArray(hCount),
            vType = IntArray(vCount), vHp = IntArray(vCount), vFlags = IntArray(vCount),
            money = def.budget.toDouble(),
            height = height,
            nextEvent = def.eventMin + rnd.nextFloat() * (def.eventMax - def.eventMin)
        )
        s.buildings.add(Building(s.newId(), "entrance", ex, ey))
        return s
    }

    private val dirs4 = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
    private operator fun IntArray.component1() = this[0]
    private operator fun IntArray.component2() = this[1]

    /** Mapa en texto para tests y ajustes: letra = terreno, cifra = nivel de la hierba. */
    fun ascii(s: GameState): String {
        val sb = StringBuilder()
        val n = s.size
        for (y in 0 until n) {
            for (x in 0 until n) {
                val i = s.idx(x, y)
                sb.append(when (s.terrain[i]) {
                    Terrain.WATER -> '~'; Terrain.FOREST -> 'T'; Terrain.ROCK -> '#'; Terrain.SAND -> '.'; Terrain.PATH, Terrain.BRIDGE -> '='
                    else -> ('0' + s.height[i])
                })
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
