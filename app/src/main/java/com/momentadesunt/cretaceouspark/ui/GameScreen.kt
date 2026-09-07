package com.momentadesunt.cretaceouspark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.momentadesunt.cretaceouspark.core.*
import com.momentadesunt.cretaceouspark.render.GameView

@Composable
fun GameScreen(vm: GameViewModel) {
    val w = vm.world ?: return
    vm.frame
    BackHandler {
        when {
            w.s.gameOver -> { vm.abandon(w.s.island); vm.world = null; vm.screen = Screen.MENU }
            vm.overlay != null -> vm.overlay = null
            vm.tool !is Tool.None -> vm.useTool(Tool.None)
            vm.selection != null -> vm.select(null)
            vm.category != null -> vm.category = null
            else -> vm.overlay = Overlay.PAUSE
        }
    }
    Box(Modifier.fillMaxSize().background(Pal.bg)) {
        AndroidView(factory = { ctx -> GameView(ctx, vm).also { vm.view = it } }, modifier = Modifier.fillMaxSize())

        TopBar(vm, w, Modifier.align(Alignment.TopStart))
        AlertsColumn(vm, w, Modifier.align(Alignment.TopStart).padding(top = 52.dp, start = 10.dp))
        CameraControls(vm, Modifier.align(Alignment.TopEnd).padding(top = 168.dp, end = 12.dp))

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            vm.uiMessage?.let { m -> Toast(m) }
            w.toast?.let { m -> if (vm.uiMessage == null) Toast(m) }
            if (vm.tool is Tool.Build && vm.ghost != null) Toast(if (vm.ghostOk) "Toca de nuevo para confirmar" else vm.ghostReason, if (vm.ghostOk) Pal.ok else Pal.alert)
            vm.selection?.let { SelectionPanel(vm, w) }
            BuildBar(vm, w)
        }

        when (vm.overlay) {
            Overlay.LAB -> LabScreen(vm, w)
            Overlay.RESEARCH -> ResearchScreen(vm, w)
            Overlay.EXPEDITIONS -> ExpeditionScreen(vm, w)
            Overlay.PAUSE -> PauseScreen(vm, w)
            null -> {}
        }
        if (w.s.gameOver) GameOverScreen(vm, w)
    }
}

@Composable
private fun Toast(text: String, color: Color = Pal.panel) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 80.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (color == Pal.panel) Pal.ink else Color(0xFF0F1A13), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 12.dp, vertical = 6.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun TopBar(vm: GameViewModel, w: World, modifier: Modifier) {
    vm.frame
    val s = w.s
    Row(modifier.fillMaxWidth().background(Pal.panel).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val net = s.incomeMin - s.expenseMin
        Chip(money(s.money), textColor = if (s.money < 0) Pal.alert else Pal.ink, onClick = { vm.overlay = Overlay.PAUSE })
        Text((if (net >= 0) "+" else "") + money(net) + "/min", color = if (net >= 0) Pal.ok else Pal.alert, fontSize = 12.sp)
        Chip("👤 ${s.visitors.size + s.virtualVisitors.sumOf { it[1].toInt() }}")
        if (!w.def.sandbox) StarsChip(s.stars) { vm.overlay = Overlay.PAUSE }
        if (s.eventText.isNotEmpty()) Chip(s.eventText, color = Pal.alert, textColor = Color.White)
        Spacer(Modifier.weight(1f))
        Chip("🧬 ADN", color = if (vm.overlay == Overlay.LAB) Pal.grass else Pal.panel2, onClick = { vm.overlay = Overlay.LAB })
        Chip("🔬 Investigar", color = Pal.panel2, onClick = { vm.overlay = Overlay.RESEARCH })
        Chip("🌍 Expediciones", color = Pal.panel2, onClick = { vm.overlay = Overlay.EXPEDITIONS })
        Spacer(Modifier.width(6.dp))
        SpeedButton("⏸", 0, s.speed) { w.setSpeed(0) }
        SpeedButton("1×", 1, s.speed) { w.setSpeed(1) }
        SpeedButton("2×", 2, s.speed) { w.setSpeed(2) }
        Chip("☰", color = Pal.panel2, onClick = { vm.overlay = Overlay.PAUSE })
    }
}

