package com.momentadesunt.cretaceouspark.core

/** Referencia a un borde (valla). h = true → borde horizontal (entre (x,y-1) y (x,y)); false → vertical (entre (x-1,y) y (x,y)). */
data class EdgeRef(val h: Boolean, val x: Int, val y: Int) {
    fun key(): Int = if (h) (y shl 12) or x else (1 shl 30) or (y shl 12) or x
    companion object {
        fun fromKey(k: Int): EdgeRef = EdgeRef((k and (1 shl 30)) == 0, k and 0xFFF, (k shr 12) and 0xFFF)
    }
}

class RegionInfo(val id: Int) {
    var tiles = 0
    var water = 0
    var forest = 0
    var sand = 0
    var buildable = 0
    var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
    val edges = ArrayList<EdgeRef>()     // bordes con valla que delimitan la región
    var weakestLevel = Int.MAX_VALUE      // nivel de contención efectivo mínimo
    var powered = false
    val tileList = ArrayList<Int>()
    val name: String get() = "Recinto $id"
    val cx: Float get() = (minX + maxX + 1) / 2f
    val cy: Float get() = (minY + maxY + 1) / 2f
}

/** Cachés derivadas del estado: regiones, energía, ocupación. Se recalculan al cambiar el mapa. */
class Grid(val s: GameState) {
    val n = s.size
    val region = IntArray(n * n)
    val buildingAt = IntArray(n * n) { -1 }   // índice en s.buildings
    val powered = BooleanArray(n * n)
    val regions = HashMap<Int, RegionInfo>()
    var regionCounter = 0

    init { rebuildAll() }

    fun rebuildAll() {
        rebuildBuildings()
        rebuildPower()
        rebuildRegions()
    }

    fun rebuildBuildings() {
        buildingAt.fill(-1)
        s.buildings.forEachIndexed { i, b ->
            for (yy in b.y until b.y + b.h) for (xx in b.x until b.x + b.w) if (s.inBounds(xx, yy)) buildingAt[s.idx(xx, yy)] = i
        }
    }

    fun rebuildPower() {
        powered.fill(false)
        if (s.generatorOff > 0f) {
            s.buildings.forEach { it.powered = !it.def.needsPower }
            return
        }
        for (b in s.buildings) {
            val r = b.def.powerRadius
            if (r <= 0) continue
            for (yy in b.y - r..b.y + b.h - 1 + r) for (xx in b.x - r..b.x + b.w - 1 + r) {
                if (!s.inBounds(xx, yy)) continue
                val dx = maxOf(0, maxOf(b.x - xx, xx - (b.x + b.w - 1)))
                val dy = maxOf(0, maxOf(b.y - yy, yy - (b.y + b.h - 1)))
                if (dx + dy <= r) powered[s.idx(xx, yy)] = true
            }
        }
        for (b in s.buildings) {
            if (!b.def.needsPower) { b.powered = true; continue }
            var any = false
            for (yy in b.y until b.y + b.h) for (xx in b.x until b.x + b.w) if (s.inBounds(xx, yy) && powered[s.idx(xx, yy)]) any = true
            b.powered = any
        }
    }

    /** ¿El borde bloquea el paso de dinosaurios? */
    fun blocks(h: Boolean, x: Int, y: Int): Boolean {
        val i = if (h) s.hIdx(x, y) else s.vIdx(x, y)
        val type = if (h) s.hType[i] else s.vType[i]
        if (type == Fence.NONE) return false
        // Una valla rota (hp 0) sigue delimitando el recinto; los dinos estresados salen por el hueco individualmente.
        val flags = if (h) s.hFlags[i] else s.vFlags[i]
        if ((flags and EdgeFlag.GATE) != 0 && (flags and EdgeFlag.OPEN) != 0) return false
        return true
    }

