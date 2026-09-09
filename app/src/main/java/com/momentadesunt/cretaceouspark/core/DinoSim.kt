package com.momentadesunt.cretaceouspark.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class RegionStats {
    var count = 0
    val perSpecies = HashMap<String, Int>()
    var demand = 0f
    var hasCarn = false
    var hasHerb = false
    var carnSpecies = HashSet<String>()
    var huntTimer = 0f
}

class DinoSim(private val w: World) {
    private val s get() = w.s
    private val grid get() = w.grid
    private val rnd get() = w.rnd
    private val stats = HashMap<Int, RegionStats>()
    private val huntTimers = HashMap<Int, Float>()
    private var contagionAcc = 0f

    fun update(dt: Float) {
        computeStats()
        contagionAcc += dt
        val contagionTick = contagionAcc >= 30f
        if (contagionTick) contagionAcc -= 30f

        val dead = ArrayList<Dino>()
        for (d in s.dinos) {
            val def = d.def
            // región actual (los fugados ignoran regiones)
            if (d.state != DinoState.ESCAPED) {
                val r = grid.regionAt(floor(d.x).toInt(), floor(d.y).toInt())
                if (r == 0 && d.state != DinoState.ASLEEP) { escape(d) } else if (r != 0) d.region = r
            }
            d.hop = (d.hop + dt * 6f) % 6.2832f
            if (d.hitCd > 0f) d.hitCd -= dt

            if (d.sleep > 0f) {
                d.sleep -= dt
                if (d.sleep <= 0f) { d.sleep = 0f; d.state = if (d.region == 0 || grid.regions[d.region] == null) DinoState.ESCAPED else DinoState.WANDER }
                continue
            }

            // necesidades
            d.food = max(0f, d.food - def.size.foodPerMin / 60f * dt)
            d.water = max(0f, d.water - def.size.waterPerMin / 60f * dt)
            if (d.food <= 0f || d.water <= 0f) {
                d.starve += dt
                if (d.starve >= 150f) { dead.add(d); w.alert("death", "${def.name} ha muerto de ${if (d.food <= 0f) "hambre" else "sed"}", d.x.toInt(), d.y.toInt(), d.id, 40f); continue }
                if (d.starve > 60f) w.alert("starve", "${def.name} se está muriendo de ${if (d.food <= 0f) "hambre" else "sed"}", d.x.toInt(), d.y.toInt(), d.id, 10f)
            } else d.starve = max(0f, d.starve - dt)

            if (d.sick) {
                d.sickTimer += dt
                if (d.sickTimer >= 180f) { dead.add(d); w.alert("death", "${def.name} ha muerto por enfermedad", d.x.toInt(), d.y.toInt(), d.id, 40f); continue }
                if (contagionTick && d.region != 0 && rnd.nextFloat() < 0.5f) {
                    s.dinos.filter { it.region == d.region && !it.sick && it !== d && !(it.geneResist && rnd.nextFloat() < 0.6f) }.randomOrNull(rnd)?.let { o ->
                        o.sick = true; o.sickTimer = 0f
                        w.alert("sick", "${o.def.name} está enfermo", o.x.toInt(), o.y.toInt(), o.id, 60f)
                    }
                }
            }

            // índices de bienestar
            val st = stats[d.region]
            val info = grid.regions[d.region]
            if (d.state == DinoState.ESCAPED || st == null || info == null) {
                d.space = 100f; d.social = 50f
            } else {
                val effective = info.buildable + info.sand * 0.5f + info.forest * (if (def.diet == Diet.HERBIVORE) 1f else 0.5f)
                d.space = if (st.demand <= 0f) 100f else min(100f, 100f * effective / st.demand)
                val same = st.perSpecies[d.species] ?: 1
                d.social = when {
                    same < def.groupMin -> max(25f, 100f - 25f * (def.groupMin - same))
                    same > def.groupMax -> 30f
                    else -> 100f
                }
                if (st.carnSpecies.any { it != d.species }) d.social -= 40f
                if (def.diet == Diet.HERBIVORE && st.hasCarn) d.social -= 40f
                d.social = d.social.coerceIn(0f, 100f)
            }
            // media ponderada, pero la peor necesidad pesa la mitad: un dino sin comida no se consuela con espacio
            val weighted = 0.30f * d.space + 0.30f * d.food + 0.20f * d.water + 0.20f * d.social
            val worst = minOf(d.space, d.food, d.water, d.social)
            var wb = 0.5f * weighted + 0.5f * worst
            if (d.sick) wb *= 0.6f
            d.wellbeing = wb

            // estrés
            var dS = 0f
            if (wb < 50f) {
                var rise = (50f - wb) / 20f
                if (d.geneResist && d.food < 40f) rise *= 0.75f
                if (d.geneTemper == 2) rise *= 1.3f
                dS += rise
            } else if (wb > 60f) dS -= (wb - 60f) / 20f
            if (w.stormActive && info != null && s.buildings.none { it.def.shelterDinos && grid.regionAt(it.x, it.y) == d.region }) dS += 0.5f
            if (d.state == DinoState.ESCAPED) dS = -0.5f
            d.stress = (d.stress + dS * dt).coerceIn(0f, 100f)

            if (d.stress >= 75f && d.state != DinoState.ESCAPED) w.alert("stress", "${def.name} está muy estresado", d.x.toInt(), d.y.toInt(), d.id, 15f)
            else if (d.stress >= 40f && d.state != DinoState.ESCAPED && wb < 50f) {
                val why = when {
                    d.food < 40f -> "tiene hambre"; d.water < 40f -> "tiene sed"; d.space < 50f -> "necesita más espacio"; d.social < 50f -> "necesita compañía"; else -> "está incómodo"
                }
                w.alert("need", "${def.name} $why", d.x.toInt(), d.y.toInt(), d.id, 12f)
            }

            behave(d, def, info, dt)
        }
        for (d in dead) kill(d, true)

        // caza: carnívoros con herbívoros en el mismo recinto
        for ((rid, st) in stats) {
            if (!(st.hasCarn && st.hasHerb)) { huntTimers.remove(rid); continue }
            val t = (huntTimers[rid] ?: 0f) + dt
            if (t >= 20f) {
                huntTimers[rid] = 0f
                val prey = s.dinos.filter { it.region == rid && it.def.diet == Diet.HERBIVORE && it.state != DinoState.ESCAPED }.randomOrNull(rnd)
                if (prey != null) {
                    w.alert("hunt", "¡Un carnívoro ha cazado a un ${prey.def.name}!", prey.x.toInt(), prey.y.toInt(), prey.id, 20f)
                    kill(prey, false)
                    for (o in s.dinos) if (o.region == rid) { if (o.def.diet == Diet.HERBIVORE) o.stress = 100f else o.food = 100f }
                }
            } else huntTimers[rid] = t
        }
    }