@Composable
fun StarsChip(v: Float, onClick: (() -> Unit)? = null) {
    val full = v.toInt(); val half = v - full >= 0.5f
    val m = Modifier.clip(RoundedCornerShape(4.dp)).background(Pal.panel2).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 10.dp, vertical = 6.dp)
    Row(m) {
        Text("★".repeat(full) + (if (half) "½" else ""), color = Pal.amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("★".repeat(5 - full - (if (half) 1 else 0)), color = Pal.line, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SpeedButton(label: String, value: Int, current: Int, onClick: () -> Unit) {
    Chip(label, color = if (current == value) Pal.grass else Pal.panel2, textColor = if (current == value) Color(0xFF0F1A13) else Pal.ink, onClick = onClick)
}

@Composable
private fun AlertsColumn(vm: GameViewModel, w: World, modifier: Modifier) {
    vm.frame
    Column(modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (a in w.s.alerts.takeLast(3).reversed()) {
            val red = a.kind in setOf("escape", "fence", "death", "vdeath", "injury", "bankrupt", "storm", "hunt")
            Text("⚠ ${a.text}", color = if (red) Color.White else Pal.ink, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (red) Pal.alert.copy(alpha = 0.9f) else Pal.panel).clickable { vm.focusAlert(a) }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun CameraControls(vm: GameViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("↻", color = Pal.panel, onClick = { vm.view?.cam?.rotate() })
        Chip("+", color = Pal.panel, onClick = { vm.view?.cam?.zoomIn() })
        Chip("−", color = Pal.panel, onClick = { vm.view?.cam?.zoomOut() })
        Chip("⌂", color = Pal.panel, onClick = { vm.view?.centerOnEntrance() })
    }
}

// ------------------------------------------------------------------ barra de construcción
@Composable
private fun BuildBar(vm: GameViewModel, w: World) {
    vm.frame
    Column(Modifier.fillMaxWidth().background(Pal.panel)) {
        val cat = vm.category
        if (cat != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolItems(vm, w, cat)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (c in Category.values()) {
                val active = vm.category == c
                Chip(c.label, color = if (active) Pal.sand else Pal.panel2, textColor = if (active) Color(0xFF0F1A13) else Pal.ink,
                    onClick = { if (active) { vm.category = null; vm.useTool(Tool.None) } else { vm.category = c; vm.useTool(Tool.None) } })
            }
            Spacer(Modifier.weight(1f))
            if (vm.tool !is Tool.None) Chip("✕ Herramienta", color = Pal.alert, textColor = Color.White, onClick = { vm.useTool(Tool.None) })
            Chip("🔨 Demoler", color = if (vm.tool is Tool.Demolish) Pal.alert else Pal.panel2, textColor = if (vm.tool is Tool.Demolish) Color.White else Pal.ink, onClick = { vm.useTool(Tool.Demolish) })
        }
    }
}

@Composable
private fun ToolItems(vm: GameViewModel, w: World, cat: Category) {
    val tool = vm.tool
    when (cat) {
        Category.ENCLOSURE -> {
            for (t in 1..4) {
                val ok = w.fenceUnlocked(t)
                ToolChip(Fence.names[t], "${Fence.cost[t]} $/tramo · ${Fence.hp[t]} PV", tool == Tool.FenceTool(t), ok.ok, ok.reason) { vm.useTool(Tool.FenceTool(t)) }
            }
            ToolChip("Puerta", "200 $ · toca una valla", tool is Tool.Gate, true, "") { vm.useTool(Tool.Gate) }
            for (b in GameData.buildings.filter { it.category == cat }) BuildingChip(vm, w, b)
        }
        Category.PATHS -> {
            ToolChip("Camino", "20 $ · arrastra para pintar", tool is Tool.PathTool, true, "") { vm.useTool(Tool.PathTool) }
            for (b in GameData.buildings.filter { it.category == cat }) BuildingChip(vm, w, b)
        }
        Category.TERRAIN -> {
            for (t in TerrainTool.values()) ToolChip(t.label, "${t.cost} $/tile", tool == Tool.Terraform(t), true, "") { vm.useTool(Tool.Terraform(t)) }
        }
        else -> for (b in GameData.buildings.filter { it.category == cat && it.id != "entrance" }) BuildingChip(vm, w, b)
    }
}

@Composable
private fun BuildingChip(vm: GameViewModel, w: World, b: BuildingDef) {
    val u = w.buildingUnlocked(b)
    ToolChip(b.name, "${b.cost} $ · ${b.w}×${b.h}" + (if (b.upkeep > 0) " · ${b.upkeep.toInt()}/min" else ""), vm.tool == Tool.Build(b.id), u.ok, u.reason) { vm.useTool(Tool.Build(b.id)) }
}

@Composable
private fun ToolChip(name: String, sub: String, active: Boolean, enabled: Boolean, reason: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(4.dp)).background(if (active) Pal.grass else if (enabled) Pal.panel2 else Pal.line)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp).widthIn(min = 96.dp, max = 190.dp)
    ) {
        Text(name, color = if (active) Color(0xFF0F1A13) else if (enabled) Pal.ink else Pal.ink3, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(if (enabled) sub else "🔒 " + reason.replace("Requiere investigar: ", "Investigar: "), color = if (active) Color(0xFF0F1A13) else Pal.ink3, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp)
    }
}

// ------------------------------------------------------------------ panel de selección
@Composable
private fun SelectionPanel(vm: GameViewModel, w: World) {
    vm.frame
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Pal.panel).padding(12.dp)) {
            when (val sel = vm.selection) {
                is Selection.DinoSel -> { val d = w.s.dinos.firstOrNull { it.id == sel.id }; if (d == null) { vm.select(null); return } else DinoPanel(vm, w, d) }
                is Selection.BuildingSel -> { val b = w.s.buildings.firstOrNull { it.id == sel.id }; if (b == null) { vm.select(null); return } else BuildingPanel(vm, w, b) }
                is Selection.EdgeSel -> EdgePanel(vm, w, sel.edge)
                null -> {}
            }
        }
        Chip("✕", color = Pal.panel2, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), onClick = { vm.select(null) })
    }
}

