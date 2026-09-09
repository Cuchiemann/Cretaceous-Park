package com.momentadesunt.cretaceouspark

import com.momentadesunt.cretaceouspark.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Simulación sin cabeza: construye un parque básico en Isla Brote y deja correr 20 minutos de juego. */
class HeadlessSimTest {

    private fun buildStarterPark(w: World) {
        val s = w.s
        val n = s.size
        val entrance = s.buildings.first { it.type == "entrance" }
        val px = entrance.x + 1
        // despejar solo los rectangulos que usa el parque de prueba (el terreno depende de la semilla)
        fun clear(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in y0..y1) for (x in x0..x1) if (s.inBounds(x, y)) {
                s.height[s.idx(x, y)] = 0   // el parque de prueba se construye en llano
                when (s.terrainAt(x, y)) {
                    Terrain.FOREST -> w.terraform(TerrainTool.CLEAR_FOREST, x, y)
                    Terrain.ROCK -> w.terraform(TerrainTool.BLAST_ROCK, x, y)
                    Terrain.WATER -> w.terraform(TerrainTool.FILL_WATER, x, y)
                }
            }
        }
        clear(px, 3, px, entrance.y - 2)                 // camino principal
        clear(2, 4, 10, 11)                              // recinto, mirador y camino lateral
        clear(px + 1, entrance.y - 15, px + 3, entrance.y - 3)  // centros
        // camino desde la entrada hacia el norte
        for (y in entrance.y - 2 downTo 3) {
            when (s.terrainAt(px, y)) {
                Terrain.FOREST -> w.terraform(TerrainTool.CLEAR_FOREST, px, y)
                Terrain.ROCK -> w.terraform(TerrainTool.BLAST_ROCK, px, y)
            }
            assertTrue("camino $y", w.placePath(px, y).ok || Terrain.isWalkablePath(s.terrainAt(px, y)))
        }
        // limpiar la zona del recinto (talar / rellenar) a la izquierda del camino
        for (y in 4..11) for (x in 2..8) {
            when (s.terrainAt(x, y)) {
                Terrain.FOREST -> w.terraform(TerrainTool.CLEAR_FOREST, x, y)
                Terrain.ROCK -> w.terraform(TerrainTool.BLAST_ROCK, x, y)
                Terrain.WATER -> w.terraform(TerrainTool.FILL_WATER, x, y)
                Terrain.PATH -> w.removePath(x, y)
            }
        }
        val placed = w.fenceRect(3, 5, 8, 10, Fence.LIGHT)
        assertTrue("tramos $placed", placed >= 20)
        val encl = w.enclosures()
        assertEquals(1, encl.size)
        val r = encl[0]
        assertEquals(36, r.tiles)
        assertTrue(w.placeBuilding("feeder_herb", 4, 6).ok)
        assertTrue(w.placeBuilding("water_trough", 7, 9).ok)
        // camino lateral hasta el recinto y mirador pegado a la valla
        for (x in 9 until px) w.placePath(x, 11)
        // el mirador va fuera del recinto, pegado al borde este (x = 9) a la altura y = 7, con camino en x=10
        for (y in 7..11) w.placePath(10, y)
        assertTrue(w.canPlaceBuilding(GameData.building("viewpoint"), 9, 7).reason, w.placeBuilding("viewpoint", 9, 7).ok)
        // centros al este del camino
        assertTrue(w.placeBuilding("generator", px + 1, entrance.y - 4).reason.ifEmpty { "ok" }, w.placeBuilding("generator", px + 1, entrance.y - 4).ok || w.hasBuilding("generator"))
        w.placeBuilding("shop_food", px + 1, entrance.y - 7)
        w.placeBuilding("expedition_hq", px + 1, entrance.y - 11)
        w.placeBuilding("lab", px + 1, entrance.y - 15)
        // dinos directamente (saltando la incubación)
        repeat(3) { assertNotNull(w.spawnDino("gallimimus", r.id)) }
    }

    @Test
    fun starterParkRunsTwentyMinutes() {
        val s = IslandGen.generate(GameData.islandById.getValue("brote"), 42L)
        val w = World(s)
        buildStarterPark(w)
        val money0 = s.money
        var ticks = 0
        while (w.s.time < 20 * 60f) { w.tick(0.1f); ticks++ }
        assertTrue("dinos vivos", s.dinos.size == 3)
        assertTrue("hay visitantes o ingresos", s.totalIncome > 0)
        assertFalse(s.gameOver)
        println("money0=$money0 money=${s.money} income=${s.totalIncome} expense=${s.totalExpense} visitors=${s.visitors.size} stars=${s.stars} idx=${s.idxProfit}/${s.idxSafety}/${s.idxWelfare}/${s.idxAttraction}")
        println("dinos: " + s.dinos.map { "${it.species} f=${it.food.toInt()} w=${it.water.toInt()} sp=${it.space.toInt()} so=${it.social.toInt()} wb=${it.wellbeing.toInt()} st=${it.stress.toInt()} state=${it.state}" })
        for (d in s.dinos) assertTrue("bienestar ${d.wellbeing}", d.wellbeing > 50f)
        // guardar y cargar
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(s)
        val s2 = json.decodeFromString<GameState>(text)
        val w2 = World(s2)
        w2.afterLoad()
        assertEquals(s.dinos.size, s2.dinos.size)
        assertEquals(w.enclosures().size, w2.enclosures().size)
        val incomeBefore = s2.incomeMin
        while (w2.s.time < 22 * 60f) w2.tick(0.1f)
        assertTrue("la tasa de ingresos no debe dispararse tras cargar (${incomeBefore} -> ${s2.incomeMin})", s2.incomeMin < incomeBefore * 3f + 500f)
    }

    @Test
    fun stressedDinoBreaksFenceAndEscapes() {
        val s = IslandGen.generate(GameData.islandById.getValue("brote"), 7L)
        val w = World(s)
        for (y in 3..6) for (x in 3..6) { if (s.terrainAt(x, y) != Terrain.GRASS) s.terrain[s.idx(x, y)] = Terrain.GRASS; s.height[s.idx(x, y)] = 0 }
        w.grid.rebuildRegions()
        w.fenceRect(3, 3, 6, 6, Fence.LIGHT)
        val r = w.enclosures().first()
        val d = w.spawnDino("velociraptor", r.id)!!
        d.food = 0f; d.water = 0f
        var t = 0f
        while (t < 140f && d.state != DinoState.ESCAPED) { w.tick(0.1f); t += 0.1f; d.food = 0f; d.water = 0f; d.starve = 0f }
        assertEquals("debería haber escapado (estado ${d.state}, estrés ${d.stress})", DinoState.ESCAPED, d.state)
        assertTrue(s.alerts.any { it.kind == "escape" })
    }

    @Test
    fun allIslandsGenerate() {
        for (def in GameData.islands) {
            val s = IslandGen.generate(def, 1L)
            val w = World(s)
            val e = s.buildings.first { it.type == "entrance" }
            assertEquals(3, w.grid.adjacentPathTiles(e).size)
            repeat(600) { w.tick(0.1f) }
        }
    }

    @Test
    fun expeditionDnaAndIncubationChain() {
        val s = IslandGen.generate(GameData.islandById.getValue("brote"), 99L)
        val w = World(s)
        buildStarterPark(w)
        assertTrue(w.hasBuilding("expedition_hq")); assertTrue(w.hasBuilding("lab"))
        val r = w.enclosures().first()
        // expediciones hasta tener ADN >= 50 de alguna especie
        var loops = 0
        while (s.dna.values.none { it >= 50 } && loops < 40) {
            val res = w.startExpedition("canon_rojo"); assertTrue(res.reason, res.ok)
            while (s.expeditions.isNotEmpty()) w.tick(0.1f)
            loops++
        }
        val sp = s.dna.entries.first { it.value >= 50 }.key
        assertTrue(s.fossilLog.isNotEmpty())
        val lab = s.buildings.first { it.type == "lab" }
        assertTrue("lab con energía", lab.powered)
        val before = s.dinos.size
        val inc = w.startIncubation(sp, r.id); assertTrue(inc.reason, inc.ok)
        while (s.incubations.isNotEmpty()) w.tick(0.1f)
        // puede fallar por viabilidad; repetir hasta 5 veces
        var tries = 0
        while (s.dinos.size == before && tries < 5) { w.startIncubation(sp, r.id); while (s.incubations.isNotEmpty()) w.tick(0.1f); tries++ }
        assertTrue("dino incubado", s.dinos.size > before)
        assertTrue(s.cloned.contains(sp))
        assertTrue(s.amberEarned >= 10)
        // investigación
        w.placeBuilding("research_center", 12, 2).let { if (!it.ok) w.placeBuilding("research_center", s.buildings.first { b -> b.type == "lab" }.x, 2) }
        if (w.hasBuilding("research_center")) {
            while (s.researchPoints < 50f) w.tick(0.1f)
            val rr = w.startResearch("B1"); assertTrue(rr.reason, rr.ok)
            while (s.researchActive != null) w.tick(0.1f)
            assertTrue(s.researchDone.contains("B1"))
            assertTrue(w.fenceUnlocked(Fence.MEDIUM).ok)
        }
    }
}

