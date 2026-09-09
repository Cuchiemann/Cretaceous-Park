package com.momentadesunt.cretaceouspark

import com.momentadesunt.cretaceouspark.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.floor

/** Regla de desnivel: dinos y visitantes cruzan escalones de un bloque, nunca de dos o más. */
class HeightRuleTest {

    private fun flatWorld(): World {
        val s = IslandGen.generate(GameData.islandById.getValue("brote"), 11L)
        val w = World(s)
        for (i in 0 until s.size * s.size) { s.terrain[i] = Terrain.GRASS; s.height[i] = 0 }
        val e = s.buildings.first { it.type == "entrance" }
        for (x in e.x until e.x + e.w) s.terrain[s.idx(x, e.y - 1)] = Terrain.PATH
        w.grid.rebuildAll()
        return w
    }

    @Test
    fun visitorsPathOnlyOverOneStepDifferences() {
        val w = flatWorld(); val s = w.s
        // camino recto de (10,10) a (20,10) con un escalón de 1 y luego uno de 2
        for (x in 10..20) s.terrain[s.idx(x, 10)] = Terrain.PATH
        for (x in 13..20) s.height[s.idx(x, 10)] = 1
        assertEquals("un escalón se cruza", 10, w.grid.pathBetween(s.idx(10, 10), s.idx(20, 10)).size)
        for (x in 17..20) s.height[s.idx(x, 10)] = 3
        assertTrue("dos bloques bloquean", w.grid.pathBetween(s.idx(10, 10), s.idx(20, 10)).isEmpty())
        assertEquals(6, w.grid.pathBetween(s.idx(10, 10), s.idx(16, 10)).size)
        // el BFS rodea el cortado por una rampa lateral a nivel 2 (1 -> 2 -> 3)
        for (x in 16..20) { s.terrain[s.idx(x, 11)] = Terrain.PATH; s.height[s.idx(x, 11)] = 2 }
        val route = w.grid.pathBetween(s.idx(10, 10), s.idx(20, 10))
        assertTrue("con rampa lateral hay ruta", route.isNotEmpty())
        var prev = s.idx(10, 10)
        for (t in route) { assertTrue("paso $prev -> $t", s.stepOk(prev, t)); prev = t }
    }

    @Test
    fun dinoCannotClimbTwoBlockCliffButTakesRamp() {
        val w = flatWorld(); val s = w.s
        // recinto 8x8 en (3..10, 3..10); mitad este a nivel 2 (cortado) salvo una rampa a nivel 1 en (7, 10)
        w.fenceRect(3, 3, 10, 10, Fence.LIGHT)
        val r = w.enclosures().first()
        for (y in 3..10) for (x in 7..10) s.height[s.idx(x, y)] = 2
        val d = w.spawnDino("gallimimus", r.id)!!
        d.x = 4.5f; d.y = 6.5f; d.tx = d.x; d.ty = d.y
        // comedero arriba del cortado: sin rampa no se alcanza
        assertTrue(w.placeBuilding("feeder_herb", 9, 5).ok)
        d.food = 10f
        repeat(300) { w.tick(0.1f); d.food = 10f; d.water = 80f }
        assertTrue("sigue abajo (x=${d.x})", d.x < 7f)
        assertTrue("avisa de que no llega", s.alerts.any { it.kind == "nofeeder" })
        // rampa: (7,10) a nivel 1 permite subir
        s.height[s.idx(7, 10)] = 1
        w.grid.rebuildAll()
        for (dd in s.dinos) dd.path.clear()
        d.state = DinoState.WANDER
        var reached = false
        repeat(600) { w.tick(0.1f); d.food = 10f; d.water = 80f; if (floor(d.x).toInt() >= 7 && s.levelAt(floor(d.x).toInt(), floor(d.y).toInt()) == 2) reached = true }
        assertTrue("sube por la rampa", reached)
    }

    @Test
    fun buildingsNeedFlatGroundAndReliefToolsWork() {
        val w = flatWorld(); val s = w.s
        s.height[s.idx(30, 30)] = 1
        assertEquals("Terreno desnivelado: nivela primero", w.canPlaceBuilding(GameData.building("generator"), 29, 29).reason)
        assertEquals("un tile elevado sí es llano", "Necesita un camino adyacente", w.canPlaceBuilding(GameData.building("toilets"), 30, 30).reason)
        assertTrue(w.terraform(TerrainTool.LOWER, 30, 30).ok)
        assertEquals(0, s.levelAt(30, 30))
        assertFalse("no baja del nivel del mar", w.canTerraform(TerrainTool.LOWER, 30, 30).ok)
        repeat(3) { assertTrue(w.terraform(TerrainTool.RAISE, 30, 30).ok) }
        assertEquals(Terrain.MAX_LEVEL, s.levelAt(30, 30))
        assertFalse("tope de tres bloques", w.canTerraform(TerrainTool.RAISE, 30, 30).ok)
        s.terrain[s.idx(31, 31)] = Terrain.WATER
        assertFalse("no se eleva el agua", w.canTerraform(TerrainTool.RAISE, 31, 31).ok)
        // guardar y cargar conserva el relieve
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val s2 = json.decodeFromString<GameState>(json.encodeToString(GameState.serializer(), s))
        assertEquals(Terrain.MAX_LEVEL, s2.levelAt(30, 30))
        // una partida sin campo de alturas (guardado antiguo) carga plana
        val old = json.encodeToString(GameState.serializer(), s).replace(Regex("\"height\":\\[[^\\]]*\\],"), "")
        val s3 = json.decodeFromString<GameState>(old)
        assertEquals(s.size * s.size, s3.height.size)
        assertEquals(0, s3.levelAt(30, 30))
    }
}