@Composable
private fun DinoPanel(vm: GameViewModel, w: World, d: Dino) {
    vm.frame
    var transporting by remember(d.id) { mutableStateOf(false) }
    val def = d.def
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.2f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(Color(def.colorBody))); Spacer(Modifier.width(8.dp))
                Title(def.name)
                Spacer(Modifier.width(8.dp))
                Label(if (def.diet == Diet.HERBIVORE) "Herbívoro" else "Carnívoro")
                Label(" · ${def.size.label} · Peligro ${def.danger}")
            }
            val state = when {
                d.state == DinoState.ESCAPED -> "¡FUGADO!"
                d.sleep > 0f -> "Dormido (${d.sleep.toInt()} s)"
                d.sick -> "Enfermo (muere en ${(180 - d.sickTimer).toInt()} s)"
                d.state == DinoState.ATTACK -> "¡Golpeando la valla!"
                d.state == DinoState.TO_FENCE -> "Buscando la valla más débil"
                d.state == DinoState.SEEK_FOOD -> "Buscando comida"
                d.state == DinoState.SEEK_WATER -> "Buscando agua"
                else -> "Tranquilo · ${w.grid.regions[d.region]?.name ?: ""}"
            }
            Body(state, if (d.state == DinoState.ESCAPED || d.state == DinoState.ATTACK) Pal.alert else Pal.ink2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { Bar("Comida", d.food); Bar("Agua", d.water, color = Pal.water); Bar("Espacio", d.space, color = Pal.sand) }
                Column(Modifier.weight(1f)) { Bar("Compañía", d.social, color = Pal.sand); Bar("Bienestar", d.wellbeing); Bar("Estrés", d.stress, color = Pal.alert) }
            }
        }
        Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (transporting) {
                Label("Transportar a…")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in w.enclosures()) BlockButton(r.name, { vm.act(w.transport(d, r.id)); transporting = false }, small = true, sub = "${r.tiles} tiles · ${w.regionDinos(r.id).size} dinos")
                    BlockButton("Cancelar", { transporting = false }, color = Pal.panel2, textColor = Pal.ink, small = true)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlockButton("Dardo", { vm.act(w.dart(d)) }, small = true, color = if (d.state == DinoState.ESCAPED || d.stress >= 75f) Pal.alert else Pal.panel2, textColor = if (d.state == DinoState.ESCAPED || d.stress >= 75f) Color.White else Pal.ink,
                        sub = "${d.dartHits}/${def.darts}" + (if (w.dartCooldown > 0f) " · ${w.dartCooldown.toInt()} s" else ""))
                    if (d.sick) BlockButton("Curar", { vm.act(w.cure(d)) }, small = true, color = Pal.ok, sub = "${def.size.cureCost} $")
                    if (d.sleep > 0f) BlockButton("Transportar", { transporting = true }, small = true, color = Pal.sand, sub = "${def.size.transportCost} $")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlockButton("Ir", { vm.pendingFocus = Pair(d.x, d.y) }, small = true, color = Pal.panel2, textColor = Pal.ink)
                    BlockButton("Vender", { vm.act(w.sellDino(d)); vm.select(null) }, small = true, color = Pal.panel2, textColor = Pal.ink, sub = "${def.cost / 4} $")
                }
                Body("Valla mínima: ${Fence.names[def.fenceMin]} · Grupo ${def.groupMin}–${def.groupMax} · ${def.spaceMin} tiles/dino")
            }
        }
    }
}

