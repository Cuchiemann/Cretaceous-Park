package com.momentadesunt.cretaceouspark.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class Result(val ok: Boolean, val reason: String = "") {
    companion object { val OK = Result(true); fun fail(r: String) = Result(false, r) }
}

/** Mundo: estado + lógica. Todo corre en el hilo principal con tick fijo de 0,1 s. */
class World(var s: GameState) {
    var grid = Grid(s)
    val rnd = Random(s.seed xor (s.time * 1000).toLong())
    val dinoSim = DinoSim(this)
    val visitorSim = VisitorSim(this)

    // Mensajes transitorios para la interfaz (no se guardan)
    var toast: String? = null
    var toastTime = 0f
    var dirty = false           // el mapa cambió: la vista debe rehacer cachés
    var saveRequested = false
    var amberFlash = 0
    var cameraMoved = false          // lo marca la vista (paso 1 del tutorial)
    var tutorialJustCompleted = -1   // índice del paso recién cumplido, para la interfaz
    private var tutorialAcc = 0f

    private var secondAcc = 0f
    private var tenAcc = 0f
    // Base de la ventana de 10 s para las tasas por minuto: parte del libro guardado, no de cero
    private var lastIncome = s.ledgerIncome
    private var lastExpense = s.ledgerExpense

    val n get() = s.size
    val def get() = s.def

    fun say(msg: String) { toast = msg; toastTime = 3.5f }

    // ------------------------------------------------------------------ economía
    fun earn(base: Float) { val amount = base * GameData.INCOME_MULT; s.money += amount; s.ledgerIncome += amount; s.totalIncome += amount }
    fun spendOps(amount: Float) { s.money -= amount; s.ledgerExpense += amount; s.totalExpense += amount }
    fun spendCapital(amount: Float) { s.money -= amount; s.totalExpense += amount }
    fun canAfford(amount: Int) = s.money >= amount || def.sandbox

    fun hasBuilding(type: String) = s.buildings.any { it.type == type }
    fun countBuilding(type: String) = s.buildings.count { it.type == type }
    fun researched(id: String?) = id == null || s.researchDone.contains(id)

    fun alert(kind: String, text: String, x: Int, y: Int, entityId: Int = -1, ttl: Float = 30f) {
        val existing = s.alerts.firstOrNull { it.kind == kind && it.entityId == entityId }
        if (existing != null) { existing.ttl = ttl; return }
        if (s.alerts.size >= 6) s.alerts.removeAt(0)
        s.alerts.add(Alert(kind, text, x, y, entityId, ttl))
    }

    // ------------------------------------------------------------------ tick
    fun tick(dt: Float) {
        if (s.gameOver) return
        s.time += dt
        if (toastTime > 0f) { toastTime -= dt; if (toastTime <= 0f) toast = null }
        if (s.generatorOff > 0f) { s.generatorOff -= dt; if (s.generatorOff <= 0f) { s.generatorOff = 0f; grid.rebuildPower(); grid.rebuildRegions() } }

        // mantenimiento
        var upkeep = 0f
        for (b in s.buildings) upkeep += b.def.upkeep
        for (t in s.hType) if (t != 0) upkeep += Fence.upkeep[t]
        for (t in s.vType) if (t != 0) upkeep += Fence.upkeep[t]
        if (!def.sandbox) spendOps(upkeep / 60f * dt)

        // comederos automaticos (investigacion B1): reponen al bajar de 10 raciones
        if (s.researchDone.contains("B1")) for (b in s.buildings) {
            val fd = b.def
            if (fd.feederDiet != null && b.stock <= 10 && canAfford(fd.refillCost)) { spendOps(fd.refillCost.toFloat()); b.stock = fd.stock }
        }
        dinoSim.update(dt)
        visitorSim.update(dt)
        updateResearch(dt)
        updateExpeditions(dt)
        updateIncubations(dt)
        updateEvents(dt)

        if (s.tutorialActive) {
            tutorialAcc += dt
            if (tutorialAcc >= 0.5f) { tutorialAcc = 0f; updateTutorial() }
        }

        for (a in s.alerts) a.ttl -= dt
        s.alerts.removeAll { it.ttl <= 0f }
        if (dartCooldown > 0f) dartCooldown -= dt

        secondAcc += dt
        if (secondAcc >= 1f) {
            secondAcc -= 1f
            if (s.generatorOff <= 0f) grid.rebuildPower()
            updateRating()
            cleanupHistory()
        }
        tenAcc += dt
        if (tenAcc >= 10f) {
            tenAcc -= 10f
            val inc = (s.ledgerIncome - lastIncome) * 6f
            val exp = (s.ledgerExpense - lastExpense) * 6f
            lastIncome = s.ledgerIncome; lastExpense = s.ledgerExpense
            s.incomeMin = s.incomeMin * 0.5f + inc * 0.5f
            s.expenseMin = s.expenseMin * 0.5f + exp * 0.5f
        }

        // quiebra
        if (!def.sandbox && s.money < 0) {
            s.bankruptTimer += dt
            if (s.bankruptTimer > 20f) alert("bankrupt", "¡Saldo negativo! Quiebra en ${(60 - s.bankruptTimer).roundToInt()} s", -1, -1, -2, 2f)
            if (s.bankruptTimer >= 60f) { s.gameOver = true; say("El parque ha quebrado.") }
        } else s.bankruptTimer = 0f

        s.autosave += dt
        if (s.autosave >= GameData.AUTOSAVE_SECONDS) { s.autosave = 0f; saveRequested = true }
    }

    private fun cleanupHistory() {
        val t = s.time
        s.injuries.removeAll { t - it > 180f }
        s.visitorDeaths.removeAll { t - it > 300f }
        s.dinoDeaths.removeAll { t - it > 300f }
    }