    private fun computeStats() {
        stats.clear()
        for (d in s.dinos) {
            if (d.state == DinoState.ESCAPED) continue
            val st = stats.getOrPut(d.region) { RegionStats() }
            st.count++
            st.perSpecies[d.species] = (st.perSpecies[d.species] ?: 0) + 1
            if (d.def.diet == Diet.CARNIVORE) { st.hasCarn = true; st.carnSpecies.add(d.species) } else st.hasHerb = true
        }
        for ((_, st) in stats) {
            val n = st.count
            val gf = max(0.6f, 1f - 0.05f * (n - 1))
            var demand = 0f
            for ((sp, c) in st.perSpecies) demand += GameData.species(sp).spaceMin * c * gf
            st.demand = demand
        }
    }

    private fun kill(d: Dino, natural: Boolean) {
        s.dinos.remove(d)
        s.dinoDeaths.add(s.time)
        s.corpses.add(Corpse(s.newId(), d.species, d.x, d.y, d.facing, if (d.def.size == Size.L) 90f else 45f))
        w.sfx("roar:${d.def.size.name}")
    }

    private fun escape(d: Dino) {
        w.sfx("roar:${d.def.size.name}")
        d.state = DinoState.ESCAPED
        d.region = 0
        d.targetEdge = -1
        d.tx = d.x; d.ty = d.y; d.path.clear()
        w.alert("escape", "¡FUGA! ${d.def.name} está suelto", d.x.toInt(), d.y.toInt(), d.id, 60f)
    }