    fun fenceType(e: EdgeRef): Int = if (e.h) s.hType[s.hIdx(e.x, e.y)] else s.vType[s.vIdx(e.x, e.y)]
    fun fenceHp(e: EdgeRef): Int = if (e.h) s.hHp[s.hIdx(e.x, e.y)] else s.vHp[s.vIdx(e.x, e.y)]
    fun fenceFlags(e: EdgeRef): Int = if (e.h) s.hFlags[s.hIdx(e.x, e.y)] else s.vFlags[s.vIdx(e.x, e.y)]
    fun setFence(e: EdgeRef, type: Int, hp: Int, flags: Int = 0) {
        if (e.h) { val i = s.hIdx(e.x, e.y); s.hType[i] = type; s.hHp[i] = hp; s.hFlags[i] = flags }
        else { val i = s.vIdx(e.x, e.y); s.vType[i] = type; s.vHp[i] = hp; s.vFlags[i] = flags }
    }
    fun setFenceHp(e: EdgeRef, hp: Int) { if (e.h) s.hHp[s.hIdx(e.x, e.y)] = hp else s.vHp[s.vIdx(e.x, e.y)] = hp }

    /** ¿La valla del borde está alimentada (para eléctricas)? Se considera si alguno de los dos tiles tiene energía. */
    fun edgePowered(e: EdgeRef): Boolean {
        if (s.generatorOff > 0f) return false
        val (ax, ay, bx, by) = if (e.h) listOf(e.x, e.y - 1, e.x, e.y) else listOf(e.x - 1, e.y, e.x, e.y)
        val pa = s.inBounds(ax, ay) && powered[s.idx(ax, ay)]
        val pb = s.inBounds(bx, by) && powered[s.idx(bx, by)]
        return pa || pb
    }

    /** Flood fill: regiones cerradas por vallas. Región 0 = exterior (toca el borde del mapa). */
    fun rebuildRegions() {
        region.fill(-1)
        regions.clear()
        val stack = IntArray(n * n)
        var nextRegion = 1
        for (start in 0 until n * n) {
            if (region[start] != -1) continue
            var sp = 0
            stack[sp++] = start
            region[start] = -2
            val tiles = ArrayList<Int>()
            var touchesBorder = false
            while (sp > 0) {
                val t = stack[--sp]
                tiles.add(t)
                val x = t % n; val y = t / n
                if (x == 0 || y == 0 || x == n - 1 || y == n - 1) {
                    // fuera si el borde exterior no tiene valla
                    if (x == 0 && !blocks(false, 0, y)) touchesBorder = true
                    if (y == 0 && !blocks(true, x, 0)) touchesBorder = true
                    if (x == n - 1 && !blocks(false, n, y)) touchesBorder = true
                    if (y == n - 1 && !blocks(true, x, n)) touchesBorder = true
                }
                // vecinos
                if (x > 0 && !blocks(false, x, y)) { val u = t - 1; if (region[u] == -1) { region[u] = -2; stack[sp++] = u } }
                if (x < n - 1 && !blocks(false, x + 1, y)) { val u = t + 1; if (region[u] == -1) { region[u] = -2; stack[sp++] = u } }
                if (y > 0 && !blocks(true, x, y)) { val u = t - n; if (region[u] == -1) { region[u] = -2; stack[sp++] = u } }
                if (y < n - 1 && !blocks(true, x, y + 1)) { val u = t + n; if (region[u] == -1) { region[u] = -2; stack[sp++] = u } }
            }
            val id = if (touchesBorder) 0 else nextRegion++
            for (t in tiles) region[t] = id
            if (id != 0) {
                val info = RegionInfo(id)
                regions[id] = info
                for (t in tiles) {
                    val x = t % n; val y = t / n
                    info.tiles++
                    info.tileList.add(t)
                    when (s.terrain[t]) {
                        Terrain.WATER -> info.water++
                        Terrain.FOREST -> info.forest++
                        Terrain.SAND -> info.sand++
                        Terrain.GRASS -> info.buildable++
                    }
                    if (x < info.minX) info.minX = x; if (y < info.minY) info.minY = y
                    if (x > info.maxX) info.maxX = x; if (y > info.maxY) info.maxY = y
                    if (powered[t]) info.powered = true
                }
            }
        }
        // bordes de cada región
        for (info in regions.values) {
            for (t in info.tileList) {
                val x = t % n; val y = t / n
                addEdgeIfFence(info, EdgeRef(false, x, y))
                addEdgeIfFence(info, EdgeRef(false, x + 1, y))
                addEdgeIfFence(info, EdgeRef(true, x, y))
                addEdgeIfFence(info, EdgeRef(true, x, y + 1))
            }
            info.weakestLevel = info.edges.minOfOrNull { Fence.strengthLevel(fenceType(it), edgePowered(it)) } ?: 0
        }
        // los bebederos cuentan como agua
        for (b in s.buildings) if (b.def.isWater) {
            val r = region[s.idx(b.x, b.y)]
            regions[r]?.let { it.water += if (s.researchDone.contains("B2")) 4 else 1 }
        }
    }