    // ------------------------------------------------------------------ valoración
    private fun updateRating() {
        if (def.sandbox) return
        val net = s.incomeMin - s.expenseMin
        val profit = (100f * net / def.incomeTarget).coerceIn(0f, 100f)

        var pen = 0f
        val escaped = s.dinos.count { it.state == DinoState.ESCAPED }
        if (escaped > 0) pen += 40f
        pen += 5f * s.injuries.size
        pen += 20f * s.visitorDeaths.size
        var weakFence = 0f
        val hasRangers = hasBuilding("ranger_station")
        var carn = false
        for (d in s.dinos) {
            if (d.def.diet == Diet.CARNIVORE) carn = true
            val r = grid.regions[d.region]
            if (r != null && r.weakestLevel < d.def.fenceMin) weakFence += 5f
        }
        pen += min(30f, weakFence)
        if (carn && !hasRangers) pen += 15f
        val shelter = s.buildings.sumOf { it.def.shelterCapacity }
        if (s.visitors.size > 50 && shelter == 0) pen += 10f
        val safety = (100f - pen).coerceIn(0f, 100f)

        val welfare = if (s.dinos.isEmpty()) 0f
        else (s.dinos.map { it.wellbeing }.average().toFloat() - 10f * s.dinoDeaths.size).coerceIn(0f, 100f)

        val alive = s.dinos.map { it.species }.toSet()
        val comfort = if (s.visitors.isEmpty()) 50f else s.visitors.map { it.comfort() }.average().toFloat()
        val attrSum = alive.sumOf { GameData.species(it).attraction }
        val attraction = (40f * min(1f, alive.size / 8f) + 30f * comfort / 100f + 30f * min(1f, attrSum / 40f)).coerceIn(0f, 100f)

        val k = 0.08f
        s.idxProfit += (profit - s.idxProfit) * k
        s.idxSafety += (safety - s.idxSafety) * k
        s.idxWelfare += (welfare - s.idxWelfare) * k
        s.idxAttraction += (attraction - s.idxAttraction) * k

        val avg = (s.idxProfit + s.idxSafety + s.idxWelfare + s.idxAttraction) / 4f
        var stars = (avg / 20f * 2f).roundToInt() / 2f
        val allHigh = s.idxProfit >= 90f && s.idxSafety >= 90f && s.idxWelfare >= 90f && s.idxAttraction >= 90f
        if (!allHigh) stars = min(stars, 4.5f)
        // Un parque sin dinosaurios no puntúa: evita estrellas y Ámbar gratis al empezar
        if (s.dinos.isEmpty()) stars = 0f
        s.stars = stars

        val fullStars = floor(stars).toInt()
        if (fullStars > s.amberStarsAwarded) {
            val gained = (fullStars - s.amberStarsAwarded) * 15
            s.amberStarsAwarded = fullStars
            s.amberEarned += gained; amberFlash += gained
            say("¡$fullStars estrellas! +$gained Ámbar")
        }
        if (stars >= 5f) {
            s.fiveStarTimer += 1f
            if (s.fiveStarTimer >= 60f && !s.completed) { s.completed = true; s.amberEarned += 100; amberFlash += 100; say("¡Isla completada! +100 Ámbar") }
        } else s.fiveStarTimer = 0f

        s.reputation += (avg - s.reputation) / 60f
        s.reputation = s.reputation.coerceIn(0f, 100f)
    }

    // ------------------------------------------------------------------ investigación
    fun researchCenters() = s.buildings.filter { it.type == "research_center" }
    fun researchRate(): Float = researchCenters().sumOf { (10.0 + if (grid.powered[s.idx(it.x, it.y)] || grid.powered[s.idx(it.x + it.w - 1, it.y + it.h - 1)]) 5.0 else 0.0) }.toFloat()

    private fun updateResearch(dt: Float) {
        val centers = researchCenters()
        if (centers.isEmpty()) return
        s.researchPoints += researchRate() / 60f * dt
        val active = s.researchActive ?: return
        val def = GameData.researchById[active] ?: run { s.researchActive = null; return }
        s.researchProgress += dt
        if (s.researchProgress >= def.seconds) {
            s.researchDone.add(active)
            s.researchActive = null
            s.researchProgress = 0f
            when (active) {
                "E6" -> s.reputation = min(100f, s.reputation + 10f)
                "G1" -> s.sitesUnlocked.add("estepa_gris")
                "G4" -> s.sitesUnlocked.add("costa_de_sal")
                "B2" -> grid.rebuildRegions()
            }
            say("Investigación completada: ${def.name}")
        }
    }

    fun canResearch(id: String): Result {
        val def = GameData.researchById[id] ?: return Result.fail("Nodo desconocido")
        if (s.researchDone.contains(id)) return Result.fail("Ya investigado")
        if (s.researchActive != null) return Result.fail("Ya hay una investigación en curso")
        if (researchCenters().isEmpty()) return Result.fail("Construye un Centro de Investigación")
        if (def.prereqs.any { !s.researchDone.contains(it) }) return Result.fail("Requiere: " + def.prereqs.filter { !s.researchDone.contains(it) }.joinToString { GameData.researchById[it]!!.name })
        if (s.researchPoints < def.pi) return Result.fail("Faltan puntos de investigación (${s.researchPoints.toInt()}/${def.pi})")
        if (!canAfford(def.cost)) return Result.fail("Dinero insuficiente")
        return Result.OK
    }

    fun startResearch(id: String): Result {
        val r = canResearch(id); if (!r.ok) return r
        val def = GameData.researchById.getValue(id)
        s.researchPoints -= def.pi
        spendCapital(def.cost.toFloat())
        s.researchActive = id; s.researchProgress = 0f
        return Result.OK
    }

    // ------------------------------------------------------------------ expediciones
    fun maxTeams() = if (s.researchDone.contains("G2")) 2 else 1
    fun siteUnlocked(site: SiteDef): Boolean = s.sitesUnlocked.contains(site.id)

