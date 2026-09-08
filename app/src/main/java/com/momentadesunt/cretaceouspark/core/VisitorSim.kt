package com.momentadesunt.cretaceouspark.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class VisitorSim(private val w: World) {
    private val s get() = w.s
    private val grid get() = w.grid
    private val rnd get() = w.rnd
    private val decay = floatArrayOf(1f / 6f, 1f / 4f, 1f / 8f, 1f / 5f)
    private val palette = intArrayOf(0xFF4FA3D9.toInt(), 0xFFD9483B.toInt(), 0xFFE8A33A.toInt(), 0xFF6DBA66.toInt(), 0xFFC75BA0.toInt(), 0xFFF1E9D2.toInt(), 0xFF3E7C8F.toInt())
    private var viewAcc = 0f

    fun entrance(): Building? = s.buildings.firstOrNull { it.type == "entrance" }
    fun spawnTiles(): List<Int> { val e = entrance() ?: return emptyList(); return grid.adjacentPathTiles(e) }

    fun capacity(): Int {
        var paths = 0
        for (t in s.terrain) if (Terrain.isWalkablePath(t)) paths++
        val beds = s.buildings.sumOf { it.def.beds }
        val perTile = if (s.researchDone.contains("E2")) 3 else 2
        return paths * perTile + beds
    }

    fun update(dt: Float) {
        // ferry
        s.ferryTimer += dt
        if (s.ferryTimer >= GameData.FERRY_INTERVAL) {
            s.ferryTimer = 0f
            ferry()
        }
        // visitantes agregados
        val it = s.virtualVisitors.iterator()
        val avgComfort = if (s.visitors.isEmpty()) 50f else s.visitors.map { it.comfort() }.average().toFloat()
        while (it.hasNext()) {
            val v = it.next()
            if (s.time >= v[0]) { it.remove(); w.earn(v[1] * GameData.VISITOR_BUDGET * spendMult(avgComfort) * 0.5f) }
        }

        viewAcc += dt
        val viewTick = viewAcc >= 5f
        if (viewTick) viewAcc -= 5f

        val storm = w.stormActive
        val danger = s.dinos.filter { it.state == DinoState.ESCAPED && it.def.danger >= 3 }
        val gone = ArrayList<Visitor>()
        for (v in s.visitors) {
            v.hop = (v.hop + dt * 8f) % 6.2832f
            v.time += dt
            for (i in 0 until 4) v.needs[i] = max(0f, v.needs[i] - decay[i] * dt)
            val sheltered = v.state == VisitorState.SLEEP || (v.state == VisitorState.USE && v.targetBuilding.let { id -> s.buildings.firstOrNull { it.id == id }?.def?.isShelter == true })
            if (storm && !sheltered) for (i in 0 until 4) v.needs[i] = max(0f, v.needs[i] - 0.5f * dt)

            // peligro cercano → huir
            if (v.state != VisitorState.FLEE && v.state != VisitorState.LEAVE && v.state != VisitorState.SLEEP) {
                val near = danger.any { abs(it.x - v.x) + abs(it.y - v.y) < 10f }
                if (near || (storm && !sheltered && rnd.nextFloat() < 0.02f)) {
                    v.needs[Need.FUN] = max(0f, v.needs[Need.FUN] - 30f)
                    startFlee(v)
                } else if (viewTick) {
                    // ver un cadáver: susto, diversión −30 y huida
                    val c = s.corpses.firstOrNull { abs(it.x - v.x) <= 6f && abs(it.y - v.y) <= 6f && !v.seen.contains("corpse:${it.id}") }
                    if (c != null) { v.seen.add("corpse:${c.id}"); v.needs[Need.FUN] = max(0f, v.needs[Need.FUN] - 30f); startFlee(v) }
                }
            }

            when (v.state) {
                VisitorState.LEAVE -> {
                    if (v.path.isEmpty()) {
                        val sp = spawnTiles()
                        if (sp.isEmpty() || sp.contains(tileOf(v))) { gone.add(v); continue }
                        v.path = grid.pathBetween(tileOf(v), sp.minByOrNull { d2(v, it) }!!)
                        if (v.path.isEmpty()) { gone.add(v); continue }
                    }
                    walk(v, dt)
                }
                VisitorState.FLEE -> {
                    if (v.path.isEmpty()) {
                        if (v.targetBuilding >= 0) { v.state = VisitorState.USE; v.timer = 20f }
                        else { v.state = VisitorState.LEAVE }
                    } else walk(v, dt)
                }
                VisitorState.SLEEP -> {
                    v.timer -= dt
                    if (v.timer <= 0f) { v.state = VisitorState.WANDER; v.needs[Need.REST] = 100f }
                }
                VisitorState.USE -> {
                    v.timer -= dt
                    if (v.timer <= 0f) {
                        val b = s.buildings.firstOrNull { it.id == v.targetBuilding }
                        if (b != null && !(danger.isNotEmpty() && b.def.isShelter) && !(storm && b.def.isShelter)) {
                            val d = b.def
                            if (d.restoreNeed >= 0 && (d.price == 0 || v.budget - v.spent >= d.price)) {
                                if (d.price > 0 && !b.powered) { /* sin energía: no vende */ }
                                else {
                                    v.spent += d.price; w.earn(d.price.toFloat())
                                    v.needs[d.restoreNeed] = min(100f, v.needs[d.restoreNeed] + d.restoreAmount)
                                }
                            }
                            v.state = VisitorState.WANDER; v.targetBuilding = -1
                        } else if (b != null) { v.timer = 5f } else { v.state = VisitorState.WANDER; v.targetBuilding = -1 }
                    }
                }
                VisitorState.VIEW -> {
                    v.timer -= dt
                    if (viewTick) gainFun(v)
                    if (v.timer <= 0f) { v.state = VisitorState.WANDER; v.targetBuilding = -1 }
                }
                VisitorState.GOTO -> {
                    if (v.path.isEmpty()) {
                        val b = s.buildings.firstOrNull { it.id == v.targetBuilding }
                        if (b == null) { v.state = VisitorState.WANDER; v.targetBuilding = -1 }
                        else if (b.def.viewRange > 0) { v.state = VisitorState.VIEW; v.timer = 10f + rnd.nextFloat() * 6f; gainFun(v) }
                        else if (b.def.beds > 0) {
                            if (v.budget - v.spent >= b.def.price && v.nights < 2) {
                                v.spent += b.def.price; w.earn(b.def.price.toFloat()); v.nights++
                                v.budget += 30f; v.time -= GameData.VISIT_SECONDS
                                v.state = VisitorState.SLEEP; v.timer = 25f
                            } else { v.state = VisitorState.WANDER; v.targetBuilding = -1 }
                        }
                        else { v.state = VisitorState.USE; v.timer = b.def.serveSeconds }
                    } else walk(v, dt)
                }
                else -> { // WANDER
                    if (v.time >= GameData.VISIT_SECONDS) { v.state = VisitorState.LEAVE; v.path.clear(); continue }
                    if (!v.path.isEmpty()) { walk(v, dt); continue }
                    decide(v, dt)
                }
            }
        }
        for (v in gone) leave(v)
    }

    private fun ferry() {
        val price = GameData.ENTRY_PRICES[s.entryPrice]
        var mod = when (s.entryPrice) { 0 -> 1.2f; 1 -> 1f; else -> if (s.reputation < 50f) 0.6f else 0.8f }
        if (s.researchDone.contains("E6")) mod *= 1.25f
        val attraction = if (s.def.sandbox) 60f else s.idxAttraction
        var arrivals = ((2f + 0.25f * attraction * (0.5f + s.reputation / 100f)) * mod).roundToInt()
        val cap = capacity() - s.visitors.size
        arrivals = min(arrivals, cap).coerceAtLeast(0)
        if (arrivals == 0) return
        val tiles = spawnTiles()
        if (tiles.isEmpty()) return
        w.earn(arrivals * price.toFloat())
        val agents = min(arrivals, GameData.MAX_VISITOR_AGENTS - s.visitors.size).coerceAtLeast(0)
        for (i in 0 until agents) {
            val t = tiles[rnd.nextInt(tiles.size)]
            s.visitors.add(Visitor(s.newId(), t % w.n + 0.3f + rnd.nextFloat() * 0.4f, t / w.n + 0.3f + rnd.nextFloat() * 0.4f,
                needs = floatArrayOf(60f + rnd.nextFloat() * 20f, 60f + rnd.nextFloat() * 20f, 100f, 30f), color = palette[rnd.nextInt(palette.size)]))
        }
        val extra = arrivals - agents
        if (extra > 0) s.virtualVisitors.add(floatArrayOf(s.time + GameData.VISIT_SECONDS, extra.toFloat()))
    }

    private fun spendMult(comfort: Float) = when { comfort >= 70f -> 1f; comfort >= 40f -> 0.5f; else -> 0.2f }

    private fun leave(v: Visitor) {
        s.visitors.remove(v)
        if (v.comfort() < 30f || v.injured) s.reputation = max(0f, s.reputation - 0.5f)
        else if (v.comfort() > 75f) s.reputation = min(100f, s.reputation + 0.1f)
    }

    private fun tileOf(v: Visitor) = s.idx(floor(v.x).toInt().coerceIn(0, w.n - 1), floor(v.y).toInt().coerceIn(0, w.n - 1))
    private fun d2(v: Visitor, t: Int): Float { val x = t % w.n + 0.5f; val y = t / w.n + 0.5f; return (x - v.x) * (x - v.x) + (y - v.y) * (y - v.y) }

    private fun walk(v: Visitor, dt: Float) {
        val t = v.path.first()
        val tx = t % w.n + 0.5f; val ty = t / w.n + 0.5f
        val dx = tx - v.x; val dy = ty - v.y
        val dist = abs(dx) + abs(dy)
        val speed = if (v.state == VisitorState.FLEE) 2.2f else 1.3f
        val step = speed * dt
        if (dist <= step) { v.x = tx; v.y = ty; v.path.removeAt(0) }
        else { v.x += dx / dist * step; v.y += dy / dist * step }
    }

    private fun startFlee(v: Visitor) {
        val from = tileOf(v)
        val shelters = s.buildings.filter { it.def.isShelter }
        var best: Building? = null; var bestPath: MutableList<Int>? = null
        for (b in shelters) {
            val adj = grid.adjacentPathTiles(b)
            if (adj.isEmpty()) continue
            val p = grid.pathBetween(from, adj.minByOrNull { d2(v, it) }!!)
            if (p.isEmpty() && adj.contains(from)) { best = b; bestPath = mutableListOf(); break }
            if (p.isNotEmpty() && (bestPath == null || p.size < bestPath.size)) { best = b; bestPath = p }
        }
        v.state = VisitorState.FLEE
        if (best != null && bestPath != null) { v.targetBuilding = best.id; v.path = bestPath }
        else { v.targetBuilding = -1; v.state = VisitorState.LEAVE; v.path.clear() }
    }

    private fun decide(v: Visitor, dt: Float) {
        val from = tileOf(v)
        // necesidad más urgente
        var worst = -1; var worstVal = 40f
        for (i in 0 until 4) if (v.needs[i] < worstVal) { worstVal = v.needs[i]; worst = i }
        if (worst >= 0 && worst != Need.FUN) {
            val b = findService(v, from, worst)
            if (b != null) { goTo(v, from, b); v.patience = 0f; return }
            v.patience += dt
            if (v.patience > 40f) { v.patience = 0f; for (i in 0 until 4) v.needs[i] = max(0f, v.needs[i] - 5f) }
        }
        if (v.needs[Need.FUN] < 60f) {
            val vp = s.buildings.filter { it.def.viewRange > 0 }.filter { grid.adjacentPathTiles(it).isNotEmpty() }
            if (vp.isNotEmpty()) {
                // preferir miradores con dinos visibles y no vistos
                val scored = vp.map { b -> b to visibleSpecies(b).sumOf { sp -> GameData.species(sp).attraction * (if (v.seen.contains(sp)) 0.4 else 1.0) } }
                val best = scored.filter { it.second > 0 }.maxByOrNull { it.second - d2(v, s.idx(it.first.x, it.first.y)) * 0.01 }?.first ?: vp[rnd.nextInt(vp.size)]
                if (goTo(v, from, best)) return
            } else if (worst < 0) {
                val gift = findService(v, from, Need.FUN)
                if (gift != null) { goTo(v, from, gift); return }
            }
        }
        // pasear
        v.timer -= dt
        if (v.timer <= 0f) {
            v.timer = 2f + rnd.nextFloat() * 3f
            val reach = grid.reachablePaths(from)
            val cand = ArrayList<Int>()
            for (i in reach.indices) if (reach[i] && i != from && abs(i % w.n - from % w.n) + abs(i / w.n - from / w.n) <= 6) cand.add(i)
            if (cand.isNotEmpty()) v.path = grid.pathBetween(from, cand[rnd.nextInt(cand.size)])
        }
    }

    private fun findService(v: Visitor, from: Int, need: Int): Building? {
        val cands = s.buildings.filter { it.def.restoreNeed == need && it.def.beds == 0 && (it.def.price == 0 || v.budget - v.spent >= it.def.price) }
            .filter { it.powered || it.def.price == 0 }
        val hotels = if (need == Need.REST && v.nights < 2) s.buildings.filter { it.def.beds > 0 && v.budget - v.spent >= it.def.price } else emptyList()
        val all = cands + hotels
        var best: Building? = null; var bd = Float.MAX_VALUE
        for (b in all) {
            val adj = grid.adjacentPathTiles(b)
            if (adj.isEmpty()) continue
            val dist = adj.minOf { d2(v, it) }
            if (dist < bd) { bd = dist; best = b }
        }
        return best
    }

    private fun goTo(v: Visitor, from: Int, b: Building): Boolean {
        val adj = grid.adjacentPathTiles(b)
        if (adj.isEmpty()) return false
        val target = adj.minByOrNull { d2(v, it) }!!
        val p = if (target == from) mutableListOf() else grid.pathBetween(from, target)
        if (p.isEmpty() && target != from) return false
        v.path = p; v.targetBuilding = b.id; v.state = VisitorState.GOTO
        return true
    }

    /** Especies visibles desde un mirador: dinos dentro del alcance (distancia Chebyshev). */
    fun visibleSpecies(b: Building): Set<String> {
        val out = HashSet<String>()
        val range = b.def.viewRange
        for (d in s.dinos) {
            if (d.state == DinoState.ESCAPED) continue
            val r = range * (if (d.def.size == Size.L) 1.5f else 1f)
            if (abs(d.x - b.cx) <= r && abs(d.y - b.cy) <= r) out.add(d.species)
        }
        return out
    }

    private fun gainFun(v: Visitor) {
        val b = s.buildings.firstOrNull { it.id == v.targetBuilding } ?: return
        var gain = 0f
        for (d in s.dinos) {
            if (d.state == DinoState.ESCAPED) continue
            val dist = max(abs(d.x - b.cx), abs(d.y - b.cy))
            val range = b.def.viewRange * (if (d.def.size == Size.L) 1.5f else 1f)
            if (dist > range) continue
            val fd = if (dist <= 4f) 1f else 0.5f
            val novelty = if (v.seen.contains(d.species)) 0.4f else 1f
            val attr = d.def.attraction + (if (d.skin == 1) 1 else 0) + (if (d.geneTemper == 2) 2 else 0)
            gain += attr * fd * novelty
            v.seen.add(d.species)
        }
        v.needs[Need.FUN] = min(100f, v.needs[Need.FUN] + gain)
    }
}