    private fun addEdgeIfFence(info: RegionInfo, e: EdgeRef) {
        if (fenceType(e) != Fence.NONE && !info.edges.contains(e)) info.edges.add(e)
    }

    fun regionAt(x: Int, y: Int): Int = if (s.inBounds(x, y)) region[s.idx(x, y)] else 0
    fun buildingAt(x: Int, y: Int): Building? = if (s.inBounds(x, y)) buildingAt[s.idx(x, y)].let { if (it >= 0) s.buildings[it] else null } else null

    /** Tile transitable para un dino dentro de su región. */
    fun dinoWalkable(x: Int, y: Int): Boolean =
        s.inBounds(x, y) && Terrain.dinoWalkable(s.terrainAt(x, y)) && buildingAt[s.idx(x, y)] < 0

    /** ¿Un dino de tamaño fp cabe con esquina en (x,y)? */
    fun dinoFits(x: Int, y: Int, r: Int): Boolean {
        return s.inBounds(x, y) && region[s.idx(x, y)] == r && dinoWalkable(x, y)
    }

    fun isPathTile(x: Int, y: Int) = s.inBounds(x, y) && Terrain.isWalkablePath(s.terrainAt(x, y))

    /** Tile transitable para un dino (por índice). */
    fun dinoWalkableIdx(i: Int): Boolean = Terrain.dinoWalkable(s.terrain[i]) && buildingAt[i] < 0

    /** ¿Camino en (x,y) al que se llega desde un edificio a nivel `level` (desnivel máximo de un nivel)? */
    private fun pathReachable(x: Int, y: Int, level: Int) = isPathTile(x, y) && kotlin.math.abs(s.levelAt(x, y) - level) <= Terrain.MAX_CLIMB

    /** Tiles de camino adyacentes a un edificio (a un nivel alcanzable). */
    fun adjacentPathTiles(b: Building): List<Int> {
        val out = ArrayList<Int>()
        val level = if (s.inBounds(b.x, b.y)) s.levelAt(b.x, b.y) else 0
        for (yy in b.y - 1..b.y + b.h) for (xx in b.x - 1..b.x + b.w) {
            if (b.covers(xx, yy)) continue
            val edgeAdj = (xx in b.x until b.x + b.w) || (yy in b.y until b.y + b.h)
            if (edgeAdj && pathReachable(xx, yy, level)) out.add(s.idx(xx, yy))
        }
        return out
    }

    fun hasAdjacentPath(x: Int, y: Int, w: Int, h: Int): Boolean {
        val level = if (s.inBounds(x, y)) s.levelAt(x, y) else 0
        for (xx in x until x + w) { if (pathReachable(xx, y - 1, level) || pathReachable(xx, y + h, level)) return true }
        for (yy in y until y + h) { if (pathReachable(x - 1, yy, level) || pathReachable(x + w, yy, level)) return true }
        return false
    }

    /** ¿Todos los tiles del rectángulo están al mismo nivel? */
    fun isFlat(x: Int, y: Int, w: Int, h: Int): Boolean {
        if (!s.inBounds(x, y)) return false
        val lv = s.levelAt(x, y)
        for (yy in y until y + h) for (xx in x until x + w) if (!s.inBounds(xx, yy) || s.levelAt(xx, yy) != lv) return false
        return true
    }

