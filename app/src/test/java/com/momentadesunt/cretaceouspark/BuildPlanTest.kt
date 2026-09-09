package com.momentadesunt.cretaceouspark

import com.momentadesunt.cretaceouspark.core.*
import org.junit.Assert.*
import org.junit.Test

/** Operaciones por lotes del nuevo sistema de construcción: planos de vallas/caminos y pinceles. */
class BuildPlanTest {

    private fun sandbox(): World {
        val s = IslandGen.generate(GameData.islands.first { it.sandbox }, 3L)
        val w = World(s)
        // zona de pruebas plana: 12x12 de hierba lejos de la entrada
        for (y in 2..13) for (x in 2..13) { s.terrain[s.idx(x, y)] = Terrain.GRASS; s.height[s.idx(x, y)] = 0 }
        w.grid.rebuildAll()
        return w
    }

    /** Contorno de vallas de un rectángulo de vértices (x0,y0)-(x1,y1) como claves de borde. */
    private fun outline(x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        val keys = ArrayList<Int>()
        for (x in x0 until x1) { keys.add(EdgeRef(true, x, y0).key()); keys.add(EdgeRef(true, x, y1).key()) }
        for (y in y0 until y1) { keys.add(EdgeRef(false, x0, y).key()); keys.add(EdgeRef(false, x1, y).key()) }
        return keys.toIntArray()
    }

    @Test
    fun fencePlanConfirmsAndClosesEnclosure() {
        val w = sandbox()
        val money = w.s.money
        val placed = w.placeFences(outline(3, 3, 9, 9), Fence.LIGHT)
        assertEquals(24, placed)
        assertEquals("", w.lastFenceFail)
        assertEquals(1, w.enclosures().size)
        assertEquals(36, w.enclosures()[0].tiles)
        assertEquals(money - 24 * Fence.cost[Fence.LIGHT], w.s.money, 0.01)
        // repetir el mismo plano: nada nuevo y sin motivo de fallo (ya hay esa valla)
        assertEquals(0, w.placeFences(outline(3, 3, 9, 9), Fence.LIGHT))
        assertEquals("", w.lastFenceFail)
    }

    @Test
    fun pathPlanPlacesOnlyValidTiles() {
        val w = sandbox()
        val s = w.s
        w.placeFences(outline(3, 3, 9, 9), Fence.LIGHT)
        // fila y=11: fuera del recinto y sin vallas pegadas -> válidos; fila y=5 dentro del recinto -> inválidos
        val tiles = (2..12).map { s.idx(it, 11) } + (4..7).map { s.idx(it, 5) }
        val placed = w.placePaths(tiles.toIntArray())
        assertEquals(11, placed)
        assertTrue(w.lastPathFail, w.lastPathFail.contains("recinto"))
        for (x in 2..12) assertEquals(Terrain.PATH, s.terrainAt(x, 11))
        assertEquals(Terrain.GRASS, s.terrainAt(5, 5))
    }

    @Test
    fun terrainBrushChangesManyTilesAtOnce() {
        val w = sandbox()
        val s = w.s
        val cells = (5..7).flatMap { y -> (5..7).map { x -> s.idx(x, y) } }.toIntArray()
        assertEquals(9, w.terraformMany(TerrainTool.PLANT_FOREST, cells))
        for (i in cells) assertEquals(Terrain.FOREST, s.terrain[i])
        // segunda pasada: ya es bosque, nada cambia
        assertEquals(0, w.terraformMany(TerrainTool.PLANT_FOREST, cells))
        assertEquals(9, w.terraformMany(TerrainTool.CLEAR_FOREST, cells))
    }

    @Test
    fun demolishBrushRemovesPathsAndFencesButKeepsBuildings() {
        val w = sandbox()
        val s = w.s
        w.placeFences(outline(3, 3, 9, 9), Fence.LIGHT)
        assertTrue(w.placeBuilding("feeder_herb", 4, 4).ok)
        assertEquals(3, w.placePaths(intArrayOf(s.idx(11, 4), s.idx(11, 5), s.idx(11, 6))))
        val buildings = s.buildings.size
        // pincel sobre la esquina del recinto, el comedero y el camino
        val cells = (3..11).flatMap { x -> (3..6).map { y -> s.idx(x, y) } }.toIntArray()
        val removed = w.demolishBrush(cells)
        assertTrue("quitados $removed", removed > 0)
        assertEquals(buildings, s.buildings.size)              // el edificio sigue
        assertEquals(Terrain.GRASS, s.terrainAt(11, 5))         // camino quitado
        assertEquals(0, w.grid.fenceType(EdgeRef(true, 3, 3)))  // valla quitada
        assertEquals(0, w.enclosures().size)                    // recinto ya no está cerrado
        assertEquals(0, w.demolishBrush(cells))
    }
}