@Composable
private fun BuildingPanel(vm: GameViewModel, w: World, b: Building) {
    vm.frame
    val def = b.def
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(Color(def.color))); Spacer(Modifier.width(8.dp)); Title(def.name)
            }
            Body(def.desc)
            val notes = ArrayList<String>()
            if (def.needsPower) notes.add(if (b.powered) "Con energía" else "⚡ SIN ENERGÍA: construye un Generador a menos de 10 tiles")
            if (def.feederDiet != null) notes.add("Raciones: ${b.stock}/${def.stock}")
            if (def.upkeep > 0) notes.add("Mantenimiento ${def.upkeep.toInt()} $/min")
            if (def.viewRange > 0) notes.add("Especies visibles: ${w.visitorSim.visibleSpecies(b).joinToString { GameData.species(it).name }.ifEmpty { "ninguna" }}")
            if (def.id == "research_center") notes.add("Produce ${w.researchRate().toInt()} PI/min en total · ${w.s.researchPoints.toInt()} PI acumulados")
            if (def.needsPath && !w.grid.hasAdjacentPath(b.x, b.y, b.w, b.h)) notes.add("⚠ Sin camino adyacente: los visitantes no llegan")
            for (n in notes) Body(n, if (n.startsWith("⚡") || n.startsWith("⚠")) Pal.alert else Pal.ink2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (def.id == "entrance") {
                Label("Precio de entrada")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..2) BlockButton("${GameData.ENTRY_PRICES[i]} $", { w.setEntryPrice(i) }, small = true, color = if (w.s.entryPrice == i) Pal.grass else Pal.panel2, textColor = if (w.s.entryPrice == i) Color(0xFF0F1A13) else Pal.ink)
                }
                Body("Reputación ${w.s.reputation.toInt()} · Ferry cada 20 s")
            } else {
                if (def.feederDiet != null) BlockButton("Reponer", { vm.act(w.refillFeeder(b)) }, small = true, sub = "${def.refillCost} $")
                BlockButton("Demoler", { vm.act(w.demolishBuilding(b)); vm.select(null) }, small = true, color = Pal.alert, textColor = Color.White, sub = "devuelve ${(def.cost * GameData.DEMOLISH_REFUND).toInt()} $")
            }
        }
    }
}