    fun canExpedition(siteId: String): Result {
        val site = GameData.siteById[siteId] ?: return Result.fail("Yacimiento desconocido")
        if (!hasBuilding("expedition_hq")) return Result.fail("Construye un Centro de Expediciones")
        if (!siteUnlocked(site)) return Result.fail("Yacimiento bloqueado")
        if (s.expeditions.size >= maxTeams()) return Result.fail("Todos los equipos están fuera")
        if (!canAfford(site.cost)) return Result.fail("Dinero insuficiente")
        return Result.OK
    }

    fun startExpedition(siteId: String): Result {
        val r = canExpedition(siteId); if (!r.ok) return r
        val site = GameData.siteById.getValue(siteId)
        spendCapital(site.cost.toFloat())
        s.expeditions.add(Expedition(siteId, site.seconds))
        return Result.OK
    }

    private fun updateExpeditions(dt: Float) {
        val it = s.expeditions.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.remaining -= dt
            if (e.remaining <= 0f) {
                it.remove()
                val site = GameData.siteById.getValue(e.site)
                var msg = "Expedición a ${site.name}: "
                val parts = ArrayList<String>()
                repeat(site.fossils) {
                    val sp = site.species[rnd.nextInt(site.species.size)]
                    val roll = rnd.nextFloat()
                    val (q, dna) = when {
                        roll < 0.50f -> "Baja" to rnd.nextInt(5, 11)
                        roll < 0.85f -> "Media" to rnd.nextInt(10, 21)
                        roll < 0.98f -> "Alta" to rnd.nextInt(20, 36)
                        else -> "Raro" to 35
                    }
                    val bonus = if (s.researchDone.contains("G6")) 5 else 0
                    val cur = s.dna[sp] ?: 0
                    var sold = 0
                    if (cur >= 100) {
                        sold = when (q) { "Baja" -> 1000; "Media" -> 2500; else -> 5000 }
                        earn(sold.toFloat())
                    } else s.dna[sp] = min(100, cur + dna + bonus)
                    if (q == "Raro") { s.amberEarned += 5; amberFlash += 5 }
                    s.fossilLog.add(0, FossilLog(sp, q, dna + bonus, sold))
                    parts.add("${GameData.species(sp).name} ($q)")
                }
                while (s.fossilLog.size > 12) s.fossilLog.removeAt(s.fossilLog.size - 1)
                msg += parts.joinToString(", ")
                say(msg)
            }
        }
    }

    // ------------------------------------------------------------------ incubación
    fun maxIncubations() = if (s.researchDone.contains("G6")) 3 else 2
    fun viability(species: String, genes: Int): Int {
        val dna = s.dna[species] ?: 0
        if (dna < 50) return 0
        return (55 + 43 * (dna - 50) / 50 - 5 * genes).coerceIn(5, 98)
    }

    fun canIncubate(species: String, region: Int): Result {
        val lab = s.buildings.firstOrNull { it.type == "lab" } ?: return Result.fail("Construye un Laboratorio")
        if (!lab.powered) return Result.fail("El Laboratorio no tiene energía")
        val sp = GameData.species(species)
        if ((s.dna[species] ?: 0) < 50) return Result.fail("ADN insuficiente (mínimo 50 %)")
        if (s.incubations.size >= maxIncubations()) return Result.fail("Incubadoras ocupadas")
        if (!canAfford(sp.cost)) return Result.fail("Dinero insuficiente")
        val r = grid.regions[region] ?: return Result.fail("Elige un recinto cerrado")
        if (r.tileList.none { grid.dinoWalkable(it % n, it / n) }) return Result.fail("El recinto no tiene suelo libre")
        return Result.OK
    }

    /** Avisos no bloqueantes sobre el recinto elegido. */
    fun incubationWarnings(species: String, region: Int): List<String> {
        val out = ArrayList<String>()
        val sp = GameData.species(species)
        val r = grid.regions[region] ?: return out
        if (r.weakestLevel < sp.fenceMin) out.add("Valla insuficiente: necesita ${Fence.names[sp.fenceMin]}")
        val others = s.dinos.filter { it.region == region }
        val demand = (others.sumOf { it.def.spaceMin } + sp.spaceMin)
        if (r.tiles - r.water < demand) out.add("Poco espacio: ${r.tiles - r.water} tiles para $demand necesarios")
        if (r.water + (if (s.researchDone.contains("B2")) 0 else 0) < 1) out.add("Sin agua en el recinto")
        if (sp.requiresWaterTiles > r.water) out.add("Necesita ${sp.requiresWaterTiles} tiles de agua")
        if (s.buildings.none { it.def.feederDiet == sp.diet && grid.regionAt(it.x, it.y) == region }) out.add("Sin comedero de ${if (sp.diet == Diet.HERBIVORE) "herbívoros" else "carnívoros"}")
        if (sp.diet == Diet.CARNIVORE && others.any { it.species != species }) out.add("Los carnívoros solo conviven con su especie")
        if (sp.diet == Diet.HERBIVORE && others.any { it.def.diet == Diet.CARNIVORE }) out.add("¡Hay carnívoros en el recinto!")
        return out
    }

    fun startIncubation(species: String, region: Int, genes: Int = 0): Result {
        val r = canIncubate(species, region); if (!r.ok) return r
        val sp = GameData.species(species)
        spendCapital(sp.cost.toFloat())
        s.incubations.add(Incubation(species, region, sp.size.incubationSeconds, genes))
        return Result.OK
    }

    private fun updateIncubations(dt: Float) {
        val it = s.incubations.iterator()
        while (it.hasNext()) {
            val inc = it.next()
            inc.remaining -= dt
            if (inc.remaining <= 0f) {
                it.remove()
                val sp = GameData.species(inc.species)
                val v = viability(inc.species, inc.genes)
                if (rnd.nextInt(100) < v) {
                    val d = spawnDino(inc.species, inc.region, inc.genes)
                    if (d != null) {
                        say("¡${sp.name} incubado con éxito!")
                        if (!s.cloned.contains(inc.species)) { s.cloned.add(inc.species); s.amberEarned += 10; amberFlash += 10 }
                    } else say("Incubación fallida: el recinto ya no existe")
                } else say("Incubación fallida (${sp.name}). Viabilidad $v %")
            }
        }
    }

    fun spawnDino(species: String, region: Int, genes: Int = 0): Dino? {
        val r = grid.regions[region] ?: return null
        val free = r.tileList.filter { grid.dinoWalkable(it % n, it / n) }
        if (free.isEmpty()) return null
        val t = free[rnd.nextInt(free.size)]
        val x = t % n + 0.5f; val y = t / n + 0.5f
        val d = Dino(s.newId(), species, x, y, x, y, region = region, homeRegion = region,
            skin = if (genes and 1 != 0) 1 else 0, geneResist = genes and 2 != 0, geneTemper = if (genes and 4 != 0) 1 else if (genes and 8 != 0) 2 else 0)
        s.dinos.add(d)
        return d
    }

    // ------------------------------------------------------------------ eventos
    private fun updateEvents(dt: Float) {
        val d = def
        if (s.eventType == EventType.NONE) {
            if (s.dinos.none { it.state == DinoState.ESCAPED }) s.nextEvent -= dt
            if (s.nextEvent <= 0f) scheduleEvent()
            return
        }
        if (s.eventWarn > 0f) {
            s.eventWarn -= dt
            if (s.eventWarn <= 0f) activateEvent()
            return
        }
        when (s.eventType) {
            EventType.STORM -> {
                s.eventTimer -= dt
                stormAcc += dt
                if (stormAcc >= 10f) {
                    stormAcc -= 10f
                    val p = when (d.stormLevel) { 1 -> 0.10f; 2 -> 0.20f; else -> 0.30f }
                    var damaged = false
                    for (i in s.hType.indices) if (s.hType[i] != 0 && s.hHp[i] > 0 && rnd.nextFloat() < p) { s.hHp[i] = max(0, s.hHp[i] - 30); damaged = true }
                    for (i in s.vType.indices) if (s.vType[i] != 0 && s.vHp[i] > 0 && rnd.nextFloat() < p) { s.vHp[i] = max(0, s.vHp[i] - 30); damaged = true }
                    if (damaged) dirty = true
                    if (stormAcc30++ % 3 == 2 && rnd.nextFloat() < 0.2f && hasBuilding("generator")) { s.generatorOff = 30f; grid.rebuildPower(); grid.rebuildRegions(); alert("power", "Tormenta: generador apagado", -1, -1, -3, 8f) }
                }
                if (s.eventTimer <= 0f) endEvent("La tormenta ha pasado")
            }
            else -> endEvent(null)
        }
    }
    private var stormAcc = 0f
    private var stormAcc30 = 0

    val stormActive get() = s.eventType == EventType.STORM && s.eventWarn <= 0f

    private fun scheduleEvent() {
        val d = def
        val options = ArrayList<Pair<Int, Int>>()
        if (d.wStorm > 0) options.add(EventType.STORM to d.wStorm)
        if (d.wDisease > 0 && s.dinos.isNotEmpty()) options.add(EventType.DISEASE to d.wDisease)
        if (d.wEscape > 0 && s.dinos.any { it.def.danger >= 3 && it.state != DinoState.ESCAPED }) options.add(EventType.ESCAPE to d.wEscape)
        if (d.wSabotage > 0 && s.buildings.any { it.type == "generator" || it.def.feederDiet != null }) options.add(EventType.SABOTAGE to d.wSabotage)
        if (options.isEmpty()) { s.nextEvent = 60f; return }
        val total = options.sumOf { it.second }
        var roll = rnd.nextInt(total)
        var chosen = options[0].first
        for ((t, w) in options) { if (roll < w) { chosen = t; break }; roll -= w }
        s.eventType = chosen
        s.eventWarn = when (chosen) { EventType.STORM -> 20f; EventType.ESCAPE -> 15f; else -> 10f }
        s.eventText = when (chosen) {
            EventType.STORM -> "Se acerca una tormenta"
            EventType.DISEASE -> "Un dinosaurio parece enfermo"
            EventType.ESCAPE -> "Un dinosaurio se está alterando"
            else -> "Actividad sospechosa en el parque"
        }
        s.eventTarget = when (chosen) {
            EventType.DISEASE -> s.dinos.filter { !it.sick }.randomOrNull(rnd)?.id ?: -1
            EventType.ESCAPE -> s.dinos.filter { it.def.danger >= 3 && it.state != DinoState.ESCAPED }.randomOrNull(rnd)?.id ?: -1
            else -> -1
        }
        val target = s.dinos.firstOrNull { it.id == s.eventTarget }
        alert("event", s.eventText, target?.x?.toInt() ?: -1, target?.y?.toInt() ?: -1, target?.id ?: -4, s.eventWarn + 2f)
        if (chosen == EventType.ESCAPE) target?.let { it.stress = max(it.stress, 60f) }
    }

    private fun activateEvent() {
        when (s.eventType) {
            EventType.STORM -> {
                s.eventTimer = when (def.stormLevel) { 1 -> 60f; 2 -> 90f; else -> 120f }
                s.eventText = "¡Tormenta!"
                stormAcc = 0f
                alert("storm", "¡Tormenta! Las vallas sufren daños", -1, -1, -5, s.eventTimer)
            }
            EventType.DISEASE -> {
                val d = s.dinos.firstOrNull { it.id == s.eventTarget } ?: s.dinos.randomOrNull(rnd)
                if (d != null && !(d.geneResist && rnd.nextFloat() < 0.6f) && !(s.researchDone.contains("B5") && rnd.nextFloat() < 0.5f)) {
                    d.sick = true; d.sickTimer = 0f
                    alert("sick", "${d.def.name} está enfermo", d.x.toInt(), d.y.toInt(), d.id, 60f)
                }
                endEvent(null)
            }
            EventType.ESCAPE -> {
                val d = s.dinos.firstOrNull { it.id == s.eventTarget }
                if (d != null) { d.stress = 100f; alert("stress", "${d.def.name} intenta romper la valla", d.x.toInt(), d.y.toInt(), d.id, 30f) }
                endEvent(null)
            }
            EventType.SABOTAGE -> {
                val gens = s.buildings.filter { it.type == "generator" }
                val feeders = s.buildings.filter { it.def.feederDiet != null && it.stock > 0 }
                val gates = allEdges().filter { grid.fenceFlags(it) and EdgeFlag.GATE != 0 }
                val roll = rnd.nextInt(3)
                val nearRangers = s.buildings.any { it.type == "ranger_station" }
                when {
                    roll == 0 && gens.isNotEmpty() -> { s.generatorOff = if (nearRangers) 30f else 60f; grid.rebuildPower(); grid.rebuildRegions(); alert("power", "¡Sabotaje! Generador apagado", gens[0].x, gens[0].y, -3, 10f) }
                    roll == 1 && gates.isNotEmpty() -> { val g = gates[rnd.nextInt(gates.size)]; grid.setFence(g, grid.fenceType(g), grid.fenceHp(g), EdgeFlag.GATE or EdgeFlag.OPEN); grid.rebuildRegions(); dirty = true; alert("gate", "¡Sabotaje! Puerta abierta", g.x, g.y, -6, 15f) }
                    feeders.isNotEmpty() -> { val f = feeders[rnd.nextInt(feeders.size)]; f.stock = 0; alert("feeder", "¡Sabotaje! Comedero vaciado", f.x, f.y, f.id, 15f) }
                    gens.isNotEmpty() -> { s.generatorOff = 60f; grid.rebuildPower(); grid.rebuildRegions(); alert("power", "¡Sabotaje! Generador apagado", gens[0].x, gens[0].y, -3, 10f) }
                }
                endEvent(null)
            }
        }
    }

    private fun endEvent(msg: String?) {
        s.eventType = EventType.NONE
        s.eventText = ""
        s.nextEvent = def.eventMin + rnd.nextFloat() * (def.eventMax - def.eventMin)
        if (msg != null) say(msg)
    }

    fun allEdges(): List<EdgeRef> {
        val out = ArrayList<EdgeRef>()
        for (y in 0..n) for (x in 0 until n) if (s.hType[s.hIdx(x, y)] != 0) out.add(EdgeRef(true, x, y))
        for (y in 0 until n) for (x in 0..n) if (s.vType[s.vIdx(x, y)] != 0) out.add(EdgeRef(false, x, y))
        return out
    }

    // ------------------------------------------------------------------ comandos: construcción
    fun buildingUnlocked(def: BuildingDef): Result {
        if (def.id == "entrance") return Result.fail("Ya existe")
        if (!researched(def.research)) return Result.fail("Requiere investigar: ${GameData.researchById[def.research]?.name}")
        if (countBuilding(def.id) >= def.maxCount) return Result.fail("Máximo ${def.maxCount}")
        return Result.OK
    }

    fun canPlaceBuilding(def: BuildingDef, x: Int, y: Int): Result {
        val u = buildingUnlocked(def); if (!u.ok) return u
        if (!canAfford(def.cost)) return Result.fail("Dinero insuficiente (${def.cost} $)")
        var region = -1
        for (yy in y until y + def.h) for (xx in x until x + def.w) {
            if (!s.inBounds(xx, yy)) return Result.fail("Fuera de la isla")
            val t = s.terrainAt(xx, yy)
            if (!Terrain.isBuildable(t)) return Result.fail(when (t) { Terrain.WATER -> "Hay agua"; Terrain.FOREST -> "Hay bosque: tala primero"; Terrain.ROCK -> "Hay roca"; else -> "Hay un camino" })
            if (grid.buildingAt(xx, yy) != null) return Result.fail("Ya hay un edificio")
            val r = grid.regionAt(xx, yy)
            if (region == -1) region = r else if (region != r) return Result.fail("Cruza una valla")
        }
        if (def.insideEnclosure && region == 0) return Result.fail("Debe ir dentro de un recinto cerrado")
        if (!def.insideEnclosure && region != 0) return Result.fail("No puede ir dentro de un recinto")
        if (def.needsPath && !grid.hasAdjacentPath(x, y, def.w, def.h)) return Result.fail("Necesita un camino adyacente")
        if (def.attachToFence && grid.fenceEdgesAround(x, y, def.w, def.h).isEmpty()) return Result.fail("Debe estar pegado a una valla")
        return Result.OK
    }

    fun placeBuilding(defId: String, x: Int, y: Int): Result {
        val def = GameData.building(defId)
        val r = canPlaceBuilding(def, x, y); if (!r.ok) return r
        spendCapital(def.cost.toFloat())
        val b = Building(s.newId(), defId, x, y, stock = def.stock)
        s.buildings.add(b)
        grid.rebuildBuildings(); grid.rebuildPower(); grid.rebuildRegions()
        dirty = true
        return Result.OK
    }

    fun demolishBuilding(b: Building): Result {
        if (b.type == "entrance") return Result.fail("La entrada no se puede demoler")
        s.buildings.remove(b)
        earn(0f); s.money += b.def.cost * GameData.DEMOLISH_REFUND
        // visitantes que la usaban
        for (v in s.visitors) if (v.targetBuilding == b.id) { v.targetBuilding = -1; v.state = VisitorState.WANDER; v.path.clear() }
        grid.rebuildBuildings(); grid.rebuildPower(); grid.rebuildRegions()
        dirty = true
        return Result.OK
    }

    fun refillFeeder(b: Building): Result {
        val def = b.def
        if (def.feederDiet == null) return Result.fail("No es un comedero")
        if (!canAfford(def.refillCost)) return Result.fail("Dinero insuficiente")
        spendOps(def.refillCost.toFloat())
        b.stock = def.stock
        return Result.OK
    }

    // ------------------------------------------------------------------ comandos: vallas
    fun fenceUnlocked(type: Int): Result {
        if (type !in 1..4) return Result.fail("Tipo de valla inválido")
        if (!researched(Fence.research[type])) return Result.fail("Requiere investigar: ${GameData.researchById[Fence.research[type]]?.name}")
        return Result.OK
    }

    fun edgeValid(e: EdgeRef): Boolean = if (e.h) e.x in 0 until n && e.y in 0..n else e.x in 0..n && e.y in 0 until n

    fun canPlaceFence(e: EdgeRef, type: Int): Result {
        val u = fenceUnlocked(type); if (!u.ok) return u
        if (!edgeValid(e)) return Result.fail("Fuera de la isla")
        val (a, b) = if (e.h) Pair(Pair(e.x, e.y - 1), Pair(e.x, e.y)) else Pair(Pair(e.x - 1, e.y), Pair(e.x, e.y))
        val landA = s.inBounds(a.first, a.second) && s.terrainAt(a.first, a.second) != Terrain.WATER
        val landB = s.inBounds(b.first, b.second) && s.terrainAt(b.first, b.second) != Terrain.WATER
        if (!landA && !landB) return Result.fail("Sin tierra firme")
        if (grid.isPathTile(a.first, a.second) || grid.isPathTile(b.first, b.second)) return Result.fail("No se puede vallar junto a un camino")
        val ba = grid.buildingAt(a.first, a.second); val bb = grid.buildingAt(b.first, b.second)
        if (ba != null && bb != null && ba === bb) return Result.fail("Atraviesa un edificio")
        if (grid.fenceType(e) == type) return Result.fail("Ya hay esa valla")
        if (!canAfford(Fence.cost[type])) return Result.fail("Dinero insuficiente")
        return Result.OK
    }

    fun placeFence(e: EdgeRef, type: Int, rebuild: Boolean = true): Result {
        val r = canPlaceFence(e, type); if (!r.ok) return r
        spendCapital(Fence.cost[type].toFloat())
        val keepGate = grid.fenceFlags(e) and EdgeFlag.GATE
        grid.setFence(e, type, Fence.hp[type], keepGate)
        if (rebuild) { grid.rebuildRegions(); dirty = true }
        return Result.OK
    }

    /** Motivo más frecuente por el que fallaron tramos en el último fenceRect. */
    var lastFenceFail: String = ""

    /** Valla el perímetro de un rectángulo de tiles (inclusive). Devuelve tramos colocados. */
    fun fenceRect(x0: Int, y0: Int, x1: Int, y1: Int, type: Int): Int {
        val ax = min(x0, x1); val bx = max(x0, x1); val ay = min(y0, y1); val by = max(y0, y1)
        var placed = 0
        val fails = HashMap<String, Int>()
        fun tryPlace(e: EdgeRef) {
            val r = placeFence(e, type, false)
            if (r.ok) placed++ else if (r.reason != "Ya hay esa valla") fails[r.reason] = (fails[r.reason] ?: 0) + 1
        }
        for (x in ax..bx) { tryPlace(EdgeRef(true, x, ay)); tryPlace(EdgeRef(true, x, by + 1)) }
        for (y in ay..by) { tryPlace(EdgeRef(false, ax, y)); tryPlace(EdgeRef(false, bx + 1, y)) }
        lastFenceFail = fails.maxByOrNull { it.value }?.let { "${it.value} tramos: ${it.key}" } ?: ""
        if (placed > 0) { grid.rebuildRegions(); dirty = true }
        return placed
    }

    fun removeFence(e: EdgeRef): Result {
        if (!edgeValid(e) || grid.fenceType(e) == 0) return Result.fail("No hay valla")
        s.money += Fence.cost[grid.fenceType(e)] * GameData.DEMOLISH_REFUND
        grid.setFence(e, 0, 0, 0)
        grid.rebuildRegions(); dirty = true
        // edificios pegados a valla que se quedan sin ella siguen existiendo (simplificación)
        return Result.OK
    }

    fun repairFence(e: EdgeRef): Result {
        val t = grid.fenceType(e); if (t == 0) return Result.fail("No hay valla")
        if (grid.fenceHp(e) >= Fence.hp[t]) return Result.fail("La valla está intacta")
        val cost = (Fence.cost[t] * GameData.REPAIR_FRACTION).roundToInt()
        if (!canAfford(cost)) return Result.fail("Dinero insuficiente")
        spendOps(cost.toFloat())
        grid.setFenceHp(e, Fence.hp[t]); dirty = true
        return Result.OK
    }

    fun repairAllFences(): Int {
        var count = 0
        for (e in allEdges()) if (grid.fenceHp(e) < Fence.hp[grid.fenceType(e)]) { if (repairFence(e).ok) count++ else break }
        return count
    }

    fun toggleGate(e: EdgeRef): Result {
        val t = grid.fenceType(e); if (t == 0) return Result.fail("No hay valla")
        val f = grid.fenceFlags(e)
        val nf = if (f and EdgeFlag.GATE == 0) {
            if (!canAfford(200)) return Result.fail("Dinero insuficiente (200 $)")
            spendCapital(200f); EdgeFlag.GATE
        } else f xor EdgeFlag.OPEN
        grid.setFence(e, t, grid.fenceHp(e), nf)
        grid.rebuildRegions(); dirty = true
        return Result.OK
    }

    // ------------------------------------------------------------------ comandos: caminos y terreno
    fun isEntrancePath(x: Int, y: Int): Boolean {
        val e = s.buildings.firstOrNull { it.type == "entrance" } ?: return false
        return y == e.y - 1 && x >= e.x && x < e.x + e.w
    }

    fun canPlacePath(x: Int, y: Int): Result {
        if (!s.inBounds(x, y)) return Result.fail("Fuera de la isla")
        val t = s.terrainAt(x, y)
        if (Terrain.isWalkablePath(t)) return Result.fail("Ya hay camino")
        if (t == Terrain.FOREST) return Result.fail("Hay bosque: tala primero")
        if (t == Terrain.ROCK) return Result.fail("Hay roca")
        if (grid.buildingAt(x, y) != null) return Result.fail("Hay un edificio")
        if (grid.regionAt(x, y) != 0) return Result.fail("No dentro de un recinto")
        // no junto a valla
        if (grid.fenceEdgesAround(x, y, 1, 1).isNotEmpty()) return Result.fail("Hay una valla pegada")
        val cost = if (t == Terrain.WATER) 150 else 20
        if (!canAfford(cost)) return Result.fail("Dinero insuficiente")
        return Result.OK
    }

    fun placePath(x: Int, y: Int): Result {
        val r = canPlacePath(x, y); if (!r.ok) return r
        val water = s.terrainAt(x, y) == Terrain.WATER
        spendCapital(if (water) 150f else 20f)
        s.terrain[s.idx(x, y)] = if (water) Terrain.BRIDGE else Terrain.PATH
        dirty = true
        return Result.OK
    }

    fun removePath(x: Int, y: Int): Result {
        if (!s.inBounds(x, y) || !Terrain.isWalkablePath(s.terrainAt(x, y))) return Result.fail("No hay camino")
        if (isEntrancePath(x, y)) return Result.fail("El camino de la entrada es fijo")
        val wasBridge = s.terrainAt(x, y) == Terrain.BRIDGE
        s.terrain[s.idx(x, y)] = if (wasBridge) Terrain.WATER else Terrain.GRASS
        s.money += (if (wasBridge) 150 else 20) * GameData.DEMOLISH_REFUND
        for (v in s.visitors) v.path.clear()
        dirty = true
        return Result.OK
    }

    fun canTerraform(tool: TerrainTool, x: Int, y: Int): Result {
        if (!s.inBounds(x, y)) return Result.fail("Fuera de la isla")
        if (s.terrainAt(x, y) != tool.from) return Result.fail("Aquí no se puede: ${tool.label}")
        if (grid.buildingAt(x, y) != null) return Result.fail("Hay un edificio")
        if (!canAfford(tool.cost)) return Result.fail("Dinero insuficiente")
        return Result.OK
    }

    fun terraform(tool: TerrainTool, x: Int, y: Int): Result {
        val r = canTerraform(tool, x, y); if (!r.ok) return r
        spendCapital(tool.cost.toFloat())
        s.terrain[s.idx(x, y)] = tool.to
        grid.rebuildRegions(); dirty = true
        return Result.OK
    }

    /** Demoler lo que haya en un tile: edificio, camino. */
    fun demolishAt(x: Int, y: Int): Result {
        grid.buildingAt(x, y)?.let { return demolishBuilding(it) }
        if (s.inBounds(x, y) && Terrain.isWalkablePath(s.terrainAt(x, y))) return removePath(x, y)
        return Result.fail("Nada que demoler")
    }

    // ------------------------------------------------------------------ comandos: dinos
    fun rangerInRange(x: Float, y: Float): Boolean {
        for (b in s.buildings) if (b.type == "ranger_station") {
            if (kotlin.math.abs(b.cx - x) + kotlin.math.abs(b.cy - y) <= b.def.actionRadius) return true
        }
        return false
    }
    var dartCooldown = 0f

    fun dart(d: Dino): Result {
        if (!hasBuilding("ranger_station")) return Result.fail("Necesitas un Centro de Rangers")
        if (!rangerInRange(d.x, d.y)) return Result.fail("Fuera del alcance de los Rangers (15 tiles)")
        if (dartCooldown > 0f) return Result.fail("Dardo recargando (${dartCooldown.roundToInt()} s)")
        if (d.sleep > 0f) return Result.fail("Ya está dormido")
        dartCooldown = 8f
        d.dartHits++
        if (d.dartHits >= d.def.darts) {
            d.dartHits = 0
            d.sleep = 60f
            d.state = DinoState.ASLEEP
            d.stress = min(d.stress, 50f)
            say("${d.def.name} dormido. Puedes transportarlo.")
        } else say("Dardo acertado (${d.dartHits}/${d.def.darts})")
        return Result.OK
    }

    fun cure(d: Dino): Result {
        if (!d.sick) return Result.fail("No está enfermo")
        if (!hasBuilding("ranger_station")) return Result.fail("Necesitas un Centro de Rangers")
        if (!s.researchDone.contains("B5") && !rangerInRange(d.x, d.y)) return Result.fail("Fuera del alcance de los Rangers")
        val cost = d.def.size.cureCost
        if (!canAfford(cost)) return Result.fail("Dinero insuficiente ($cost $)")
        spendOps(cost.toFloat())
        d.sick = false; d.sickTimer = 0f
        return Result.OK
    }

    fun transport(d: Dino, region: Int): Result {
        if (d.sleep <= 0f) return Result.fail("Primero hay que dormirlo con dardos")
        val r = grid.regions[region] ?: return Result.fail("Recinto inválido")
        val cost = d.def.size.transportCost
        if (!canAfford(cost)) return Result.fail("Dinero insuficiente ($cost $)")
        val free = r.tileList.filter { grid.dinoWalkable(it % n, it / n) }
        if (free.isEmpty()) return Result.fail("El recinto no tiene suelo libre")
        spendOps(cost.toFloat())
        val t = free[rnd.nextInt(free.size)]
        d.x = t % n + 0.5f; d.y = t / n + 0.5f; d.tx = d.x; d.ty = d.y
        d.region = region; d.homeRegion = region
        d.state = DinoState.WANDER; d.sleep = 0f; d.stress = 30f; d.targetEdge = -1
        return Result.OK
    }

    fun sellDino(d: Dino): Result {
        val price = d.def.cost / 4
        s.dinos.remove(d)
        earn(price.toFloat())
        say("${d.def.name} vendido por $price $")
        return Result.OK
    }

    fun setSpeed(sp: Int) { s.speed = sp.coerceIn(0, 2) }
    fun setEntryPrice(i: Int) { s.entryPrice = i.coerceIn(0, 2) }

    /** Recintos válidos (cerrados). */
    fun enclosures(): List<RegionInfo> = grid.regions.values.sortedBy { it.id }

    fun regionDinos(region: Int) = s.dinos.filter { it.region == region && it.state != DinoState.ESCAPED }

    // ------------------------------------------------------------------ tutorial
    fun currentTutorialStep(): TutorialStep? =
        if (s.tutorialActive && s.tutorialStep < Tutorial.count) Tutorial.steps[s.tutorialStep] else null

    private fun updateTutorial() {
        val step = currentTutorialStep() ?: run { s.tutorialActive = false; return }
        if (!step.check(this)) return
        tutorialJustCompleted = s.tutorialStep
        s.tutorialStep++
        if (s.tutorialStep >= Tutorial.count) {
            s.tutorialActive = false
            say("¡Tutorial completado! Ya conoces lo básico: ahora, a por las cinco estrellas.")
        } else say("Objetivo cumplido: ${step.title}")
    }

    fun skipTutorial() { s.tutorialActive = false }
    fun restartTutorial() { s.tutorialActive = true; s.tutorialStep = 0; cameraMoved = false }

    /** ¿Algún tile de camino alcanzable desde la entrada llega a 2 tiles de un recinto cerrado? (hueco para el mirador) */
    fun pathReachesEnclosure(): Boolean {
        val spawn = visitorSim.spawnTiles().firstOrNull() ?: return false
        val reach = grid.reachablePaths(spawn)
        for (i in reach.indices) {
            if (!reach[i]) continue
            val x = i % n; val y = i / n
            for (dy in -2..2) for (dx in -2..2) if (grid.regionAt(x + dx, y + dy) > 0) return true
        }
        return false
    }

    /** Isla Libre: un recinto por especie con comedero, agua y el grupo mínimo, para ver los modelos. */
    fun spawnShowcase(): Int {
        if (!def.sandbox) return 0
        val entrance = s.buildings.first { it.type == "entrance" }
        val pen = 8; val gap = 2
        val cols = 4
        var count = 0
        val x0 = (entrance.x - (cols * (pen + gap)) / 2 + 2).coerceAtLeast(1)
        val y0 = entrance.y - 12 - 4 * (pen + gap)
        GameData.species.forEachIndexed { i, sp ->
            val px = x0 + (i % cols) * (pen + gap); val py = y0 + (i / cols) * (pen + gap)
            if (py < 1 || px + pen >= n) return@forEachIndexed
            for (y in py..py + pen - 1) for (x in px..px + pen - 1) {
                val t = s.terrainAt(x, y)
                if (t == Terrain.FOREST || t == Terrain.ROCK || t == Terrain.WATER || Terrain.isWalkablePath(t)) s.terrain[s.idx(x, y)] = Terrain.GRASS
                grid.buildingAt(x, y)?.let { if (it.type != "entrance") s.buildings.remove(it) }
            }
            grid.rebuildBuildings(); grid.rebuildRegions()
            fenceRect(px, py, px + pen - 1, py + pen - 1, Fence.HEAVY)
            val region = grid.regionAt(px, py)
            if (region <= 0) return@forEachIndexed
            placeBuilding(if (sp.diet == Diet.HERBIVORE) "feeder_herb" else "feeder_carn", px, py)
            placeBuilding("water_trough", px + pen - 1, py + pen - 1)
            if (sp.requiresWaterTiles > 0) for (k in 0 until sp.requiresWaterTiles) s.terrain[s.idx(px + 1 + k, py + pen - 1)] = Terrain.WATER
            grid.rebuildRegions()
            repeat(sp.groupMin.coerceIn(1, 3)) { if (spawnDino(sp.id, region) != null) count++ }
        }
        // fila de todos los edificios junto a un camino, para revisar sus modelos
        val pathY = entrance.y - 6
        var bx = (entrance.x - 22).coerceAtLeast(1)
        val defs = listOf("generator") + GameData.buildings.filter { it.id != "entrance" && it.id != "generator" && !it.insideEnclosure }.map { it.id }
        for (x in bx until (bx + 60).coerceAtMost(n - 1)) { if (s.terrainAt(x, pathY) != Terrain.PATH) { s.terrain[s.idx(x, pathY)] = Terrain.GRASS; placePath(x, pathY) } }
        for (id in defs) {
            val d = GameData.building(id)
            val by = pathY - d.h
            if (bx + d.w >= n - 1) break
            for (yy in by until by + d.h) for (xx in bx until bx + d.w) { s.terrain[s.idx(xx, yy)] = Terrain.GRASS; grid.buildingAt(xx, yy)?.let { if (it.type != "entrance") s.buildings.remove(it) } }
            grid.rebuildBuildings(); grid.rebuildPower(); grid.rebuildRegions()
            if (d.attachToFence) { fenceRect(bx, by - 2, bx + d.w - 1, by - 1, Fence.LIGHT) }
            placeBuilding(id, bx, by)
            bx += d.w + 1
        }
        dirty = true
        return count
    }

    fun afterLoad() {
        grid = Grid(s)
        lastIncome = s.ledgerIncome; lastExpense = s.ledgerExpense
        dirty = true
    }
}