    // ------------------------------------------------------------------ comportamiento
    private fun behave(d: Dino, def: SpeciesDef, info: RegionInfo?, dt: Float) {
        val speed = def.size.speed
        when (d.state) {
            DinoState.ESCAPED -> behaveEscaped(d, def, dt)
            DinoState.ATTACK, DinoState.TO_FENCE -> {
                if (d.stress < 75f || info == null) { d.state = DinoState.WANDER; d.targetEdge = -1; return }
                val e = if (d.targetEdge >= 0) EdgeRef.fromKey(d.targetEdge) else null
                if (e == null || grid.fenceType(e) == 0) { pickTargetEdge(d, info); return }
                val (mx, my) = edgeMid(e)
                val dist = abs(mx - d.x) + abs(my - d.y)
                if (dist < 1.1f) {
                    if (grid.fenceHp(e) <= 0) {
                        // hueco: salir
                        val (ox, oy) = outsideTile(e, d.region)
                        d.x = ox + 0.5f; d.y = oy + 0.5f
                        escape(d)
                        return
                    }
                    if (d.stress >= 100f) {
                        d.state = DinoState.ATTACK
                        d.attackCd -= dt
                        if (d.attackCd <= 0f) {
                            d.attackCd = 3f
                            var dmg = def.attack.toFloat()
                            if (d.geneTemper == 1) dmg *= 0.5f
                            if (grid.fenceType(e) == Fence.ELECTRIC && grid.edgePowered(e)) dmg *= 0.3f
                            val hp = max(0, grid.fenceHp(e) - dmg.toInt())
                            grid.setFenceHp(e, hp)
                            w.dirty = true
                            w.sfx("hit")
                            if (hp <= 0) { w.alert("fence", "¡Valla rota en ${info.name}!", e.x, e.y, -7, 40f); grid.rebuildRegions() }
                            else w.alert("attack", "${def.name} golpea la valla (${hp} PV)", e.x, e.y, d.id, 6f)
                        }
                    }
                } else if (arrived(d)) pickTargetEdge(d, info)   // la ruta acabó lejos del borde: elegir de nuevo
                else moveToward(d, speed * 1.2f, dt)
            }
            DinoState.SEEK_FOOD -> {
                if (arrived(d)) {
                    if (d.food < 100f) {
                        val feeder = nearestFeeder(d, def)
                        if (feeder != null && feeder.stock > 0) { feeder.stock -= def.size.feederUnits; d.food = min(100f, d.food + 40f) }
                        else if (def.diet == Diet.HERBIVORE && def.size == Size.L && (info?.forest ?: 0) > 0) d.food = min(100f, d.food + 40f)
                    }
                    d.state = DinoState.WANDER; d.idle = 1.5f
                } else moveToward(d, speed, dt)
            }
            DinoState.SEEK_WATER -> {
                if (arrived(d)) { d.water = min(100f, d.water + 40f); d.state = DinoState.WANDER; d.idle = 1.5f }
                else moveToward(d, speed, dt)
            }
            else -> { // WANDER
                if (info == null) { d.state = DinoState.ESCAPED; return }
                if (d.stress >= 75f) { pickTargetEdge(d, info); return }
                if (d.food < 40f) {
                    val f = nearestFeeder(d, def)
                    if (f != null && f.stock > 0 && setTargetAdjacent(d, f.x, f.y, f.w, f.h)) { d.state = DinoState.SEEK_FOOD; return }
                    if (def.diet == Diet.HERBIVORE && def.size == Size.L && info.forest > 0) {
                        val trees = info.tileList.filter { s.terrain[it] == Terrain.FOREST }.sortedBy { dist2(d, it) }
                        for (t in trees.take(4)) if (setTarget(d, t)) { d.state = DinoState.SEEK_FOOD; return }
                    }
                    if (f == null) w.alert("nofeeder", "${def.name}: sin comedero con comida", d.x.toInt(), d.y.toInt(), d.id, 10f)
                    else if (f.stock > 0) w.alert("nofeeder", "${def.name}: no puede llegar al comedero (desnivel)", d.x.toInt(), d.y.toInt(), d.id, 10f)
                }
                if (d.water < 40f) {
                    val waters = info.tileList.filter { s.terrain[it] == Terrain.WATER }.sortedBy { dist2(d, it) }
                    for (wt in waters.take(4)) {
                        // tile adyacente al agua alcanzable
                        for ((dx, dy) in dirs4) if (setTargetAdjacentTile(d, wt % w.n + dx, wt / w.n + dy)) { d.state = DinoState.SEEK_WATER; return }
                    }
                    val trough = s.buildings.filter { it.def.isWater && grid.regionAt(it.x, it.y) == d.region }.minByOrNull { abs(it.cx - d.x) + abs(it.cy - d.y) }
                    if (trough != null && setTargetAdjacent(d, trough.x, trough.y, 1, 1)) { d.state = DinoState.SEEK_WATER; return }
                    w.alert("nowater", "${def.name}: ${if (waters.isNotEmpty() || trough != null) "no puede llegar al agua (desnivel)" else "sin agua en el recinto"}", d.x.toInt(), d.y.toInt(), d.id, 10f)
                }
                if (arrived(d)) {
                    d.idle -= dt
                    if (d.idle <= 0f) {
                        d.idle = 1f + rnd.nextFloat() * 3f
                        val cand = info.tileList.filter { abs(it % w.n - d.x) < 5 && abs(it / w.n - d.y) < 5 && grid.dinoWalkableIdx(it) }
                        if (cand.isNotEmpty()) for (k in 0 until 4) if (setTarget(d, cand[rnd.nextInt(cand.size)])) break
                    }
                } else moveToward(d, speed * 0.6f, dt)
            }
        }
    }