class TutorialTest {
    @Test
    fun tutorialAdvancesWithStarterPark() {
        val s = IslandGen.generate(GameData.islandById.getValue("brote"), 42L)
        s.tutorialActive = true
        val w = World(s)
        assertEquals(0, s.tutorialStep)
        w.cameraMoved = true
        repeat(10) { w.tick(0.1f) }
        assertEquals("paso de camara", 1, s.tutorialStep)
        val entrance = s.buildings.first { it.type == "entrance" }
        val px = entrance.x + 1
        fun clear(x0: Int, y0: Int, x1: Int, y1: Int) { for (y in y0..y1) for (x in x0..x1) if (s.inBounds(x, y)) { s.height[s.idx(x, y)] = 0; when (s.terrainAt(x, y)) {
            Terrain.FOREST -> w.terraform(TerrainTool.CLEAR_FOREST, x, y); Terrain.ROCK -> w.terraform(TerrainTool.BLAST_ROCK, x, y); Terrain.WATER -> w.terraform(TerrainTool.FILL_WATER, x, y) } } }
        clear(px, 3, px, entrance.y - 2); clear(2, 4, 10, 12); clear(10, 12, px, 12); clear(px + 1, entrance.y - 25, px + 3, entrance.y - 3)
        w.fenceRect(3, 5, 8, 10, Fence.LIGHT)
        repeat(6) { w.tick(0.1f) }; assertEquals("recinto", 2, s.tutorialStep)
        assertTrue(w.placeBuilding("feeder_herb", 4, 6).ok); repeat(6) { w.tick(0.1f) }; assertEquals("comedero", 3, s.tutorialStep)
        assertTrue(w.placeBuilding("water_trough", 7, 9).ok); repeat(6) { w.tick(0.1f) }; assertEquals("agua", 4, s.tutorialStep)
        for (y in entrance.y - 2 downTo 12) w.placePath(px, y)
        for (x in 10..px) w.placePath(x, 12)
        for (y in 7..12) w.placePath(10, y)
        repeat(6) { w.tick(0.1f) }; assertEquals("camino", 5, s.tutorialStep)
        assertTrue(w.canPlaceBuilding(GameData.building("viewpoint"), 9, 7).reason, w.placeBuilding("viewpoint", 9, 7).ok)
        repeat(6) { w.tick(0.1f) }; assertEquals("mirador", 6, s.tutorialStep)
        assertTrue(w.placeBuilding("generator", px + 1, entrance.y - 4).ok); repeat(6) { w.tick(0.1f) }; assertEquals("generador", 7, s.tutorialStep)
        assertTrue(w.placeBuilding("expedition_hq", px + 1, entrance.y - 8).ok); repeat(6) { w.tick(0.1f) }; assertEquals("expediciones", 8, s.tutorialStep)
        assertTrue(w.startExpedition("canon_rojo").ok); repeat(6) { w.tick(0.1f) }; assertEquals("expedicion", 9, s.tutorialStep)
        assertTrue(w.placeBuilding("lab", px + 1, entrance.y - 12).ok); repeat(12) { w.tick(0.1f) }; assertEquals("laboratorio", 10, s.tutorialStep)
        val money = s.money
        w.spawnDino("gallimimus", w.enclosures().first().id); repeat(6) { w.tick(0.1f) }; assertEquals("primer dino", 11, s.tutorialStep)
        assertTrue("el tutorial no da dinero", s.money <= money)
        w.spawnDino("gallimimus", w.enclosures().first().id); w.spawnDino("gallimimus", w.enclosures().first().id)
        repeat(6) { w.tick(0.1f) }; assertEquals("grupo", 12, s.tutorialStep)
        w.skipTutorial(); assertNull(w.currentTutorialStep())
        w.restartTutorial(); assertEquals(0, s.tutorialStep)
    }

