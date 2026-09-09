package com.momentadesunt.cretaceouspark

import com.momentadesunt.cretaceouspark.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Generación procedural: porcentajes, relieve y una entrada llana y accesible. */
class TerrainGenTest {

    @Test
    fun printBrote() {
        val s = IslandGen.generate(GameData.islandById.getValue("brote"), 42L)
        println(IslandGen.ascii(s))
        println(stats(s))
    }

    @Test
    fun printCeniza() {
        val s = IslandGen.generate(GameData.islandById.getValue("ceniza"), 7L)
        println(IslandGen.ascii(s))
        println(stats(s))
    }

    private fun stats(s: GameState): String {
        val n = s.size; val total = n * n
        val counts = IntArray(7); val levels = IntArray(4)
        var cliffs2 = 0; var edges = 0
        for (i in 0 until total) {
            counts[s.terrain[i]]++
            if (s.terrain[i] != Terrain.WATER) levels[s.height[i]]++
            val x = i % n; val y = i / n
            if (x + 1 < n) { edges++; if (abs(s.height[i] - s.height[i + 1]) >= 2) cliffs2++ }
            if (y + 1 < n) { edges++; if (abs(s.height[i] - s.height[i + n]) >= 2) cliffs2++ }
        }
        return "water=${100 * counts[Terrain.WATER] / total}% forest=${100 * counts[Terrain.FOREST] / total}% rock=${100 * counts[Terrain.ROCK] / total}% sand=${100 * counts[Terrain.SAND] / total}% " +
            "levels=${levels.toList()} cliffs>=2: $cliffs2 de $edges bordes"
    }

    @Test
    fun percentagesAndEntranceHoldForEveryIsland() {
        for (def in GameData.islands) for (seed in listOf(1L, 99L, 12345L)) {
            val s = IslandGen.generate(def, seed)
            val n = s.size; val total = n * n
            var water = 0; var forest = 0; var rock = 0; var maxLevel = 0
            for (i in 0 until total) {
                when (s.terrain[i]) { Terrain.WATER -> water++; Terrain.FOREST -> forest++; Terrain.ROCK -> rock++ }
                assertTrue(s.height[i] in 0..Terrain.MAX_LEVEL)
                if (s.height[i] > maxLevel) maxLevel = s.height[i]
                if (s.terrain[i] == Terrain.WATER) assertEquals("el agua queda a nivel 0", 0, s.height[i])
            }
            val name = "${def.id}/$seed"
            assertTrue("$name agua ${100 * water / total} vs ${def.pctWater}", abs(100 * water / total - def.pctWater) <= 4)
            assertTrue("$name bosque ${100 * forest / total} vs ${def.pctForest}", abs(100 * forest / total - def.pctForest) <= 4)
            assertTrue("$name roca ${100 * rock / total} vs ${def.pctRock}", abs(100 * rock / total - def.pctRock) <= 4)
            assertEquals("$name usa los tres niveles", Terrain.MAX_LEVEL, maxLevel)
            // la entrada y su camino inicial están llanos y a nivel 0
            val e = s.buildings.first { it.type == "entrance" }
            for (y in e.y - 1..e.y + 1) for (x in e.x - 1..e.x + e.w) if (s.inBounds(x, y)) assertEquals("$name entrada llana", 0, s.levelAt(x, y))
            val w = World(s)
            assertEquals(3, w.grid.adjacentPathTiles(e).size)
        }
    }
}
