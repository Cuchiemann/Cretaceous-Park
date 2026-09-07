package com.momentadesunt.cretaceouspark.core

import kotlin.random.Random

object IslandGen {

    fun generate(def: IslandDef, seed: Long): GameState {
        val n = def.size
        val rnd = Random(seed)
        val terrain = IntArray(n * n) { Terrain.GRASS }
        fun idx(x: Int, y: Int) = y * n + x
        fun inb(x: Int, y: Int) = x in 0 until n && y in 0 until n

        // Zona reservada para la entrada (centro inferior)
        val ex = n / 2 - 1
        val ey = n - 2
        fun reserved(x: Int, y: Int) = y >= n - 8 && x >= ex - 4 && x <= ex + 6

        val scale = n / 20f   // 1 en el diseño original de 20x20; ~3 en las islas x10

        // Arena en la costa: franja de 1 a 3 tiles segun el tamano de la isla
        val coast = (1 + scale).toInt().coerceIn(1, 3)
        for (y in 0 until n) for (x in 0 until n) {
            val d = minOf(x, y, n - 1 - x, n - 1 - y)
            val p = when { d == 0 -> 0.6f; d < coast -> 0.35f; d == coast -> 0.15f; else -> 0f } * (def.pctSand / 7f)
            if (rnd.nextFloat() < p) terrain[idx(x, y)] = Terrain.SAND
        }

        fun blobs(type: Int, pct: Int, rMinBase: Int, rMaxBase: Int) {
            val target = n * n * pct / 100
            val rMin = (rMinBase * scale).toInt().coerceAtLeast(1)
            val rMax = (rMaxBase * scale).toInt().coerceAtLeast(rMin)
            var placed = 0
            var tries = 0
            val maxTries = 4000 + n * n
            while (placed < target && tries < maxTries) {
                tries++
                val cx = rnd.nextInt(1, n - 1); val cy = rnd.nextInt(1, n - 1)
                val r = rnd.nextInt(rMin, rMax + 1)
                for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
                    if (!inb(x, y) || reserved(x, y)) continue
                    val dx = x - cx; val dy = y - cy
                    if (dx * dx + dy * dy <= r * r + rnd.nextInt(0, 2)) {
                        val i = idx(x, y)
                        if (terrain[i] == Terrain.GRASS || terrain[i] == Terrain.SAND) { terrain[i] = type; placed++ }
                    }
                }
            }
        }
        blobs(Terrain.WATER, def.pctWater, 1, if (def.pctWater > 20) 3 else 2)
        blobs(Terrain.FOREST, def.pctForest, 1, 2)
        blobs(Terrain.ROCK, def.pctRock, 1, if (def.pctRock > 20) 3 else 1)

        // Limpiar zona de entrada y colocar camino inicial
        for (y in n - 8 until n) for (x in ex - 4..ex + 6) if (inb(x, y)) terrain[idx(x, y)] = Terrain.GRASS
        for (x in ex..ex + 2) terrain[idx(x, ey - 1)] = Terrain.PATH

        val hCount = (n + 1) * n
        val vCount = n * (n + 1)
        val s = GameState(
            island = def.id, size = n, seed = seed, terrain = terrain,
            hType = IntArray(hCount), hHp = IntArray(hCount), hFlags = IntArray(hCount),
            vType = IntArray(vCount), vHp = IntArray(vCount), vFlags = IntArray(vCount),
            money = def.budget.toDouble(),
            nextEvent = def.eventMin + rnd.nextFloat() * (def.eventMax - def.eventMin)
        )
        s.buildings.add(Building(s.newId(), "entrance", ex, ey))
        return s
    }
}