    @Test
    fun challengesAndCorpses() {
        val s = IslandGen.generate(GameData.islandById.getValue("brote"), 5L)
        s.challenge = "no_carnivores"
        val w = World(s)
        for (y in 3..8) for (x in 3..8) { s.terrain[s.idx(x, y)] = Terrain.GRASS; s.height[s.idx(x, y)] = 0 }
        w.grid.rebuildRegions()
        w.fenceRect(3, 3, 8, 8, Fence.LIGHT)
        val r = w.enclosures().first()
        for (y in 12..15) for (x in 12..17) { s.terrain[s.idx(x, y)] = Terrain.GRASS; s.height[s.idx(x, y)] = 0 }
        w.grid.rebuildAll()
        w.placeBuilding("lab", 12, 12); w.placeBuilding("generator", 15, 12)
        s.dna["velociraptor"] = 100; s.dna["gallimimus"] = 100
        assertFalse("el reto bloquea carnívoros", w.canIncubate("velociraptor", r.id).ok)
        // un dino que muere deja cadáver
        val d = w.spawnDino("gallimimus", r.id)!!
        d.food = 0f; d.water = 0f; d.starve = 149f
        repeat(15) { d.food = 0f; d.water = 0f; w.tick(0.1f) }
        assertTrue("cadáver creado", s.corpses.isNotEmpty())
        assertTrue("dino retirado", s.dinos.isEmpty())
        // el cadáver desaparece con el tiempo
        while (s.corpses.isNotEmpty() && s.time < 200f) w.tick(0.1f)
        assertTrue("cadáver retirado", s.corpses.isEmpty())
        // sonidos emitidos
        var sounds = 0
        w.soundSink = { sounds++ }
        s.terrain[s.idx(20, 20)] = Terrain.GRASS; s.height[s.idx(20, 20)] = 0
        assertTrue(w.placePath(20, 20).ok)
        assertTrue("sonido de colocación emitido", sounds >= 1)
        // reto tormentas: el siguiente evento es tormenta
        s.challenge = "storms"
        s.nextEvent = 0.1f; s.eventType = EventType.NONE
        w.tick(0.1f); w.tick(0.1f)
        assertEquals(EventType.STORM, s.eventType)
    }
}