    /** Bordes con valla que tocan el perímetro del edificio. */
    fun fenceEdgesAround(x: Int, y: Int, w: Int, h: Int): List<EdgeRef> {
        val out = ArrayList<EdgeRef>()
        for (xx in x until x + w) {
            EdgeRef(true, xx, y).let { if (fenceType(it) != 0) out.add(it) }
            EdgeRef(true, xx, y + h).let { if (fenceType(it) != 0) out.add(it) }
        }
        for (yy in y until y + h) {
            EdgeRef(false, x, yy).let { if (fenceType(it) != 0) out.add(it) }
            EdgeRef(false, x + w, yy).let { if (fenceType(it) != 0) out.add(it) }
        }
        return out
    }

    /** Región al otro lado de un borde respecto al exterior: la que no es 0, si la hay. */
    fun regionsOfEdge(e: EdgeRef): Pair<Int, Int> {
        return if (e.h) Pair(regionAt(e.x, e.y - 1), regionAt(e.x, e.y)) else Pair(regionAt(e.x - 1, e.y), regionAt(e.x, e.y))
    }

    /**
     * BFS 4-conexo con la regla de desnivel: solo se pasa entre vecinos cuya altura difiere en un nivel como mucho.
     * `passable` decide qué tiles se pueden pisar; el origen siempre cuenta. Devuelve la lista de tiles desde el
     * siguiente al origen hasta el destino (vacía si no hay ruta o si origen = destino).
     */
    private val bfsPrev = IntArray(n * n)
    private val bfsQueue = IntArray(n * n)
    private val bfsSeen = BooleanArray(n * n)

    fun findPath(from: Int, to: Int, passable: (Int) -> Boolean): MutableList<Int> {
        if (from == to) return mutableListOf()
        val prev = bfsPrev; prev.fill(-1)
        val queue = bfsQueue
        var qh = 0; var qt = 0
        queue[qt++] = from; prev[from] = from
        while (qh < qt) {
            val t = queue[qh++]
            if (t == to) break
            val x = t % n; val y = t / n
            fun visit(u: Int) { if (prev[u] == -1 && s.stepOk(t, u) && passable(u)) { prev[u] = t; queue[qt++] = u } }
            if (x > 0) visit(t - 1)
            if (x < n - 1) visit(t + 1)
            if (y > 0) visit(t - n)
            if (y < n - 1) visit(t + n)
        }
        if (prev[to] == -1) return mutableListOf()
        val out = ArrayList<Int>()
        var cur = to
        while (cur != from) { out.add(cur); cur = prev[cur] }
        out.reverse()
        return out
    }

    /** Tiles alcanzables desde un origen con la regla de desnivel. El array devuelto es un buffer compartido. */
    fun reachableFrom(from: Int, passable: (Int) -> Boolean): BooleanArray {
        val seen = bfsSeen; seen.fill(false)
        val queue = bfsQueue; var qh = 0; var qt = 0
        queue[qt++] = from; seen[from] = true
        while (qh < qt) {
            val t = queue[qh++]; val x = t % n; val y = t / n
            fun visit(u: Int) { if (!seen[u] && s.stepOk(t, u) && passable(u)) { seen[u] = true; queue[qt++] = u } }
            if (x > 0) visit(t - 1)
            if (x < n - 1) visit(t + 1)
            if (y > 0) visit(t - n)
            if (y < n - 1) visit(t + n)
        }
        return seen
    }

    private val pathPassable: (Int) -> Boolean = { Terrain.isWalkablePath(s.terrain[it]) }

    /** Ruta de visitante sobre caminos (BFS con desnivel máximo de un nivel). */
    fun pathBetween(from: Int, to: Int): MutableList<Int> = findPath(from, to, pathPassable)

    /** Alcance de tiles de camino desde un origen (para elegir destinos accesibles). Buffer compartido: consumir antes de la siguiente llamada. */
    fun reachablePaths(from: Int): BooleanArray {
        if (!isPathTile(from % n, from / n)) { bfsSeen.fill(false); return bfsSeen }
        return reachableFrom(from, pathPassable)
    }
}
