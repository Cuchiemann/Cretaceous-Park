package com.momentadesunt.cretaceouspark.core

/** Tipos de terreno. Los valores se guardan en el estado; no reordenar. */
object Terrain {
    const val GRASS = 0
    const val SAND = 1
    const val FOREST = 2
    const val ROCK = 3
    const val WATER = 4
    const val PATH = 5
    const val BRIDGE = 6

    fun isLand(t: Int) = t != WATER
    fun isWalkablePath(t: Int) = t == PATH || t == BRIDGE
    fun isBuildable(t: Int) = t == GRASS || t == SAND
    fun dinoWalkable(t: Int) = t == GRASS || t == SAND || t == FOREST
}

enum class Diet { HERBIVORE, CARNIVORE }

enum class Size(val footprint: Int, val foodPerMin: Float, val waterPerMin: Float, val feederUnits: Int,
                val speed: Float, val incubationSeconds: Float, val transportCost: Int, val cureCost: Int, val label: String) {
    S(1, 10f, 15f, 1, 1.5f, 30f, 2000, 1500, "Pequeño"),
    M(2, 12f, 15f, 2, 1.0f, 60f, 5000, 3000, "Mediano"),
    L(3, 15f, 15f, 4, 0.7f, 90f, 10000, 6000, "Grande")
}

/** Niveles de valla. 0 = sin valla. */
object Fence {
    const val NONE = 0
    const val LIGHT = 1
    const val MEDIUM = 2
    const val HEAVY = 3
    const val ELECTRIC = 4

    val names = arrayOf("—", "Valla ligera", "Valla media", "Valla pesada", "Valla eléctrica")
    val hp = intArrayOf(0, 100, 250, 600, 400)
    val cost = intArrayOf(0, 50, 120, 300, 250)
    val upkeep = floatArrayOf(0f, 1f, 2f, 4f, 3f)
    val research = arrayOf<String?>(null, null, "B1", "B3", "B6")

    /** Nivel efectivo de contención para comparar con fenceMin de la especie. */
    fun strengthLevel(type: Int, powered: Boolean): Int = when (type) {
        ELECTRIC -> if (powered) HEAVY else LIGHT
        else -> type
    }
}

data class SpeciesDef(
    val id: String, val name: String, val diet: Diet, val size: Size, val cost: Int,
    val spaceMin: Int, val groupMin: Int, val groupMax: Int, val attraction: Int, val danger: Int,
    val attack: Int, val fenceMin: Int, val darts: Int, val site: String,
    val colorBody: Int, val colorDetail: Int, val requiresWaterTiles: Int = 0
)

enum class Category(val label: String) { ENCLOSURE("Recintos"), PATHS("Caminos"), SERVICES("Servicios"), CENTERS("Centros"), TERRAIN("Terreno") }

data class BuildingDef(
    val id: String, val name: String, val category: Category, val w: Int, val h: Int, val cost: Int, val upkeep: Float,
    val research: String? = null,
    val insideEnclosure: Boolean = false, val needsPath: Boolean = false, val needsPower: Boolean = false,
    val attachToFence: Boolean = false,
    val feederDiet: Diet? = null, val stock: Int = 0, val refillCost: Int = 0,
    val isWater: Boolean = false, val shelterDinos: Boolean = false,
    val viewRange: Int = 0, val visitorCapacity: Int = 0, val isShelter: Boolean = false, val shelterCapacity: Int = 0,
    val serveSeconds: Float = 0f, val price: Int = 0, val restoreNeed: Int = -1, val restoreAmount: Int = 0,
    val beds: Int = 0, val powerRadius: Int = 0, val researchPerMin: Float = 0f, val actionRadius: Int = 0,
    val maxCount: Int = 99, val color: Int, val height: Float = 1f, val desc: String = ""
)

/** Índices de necesidades de visitante. */
object Need { const val HUNGER = 0; const val THIRST = 1; const val REST = 2; const val FUN = 3 }

enum class Branch(val label: String) { WELFARE("Bienestar"), FUN("Entretenimiento"), GENETICS("Genética") }

data class ResearchDef(val id: String, val name: String, val branch: Branch, val pi: Int, val cost: Int,
                       val seconds: Float, val prereqs: List<String>, val desc: String)

data class SiteDef(val id: String, val name: String, val cost: Int, val seconds: Float, val fossils: Int,
                   val unlock: String, val species: List<String>)

data class IslandDef(
    val id: String, val name: String, val size: Int, val budget: Int, val incomeTarget: Float,
    val pctForest: Int, val pctRock: Int, val pctWater: Int, val pctSand: Int,
    val eventMin: Float, val eventMax: Float,
    val wStorm: Int, val wDisease: Int, val wEscape: Int, val wSabotage: Int, val stormLevel: Int,
    val requiredStarsPrev: Float, val desc: String, val sandbox: Boolean = false
)

enum class TerrainTool(val label: String, val from: Int, val to: Int, val cost: Int) {
    CLEAR_FOREST("Talar", Terrain.FOREST, Terrain.GRASS, 100),
    DIG_WATER("Cavar agua", Terrain.GRASS, Terrain.WATER, 150),
    FILL_WATER("Rellenar", Terrain.WATER, Terrain.GRASS, 150),
    PLANT_FOREST("Plantar", Terrain.GRASS, Terrain.FOREST, 80),
    BLAST_ROCK("Demoler roca", Terrain.ROCK, Terrain.GRASS, 400)
}

/** Estado de un dinosaurio (máquina de estados). */
object DinoState {
    const val WANDER = 0
    const val SEEK_FOOD = 1
    const val SEEK_WATER = 2
    const val TO_FENCE = 3
    const val ATTACK = 4
    const val ESCAPED = 5
    const val ASLEEP = 6
}

object VisitorState {
    const val WANDER = 0
    const val GOTO = 1
    const val USE = 2
    const val VIEW = 3
    const val FLEE = 4
    const val LEAVE = 5
    const val SLEEP = 6
}

object EventType { const val NONE = 0; const val STORM = 1; const val DISEASE = 2; const val ESCAPE = 3; const val SABOTAGE = 4 }