    private fun behaveEscaped(d: Dino, def: SpeciesDef, dt: Float) {
        val speed = def.size.speed
        // contacto con visitantes
        if (def.danger >= 3 && d.hitCd <= 0f) {
            val v = s.visitors.minByOrNull { abs(it.x - d.x) + abs(it.y - d.y) }
            if (v != null && abs(v.x - d.x) + abs(v.y - d.y) < 0.9f) {
                d.hitCd = 5f
                if (rnd.nextFloat() < 0.3f) {
                    s.visitors.remove(v); s.visitorDeaths.add(s.time); s.reputation = max(0f, s.reputation - 5f)
                    w.spendOps(GameData.DEATH_COST.toFloat())
                    w.alert("vdeath", "Un visitante ha muerto. Indemnización ${GameData.DEATH_COST} $", d.x.toInt(), d.y.toInt(), -8, 30f)
                } else {
                    v.injured = true; v.state = VisitorState.LEAVE; v.path.clear()
                    s.injuries.add(s.time)
                    w.spendOps(GameData.INJURY_COST.toFloat())
                    w.alert("injury", "Visitante herido. Indemnización ${GameData.INJURY_COST} $", d.x.toInt(), d.y.toInt(), -9, 15f)
                }
                if (def.diet == Diet.CARNIVORE) d.food = 100f
            }
        }
        if (def.danger >= 5) {
            val v = s.visitors.filter { abs(it.x - d.x) + abs(it.y - d.y) <= 8f }.minByOrNull { abs(it.x - d.x) + abs(it.y - d.y) }
            if (v != null) {
                // persecución por ruta (los desniveles de dos bloques también frenan al dino); se recalcula cada medio segundo
                d.idle -= dt
                if (d.idle <= 0f || arrived(d)) { d.idle = 0.5f; setTarget(d, tileOf(v.x, v.y)) }
                if (!arrived(d)) { moveToward(d, speed * 1.4f, dt); return }
            }
        }
        if (arrived(d)) {
            d.idle -= dt
            if (d.idle <= 0f) {
                d.idle = 1f + rnd.nextFloat() * 2f
                for (i in 0 until 8) {
                    val tx = floor(d.x).toInt() + rnd.nextInt(-4, 5); val ty = floor(d.y).toInt() + rnd.nextInt(-4, 5)
                    if (s.inBounds(tx, ty) && escapedPassable(s.idx(tx, ty)) && setTarget(d, s.idx(tx, ty))) break
                }
            }
        } else moveToward(d, speed * 0.9f, dt)
    }

