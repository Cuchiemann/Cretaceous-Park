package com.momentadesunt.cretaceouspark.core

import kotlinx.serialization.Serializable

@Serializable
class Building(
    val id: Int, val type: String, val x: Int, val y: Int,
    var stock: Int = 0,
    var powered: Boolean = true,
    var open: Boolean = false
) {
    val def: BuildingDef get() = GameData.building(type)
    val w get() = def.w
    val h get() = def.h
    fun covers(tx: Int, ty: Int) = tx >= x && tx < x + w && ty >= y && ty < y + h
    val cx get() = x + w / 2f
    val cy get() = y + h / 2f
}

@Serializable
class Dino(
    val id: Int, val species: String,
    var x: Float, var y: Float,
    var tx: Float, var ty: Float,               // objetivo actual
    var region: Int = 0,
    var homeRegion: Int = 0,
    var food: Float = 80f, var water: Float = 80f,
    var stress: Float = 0f,
    var wellbeing: Float = 80f,
    var space: Float = 100f, var social: Float = 100f,
    var state: Int = DinoState.WANDER,
    var idle: Float = 0f,                        // segundos de espera
    var attackCd: Float = 0f,
    var sick: Boolean = false, var sickTimer: Float = 0f,
    var starve: Float = 0f,
    var dartHits: Int = 0, var sleep: Float = 0f,
    var targetEdge: Int = -1,                    // índice de borde objetivo (h: idx, v: idx + 1_000_000)
    var facing: Int = 0,
    var skin: Int = 0,
    var geneResist: Boolean = false, var geneTemper: Int = 0, // 0 normal, 1 dócil, 2 vistoso
    var hitCd: Float = 0f,
    var hop: Float = 0f
) {
    val def: SpeciesDef get() = GameData.species(species)
}

@Serializable
class Visitor(
    val id: Int,
    var x: Float, var y: Float,
    var needs: FloatArray = floatArrayOf(70f, 70f, 100f, 30f),
    var budget: Float = GameData.VISITOR_BUDGET,
    var spent: Float = 0f,
    var time: Float = 0f,
    var state: Int = VisitorState.WANDER,
    var path: MutableList<Int> = mutableListOf(),  // tiles (índice y*n+x) pendientes
    var targetBuilding: Int = -1,
    var timer: Float = 0f,
    var patience: Float = 0f,
    var comfortHit: Float = 0f,
    var seen: MutableSet<String> = mutableSetOf(),
    var nights: Int = 0,
    var color: Int = 0,
    var injured: Boolean = false,
    var hop: Float = 0f
) {
    fun comfort(): Float = (needs[0] + needs[1] + needs[2] + needs[3]) / 4f
}

@Serializable
class Expedition(val site: String, var remaining: Float)

@Serializable
class Incubation(val species: String, val region: Int, var remaining: Float, val genes: Int = 0)

@Serializable
class Alert(val kind: String, val text: String, val x: Int, val y: Int, val entityId: Int = -1, var ttl: Float = 30f)

@Serializable
class FossilLog(val species: String, val quality: String, val dna: Int, val sold: Int)

@Serializable
class GameState(
    val island: String,
    val size: Int,
    var seed: Long,
    val terrain: IntArray,
    val hType: IntArray, val hHp: IntArray, val hFlags: IntArray,   // bordes horizontales: (size+1) filas × size
    val vType: IntArray, val vHp: IntArray, val vFlags: IntArray,   // bordes verticales: size filas × (size+1)
    var money: Double,
    var time: Float = 0f,
    var nextId: Int = 1,
    var speed: Int = 1,
    val buildings: MutableList<Building> = mutableListOf(),
    val dinos: MutableList<Dino> = mutableListOf(),
    val visitors: MutableList<Visitor> = mutableListOf(),
    var virtualVisitors: MutableList<FloatArray> = mutableListOf(),  // [leaveTime, count]
    var entryPrice: Int = 1,
    var reputation: Float = 50f,
    var idxProfit: Float = 0f, var idxSafety: Float = 100f, var idxWelfare: Float = 0f, var idxAttraction: Float = 0f,
    var stars: Float = 0f,
    var fiveStarTimer: Float = 0f,
    var completed: Boolean = false,
    var bankruptTimer: Float = 0f,
    var gameOver: Boolean = false,
    var incomeMin: Float = 0f, var expenseMin: Float = 0f,
    var ledgerIncome: Float = 0f, var ledgerExpense: Float = 0f, var ledgerTimer: Float = 0f,
    var totalIncome: Double = 0.0, var totalExpense: Double = 0.0,
    val researchDone: MutableSet<String> = mutableSetOf(),
    var researchActive: String? = null,
    var researchProgress: Float = 0f,
    var researchPoints: Float = 0f,
    val sitesUnlocked: MutableSet<String> = mutableSetOf("canon_rojo"),
    val expeditions: MutableList<Expedition> = mutableListOf(),
    val dna: MutableMap<String, Int> = mutableMapOf(),
    val fossilLog: MutableList<FossilLog> = mutableListOf(),
    val incubations: MutableList<Incubation> = mutableListOf(),
    val cloned: MutableSet<String> = mutableSetOf(),
    val alerts: MutableList<Alert> = mutableListOf(),
    // eventos
    var eventType: Int = EventType.NONE,
    var eventWarn: Float = 0f,
    var eventTimer: Float = 0f,
    var nextEvent: Float = 300f,
    var eventTarget: Int = -1,
    var generatorOff: Float = 0f,
    var eventText: String = "",
    // historial reciente para seguridad/bienestar
    val injuries: MutableList<Float> = mutableListOf(),
    val visitorDeaths: MutableList<Float> = mutableListOf(),
    val dinoDeaths: MutableList<Float> = mutableListOf(),
    var amberEarned: Int = 0,
    var amberBanked: Int = 0,
    var amberStarsAwarded: Int = 0,
    var ferryTimer: Float = 0f,
    var autosave: Float = 0f,
    var tutorialActive: Boolean = false,
    var tutorialStep: Int = 0
) {
    fun idx(x: Int, y: Int) = y * size + x
    fun inBounds(x: Int, y: Int) = x >= 0 && y >= 0 && x < size && y < size
    fun terrainAt(x: Int, y: Int) = terrain[idx(x, y)]
    fun newId() = nextId++

    // Bordes: horizontal (x, y) separa (x, y-1) y (x, y). y ∈ 0..size. Índice y*size + x.
    fun hIdx(x: Int, y: Int) = y * size + x
    // Vertical (x, y) separa (x-1, y) y (x, y). x ∈ 0..size. Índice y*(size+1) + x.
    fun vIdx(x: Int, y: Int) = y * (size + 1) + x

    val def: IslandDef get() = GameData.islandById.getValue(island)
}

/** Flags de borde. */
object EdgeFlag { const val GATE = 1; const val OPEN = 2 }