@Composable
private fun EdgePanel(vm: GameViewModel, w: World, e: EdgeRef) {
    vm.frame
    val t = w.grid.fenceType(e)
    if (t == 0) { vm.select(null); return }
    val hp = w.grid.fenceHp(e); val maxHp = Fence.hp[t]
    val flags = w.grid.fenceFlags(e)
    val (ra, rb) = w.grid.regionsOfEdge(e)
    val region = w.grid.regions[if (ra != 0) ra else rb]
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Title(if (flags and EdgeFlag.GATE != 0) "Puerta (${Fence.names[t]})" else Fence.names[t])
            Bar("Resistencia", hp.toFloat(), maxHp.toFloat(), color = if (hp < maxHp / 2) Pal.alert else Pal.grass)
            if (t == Fence.ELECTRIC) Body(if (w.grid.edgePowered(e)) "Electrificada: ataque recibido ×0,3" else "⚡ Sin energía: se comporta como valla ligera", if (w.grid.edgePowered(e)) Pal.ink2 else Pal.alert)
            if (region != null) {
                val dinos = w.regionDinos(region.id)
                Body("${region.name}: ${region.tiles} tiles (${region.water} agua, ${region.forest} bosque) · ${dinos.size} dinos · valla más débil: ${Fence.names[region.weakestLevel.coerceIn(0, 4)]}")
                val weak = dinos.filter { it.def.fenceMin > region.weakestLevel }
                if (weak.isNotEmpty()) Body("⚠ Valla insuficiente para: ${weak.map { it.def.name }.distinct().joinToString()}", Pal.alert)
            } else Body("Este tramo no cierra ningún recinto", Pal.ink3)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BlockButton("Reparar", { vm.act(w.repairFence(e)) }, small = true, enabled = hp < maxHp, sub = "${(Fence.cost[t] * GameData.REPAIR_FRACTION).toInt()} $")
                BlockButton("Reparar todo", { val c = w.repairAllFences(); vm.message("$c tramos reparados") }, small = true, color = Pal.panel2, textColor = Pal.ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BlockButton(if (flags and EdgeFlag.GATE == 0) "Hacer puerta" else if (flags and EdgeFlag.OPEN != 0) "Cerrar puerta" else "Abrir puerta", { vm.act(w.toggleGate(e)) }, small = true, color = Pal.sand)
                BlockButton("Quitar", { vm.act(w.removeFence(e)); vm.select(null) }, small = true, color = Pal.alert, textColor = Color.White)
            }
        }
    }
}

// ------------------------------------------------------------------ pausa y fin
@Composable
fun PauseScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    OverlayFrame("Parque · ${w.def.name}", onClose = { vm.overlay = null }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("VALORACIÓN"); Text(stars(s.stars), color = Pal.amber, fontSize = 28.sp)
                Bar("Beneficios", s.idxProfit); Bar("Seguridad", s.idxSafety, color = Pal.water); Bar("Bienestar animal", s.idxWelfare); Bar("Atractivo", s.idxAttraction, color = Pal.sand)
                Body("Para 5★ los cuatro índices deben superar 90. Objetivo de beneficio: ${w.def.incomeTarget.toInt()} $/min.")
                Bar("Reputación", s.reputation, color = Pal.amber)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("RESUMEN")
                Body("Saldo ${money(s.money)} · Ingresos ${money(s.incomeMin)}/min · Gastos ${money(s.expenseMin)}/min")
                Body("Tiempo de juego ${(s.time / 60).toInt()} min · ${s.dinos.size} dinosaurios de ${s.dinos.map { it.species }.toSet().size} especies")
                Body("${s.visitors.size} visitantes · ${s.buildings.size - 1} edificios · ${w.allEdges().size} tramos de valla")
                Body("Ámbar ganado en esta isla: ${s.amberEarned} · total ${vm.amber}", Pal.amber)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockButton("Seguir jugando", { vm.overlay = null })
                    BlockButton("Guardar y salir", { vm.exitToMenu() }, color = Pal.panel2, textColor = Pal.ink)
                }
                Spacer(Modifier.height(10.dp))
                Label("CÓMO EMPEZAR")
                Body("1. Recintos → valla ligera: arrastra un rectángulo sobre la hierba.\n2. Dentro: comedero y bebedero. 3. Caminos desde la entrada y un mirador pegado a la valla.\n4. Centros: Generador, Expediciones, Laboratorio. 5. 🌍 envía una expedición, 🧬 incuba con ADN ≥ 50 %.")
            }
        }
    }
}

@Composable
fun GameOverScreen(vm: GameViewModel, w: World) {
    vm.frame
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(RoundedCornerShape(8.dp)).background(Pal.panel2).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("El parque ha quebrado", color = Pal.alert, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Body("Ámbar conservado: ${w.s.amberEarned}. Puedes empezar de nuevo en esta isla.")
            Spacer(Modifier.height(12.dp))
            BlockButton("Volver al menú", { vm.abandon(w.s.island); vm.world = null; vm.screen = Screen.MENU })
        }
    }
}

@Composable
fun OverlayFrame(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.92f).fillMaxHeight(0.92f).clip(RoundedCornerShape(8.dp)).background(Pal.bg)
                .clickable(enabled = false) {}.padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Pal.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Label("Juego en pausa")
                Spacer(Modifier.width(12.dp))
                Chip("✕ Cerrar", color = Pal.panel2, onClick = onClose)
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), content = content)
        }
    }
}