    private fun pickTargetEdge(d: Dino, info: RegionInfo) {
        if (info.edges.isEmpty()) { d.state = DinoState.WANDER; return }
        // solo bordes cuyo tile interior se alcanza con la regla de desnivel; primero huecos, luego la más débil
        val reach = grid.reachableFrom(tileOf(d.x, d.y), regionPassable(d.region))
        val reachable = info.edges.filter { val (ix, iy) = insideTile(it, d.region); s.inBounds(ix, iy) && reach[s.idx(ix, iy)] }
        if (reachable.isEmpty()) { d.state = DinoState.WANDER; d.targetEdge = -1; d.idle = 2f; return }
        val gap = reachable.firstOrNull { grid.fenceHp(it) <= 0 }
        val e = gap ?: reachable.minWithOrNull(compareBy({ Fence.strengthLevel(grid.fenceType(it), grid.edgePowered(it)) }, { grid.fenceHp(it) }, { val (mx, my) = edgeMid(it); abs(mx - d.x) + abs(my - d.y) }))!!
        val (ix, iy) = insideTile(e, d.region)
        if (!setTarget(d, s.idx(ix, iy))) { d.state = DinoState.WANDER; d.targetEdge = -1; d.idle = 2f; return }
        d.targetEdge = e.key()
        d.state = DinoState.TO_FENCE
    }

    private fun edgeMid(e: EdgeRef): Pair<Float, Float> = if (e.h) Pair(e.x + 0.5f, e.y.toFloat()) else Pair(e.x.toFloat(), e.y + 0.5f)

    private fun insideTile(e: EdgeRef, region: Int): Pair<Int, Int> {
        val a = if (e.h) Pair(e.x, e.y - 1) else Pair(e.x - 1, e.y)
        val b = if (e.h) Pair(e.x, e.y) else Pair(e.x, e.y)
        return if (grid.regionAt(a.first, a.second) == region) a else b
    }
    private fun outsideTile(e: EdgeRef, region: Int): Pair<Int, Int> {
        val a = if (e.h) Pair(e.x, e.y - 1) else Pair(e.x - 1, e.y)
        val b = if (e.h) Pair(e.x, e.y) else Pair(e.x, e.y)
        val out = if (grid.regionAt(a.first, a.second) == region) b else a
        return if (s.inBounds(out.first, out.second)) out else insideTile(e, region)
    }

    private fun nearestFeeder(d: Dino, def: SpeciesDef): Building? =
        s.buildings.filter { it.def.feederDiet == def.diet && grid.regionAt(it.x, it.y) == d.region }
            .minByOrNull { abs(it.cx - d.x) + abs(it.cy - d.y) }
            .let { f -> if (f != null && f.stock <= 0) s.buildings.filter { it.def.feederDiet == def.diet && grid.regionAt(it.x, it.y) == d.region && it.stock > 0 }.minByOrNull { abs(it.cx - d.x) + abs(it.cy - d.y) } ?: f else f }

    // ------------------------------------------------------------------ rutas
    private val dirs4 = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
    private fun tileOf(x: Float, y: Float) = s.idx(floor(x).toInt().coerceIn(0, w.n - 1), floor(y).toInt().coerceIn(0, w.n - 1))

    /** Tiles que puede pisar un dino de un recinto: suelo transitable de su misma región. */
    private fun regionPassable(region: Int): (Int) -> Boolean = { grid.region[it] == region && grid.dinoWalkableIdx(it) }
    /** Tiles que puede pisar un dino fugado: todo el exterior salvo agua, roca y edificios. */
    private val escapedPassable: (Int) -> Boolean = { grid.region[it] == 0 && s.terrain[it] != Terrain.WATER && s.terrain[it] != Terrain.ROCK && grid.buildingAt[it] < 0 }

    /**
     * Fija como objetivo el centro de un tile si hay ruta hasta él (4-conexa y con desnivel máximo de un nivel).
     * Devuelve false, sin tocar el objetivo actual, si el tile no se alcanza.
     */
    private fun setTarget(d: Dino, tile: Int): Boolean {
        val from = tileOf(d.x, d.y)
        val path = if (from == tile) mutableListOf() else grid.findPath(from, tile, if (d.state == DinoState.ESCAPED) escapedPassable else regionPassable(d.region))
        if (from != tile && path.isEmpty()) return false
        d.path = path; d.tx = tile % w.n + 0.5f; d.ty = tile / w.n + 0.5f
        return true
    }

    /** Objetivo en (x, y) si es suelo transitable de la región del dino y hay ruta. */
    private fun setTargetAdjacentTile(d: Dino, x: Int, y: Int): Boolean =
        grid.dinoWalkable(x, y) && grid.regionAt(x, y) == d.region && setTarget(d, s.idx(x, y))

    /** Objetivo en el tile libre alcanzable más cercano al perímetro de un edificio. */
    private fun setTargetAdjacent(d: Dino, x: Int, y: Int, bw: Int, bh: Int): Boolean {
        val cands = ArrayList<Int>()
        for (yy in y - 1..y + bh) for (xx in x - 1..x + bw) {
            if (xx in x until x + bw && yy in y until y + bh) continue
            if (grid.dinoWalkable(xx, yy) && grid.regionAt(xx, yy) == d.region) cands.add(s.idx(xx, yy))
        }
        cands.sortBy { dist2(d, it) }
        for (t in cands) if (setTarget(d, t)) return true
        return false
    }

    private fun dist2(d: Dino, t: Int): Float { val x = t % w.n + 0.5f; val y = t / w.n + 0.5f; return (x - d.x) * (x - d.x) + (y - d.y) * (y - d.y) }
    private fun arrived(d: Dino) = d.path.isEmpty() && abs(d.tx - d.x) + abs(d.ty - d.y) < 0.1f

    /** Avanza por la ruta (centros de tile) y al final en línea recta hasta (tx, ty). */
    private fun moveToward(d: Dino, speed: Float, dt: Float) {
        var budget = speed * dt
        var guard = 0
        while (budget > 1e-4f && guard++ < 8) {
            val wp = d.path.isNotEmpty()
            val gx = if (wp) d.path[0] % w.n + 0.5f else d.tx
            val gy = if (wp) d.path[0] / w.n + 0.5f else d.ty
            val dx = gx - d.x; val dy = gy - d.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > 1e-3f) d.facing = if (abs(dx) > abs(dy)) (if (dx > 0) 1 else 3) else (if (dy > 0) 2 else 0)
            if (dist > budget) { d.x += dx / dist * budget; d.y += dy / dist * budget; return }
            d.x = gx; d.y = gy; budget -= dist
            if (wp) d.path.removeAt(0) else return
        }
    }
}
