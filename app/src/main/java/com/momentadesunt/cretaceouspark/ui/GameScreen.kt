package com.momentadesunt.cretaceouspark.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
            vm.buildOpen -> vm.buildOpen = false
            else -> vm.overlay = Overlay.PAUSE
        }
    }
    Box(Modifier.fillMaxSize().background(Pal.bg)) {
        AndroidView(factory = { ctx -> GameView(ctx, vm).also { vm.view = it } }, modifier = Modifier.fillMaxSize())

        TopBar(vm, w, Modifier.align(Alignment.TopStart))
        val tutorial = w.currentTutorialStep()
        if (tutorial != null) TutorialCard(vm, w, tutorial, Modifier.align(Alignment.TopStart).padding(top = 56.dp, start = 120.dp))
        AlertsColumn(vm, w, Modifier.align(Alignment.TopStart).padding(top = if (tutorial != null) 196.dp else 56.dp, start = 120.dp))
        CameraControls(vm, Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 56.dp))

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            vm.uiMessage?.let { m -> Toast(m) }
            w.toast?.let { m -> if (vm.uiMessage == null) Toast(m) }
            vm.selection?.let { SelectionPanel(vm, w) }
            ToolBar(vm, w)
            BuildBar(vm, w)
        }

        when (vm.overlay) {
            Overlay.DINOS -> DinosScreen(vm, w)
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
private fun Toast(text: String, color: Color = Color(0xE6101A14)) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 80.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, style = shadowStyle,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color).border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 7.dp), textAlign = TextAlign.Center)
    }
}

// ------------------------------------------------------------------ barra superior
@Composable
private fun TopBar(vm: GameViewModel, w: World, modifier: Modifier) {
    val s = w.s
    Row(modifier.fillMaxWidth().background(Pal.panel).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val net = s.incomeMin - s.expenseMin
        ResourceBadge("💰", money(s.money), valueColor = if (s.money < 0) Pal.red else Pal.gold, sub = (if (net >= 0) "+" else "") + money(net) + "/min", subColor = if (net >= 0) Color(0xFF7BE07B) else Pal.red, onClick = { vm.overlay = Overlay.PAUSE })
        ResourceBadge("👤", "${s.visitors.size + s.virtualVisitors.sumOf { it[1].toInt() }}", sub = "visitantes")
        if (!w.def.sandbox) ResourceBadge("★", stars(s.stars), valueColor = Pal.gold, onClick = { vm.overlay = Overlay.PAUSE })
        if (s.eventText.isNotEmpty()) CocButton(s.eventText, {}, color = Pal.red, dark = Pal.redDark, small = true, icon = "⚠")
        w.challengeDef?.let { ch -> CocButton(ch.name, { vm.overlay = Overlay.PAUSE }, color = if (s.challengeDone) Pal.green else Pal.gold, dark = if (s.challengeDone) Pal.greenDark else Pal.goldDark, small = true, icon = if (s.challengeDone) "✓" else "🏁") }
        Spacer(Modifier.weight(1f))
        SpeedButton("⏸", 0, s.speed) { w.setSpeed(0) }
        SpeedButton("1×", 1, s.speed) { w.setSpeed(1) }
        SpeedButton("2×", 2, s.speed) { w.setSpeed(2) }
        Spacer(Modifier.width(4.dp))
        CocButton("Parque", { vm.overlay = Overlay.PAUSE }, color = Pal.gold, dark = Pal.goldDark, small = true, icon = "☰")
    }
}

@Composable
private fun SpeedButton(label: String, value: Int, current: Int, onClick: () -> Unit) {
    val active = current == value
    CocButton(label, onClick, Modifier.width(52.dp), color = if (active) Pal.green else Pal.darkBtn, dark = if (active) Pal.greenDark else Pal.darkBtnDark, small = true)
}

// ------------------------------------------------------------------ tutorial y alertas
@Composable
private fun TutorialCard(vm: GameViewModel, w: World, step: TutorialStep, modifier: Modifier) {
    val flash = System.currentTimeMillis() - vm.tutorialFlash < 1500
    CocFrame(modifier.width(340.dp), body = if (flash) Color(0xFFD7EDC9) else Pal.body, padding = 8) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ribbon("Tutorial ${w.s.tutorialStep + 1} / ${Tutorial.count}", color = Pal.blue)
            Spacer(Modifier.weight(1f))
            CocButton("Saltar", { w.skipTutorial(); vm.message("Tutorial desactivado. Puedes reiniciarlo desde Parque.") }, color = Pal.red, dark = Pal.redDark, small = true)
        }
        Spacer(Modifier.height(4.dp))
        Text(step.title, color = Pal.text, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text(step.text, color = Pal.text2, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until Tutorial.count) Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(if (i < w.s.tutorialStep) Pal.green else if (i == w.s.tutorialStep) Pal.gold else Pal.frameLight.copy(alpha = 0.35f)))
        }
    }
}

@Composable
private fun AlertsColumn(vm: GameViewModel, w: World, modifier: Modifier) {
    Column(modifier.widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (a in w.s.alerts.takeLast(3).reversed()) {
            val red = a.kind in w.redAlertKinds
            Text("⚠ ${a.text}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = shadowStyle,
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(if (red) Pal.red.copy(alpha = 0.92f) else Color(0xD9101A14)).border(2.dp, if (red) Pal.redDark else Color(0xFF3A4A3E), RoundedCornerShape(7.dp)).clickable { vm.focusAlert(a) }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun CameraControls(vm: GameViewModel, modifier: Modifier) {
    @Composable fun B(label: String, action: () -> Unit) = CocButton(label, action, Modifier.width(48.dp), color = Pal.darkBtn, dark = Pal.darkBtnDark, small = true)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { B("↻") { vm.view?.cam?.rotate() }; B("⌂") { vm.view?.centerOnEntrance() } }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { B("+") { vm.view?.cam?.zoomIn() }; B("−") { vm.view?.cam?.zoomOut() } }
    }
}

// ------------------------------------------------------------------ barra de la herramienta activa
/** Confirmar/borrar el plano (vallas, caminos, edificios) o tamaño del pincel (terreno, demoler). */
@Composable
private fun ToolBar(vm: GameViewModel, w: World) {
    val t = vm.tool
    if (t is Tool.None || t is Tool.Gate) return
    Row(Modifier.fillMaxWidth().background(Pal.panel).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun Hint(text: String, color: Color = Pal.text) =
            Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = shadowStyle, maxLines = 2, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        when (t) {
            is Tool.FenceTool, is Tool.PathTool -> {
                val what = if (t is Tool.FenceTool) "tramos" else "tiles"
                Hint(if (vm.planCount == 0) "Dibuja con el dedo sobre el mapa · toca para añadir o quitar" else "${vm.planValid} de ${vm.planCount} $what válidos",
                    if (vm.planCount > 0 && vm.planValid < vm.planCount) Pal.red else Pal.text)
                CocButton("Confirmar", { vm.confirmPlan() }, enabled = vm.planValid > 0, small = true, icon = "✓", sub = if (vm.planValid > 0) "${vm.planCost} $" else null)
                CocButton("Borrar", { vm.clearPlan() }, enabled = vm.planCount > 0, color = Pal.red, dark = Pal.redDark, small = true, icon = "✕")
            }
            is Tool.Build -> {
                val g = vm.ghost
                val def = GameData.building(t.defId)
                Hint(when { g == null -> "Toca el mapa para colocar el plano de ${def.name}"; vm.ghostOk -> "Arrastra el plano para moverlo y confirma"; else -> vm.ghostReason },
                    if (g != null && !vm.ghostOk) Pal.red else Pal.text)
                CocButton("Confirmar", { vm.confirmGhost() }, enabled = g != null && vm.ghostOk, small = true, icon = "✓", sub = "${def.cost} $")
                CocButton("Quitar plano", { vm.ghost = null }, enabled = g != null, color = Pal.red, dark = Pal.redDark, small = true, icon = "✕")
            }
            else -> {
                Hint(when (t) {
                    is Tool.Demolish -> "Pinta para quitar caminos y vallas · toca un edificio para demolerlo"
                    is Tool.Terraform -> "Pinta sobre el mapa · ${t.t.cost} $ por tile"
                    else -> ""
                })
                Text("Pincel", color = Pal.text3, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                for (sz in 1..4) CocButton("$sz", { vm.brushSize = sz }, Modifier.width(40.dp), color = if (vm.brushSize == sz) Pal.green else Pal.darkBtn, dark = if (vm.brushSize == sz) Pal.greenDark else Pal.darkBtnDark, small = true)
            }
        }
    }
}

// ------------------------------------------------------------------ barra inferior
@Composable
private fun BuildBar(vm: GameViewModel, w: World) {
    Column(Modifier.fillMaxWidth().background(Pal.panel)) {
        if (vm.buildOpen) {
            val cat = vm.category
            if (cat != null) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ToolItems(vm, w, cat) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val icons = mapOf(Category.ENCLOSURE to "🧱", Category.PATHS to "🛤", Category.SERVICES to "🍔", Category.CENTERS to "🏛", Category.TERRAIN to "⛏")
                for (c in Category.values()) {
                    val active = vm.category == c
                    CocButton(c.label, { if (active) { vm.category = null; vm.useTool(Tool.None) } else { vm.category = c; vm.useTool(Tool.None) } },
                        color = if (active) Pal.gold else Pal.blue, dark = if (active) Pal.goldDark else Pal.blueDark, small = true, icon = icons[c])
                }
                CocButton("Demoler", { vm.category = null; vm.useTool(Tool.Demolish) }, color = Pal.red, dark = Pal.redDark, small = true, icon = "🔨")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MainTab("Construir", "🏗", vm.buildOpen) { vm.buildOpen = !vm.buildOpen; if (!vm.buildOpen) { vm.category = null; vm.useTool(Tool.None) } }
            MainTab("Dinosaurios", "🦕", vm.overlay == Overlay.DINOS, badge = w.s.dinos.size.takeIf { it > 0 }?.toString()) { vm.overlay = Overlay.DINOS }
            MainTab("ADN e incubar", "🧬", vm.overlay == Overlay.LAB, badge = w.s.incubations.size.takeIf { it > 0 }?.let { "$it" }) { vm.overlay = Overlay.LAB }
            MainTab("Investigar", "🔬", vm.overlay == Overlay.RESEARCH, badge = w.s.researchActive?.let { "…" }) { vm.overlay = Overlay.RESEARCH }
            MainTab("Expediciones", "🌍", vm.overlay == Overlay.EXPEDITIONS, badge = w.s.expeditions.size.takeIf { it > 0 }?.toString()) { vm.overlay = Overlay.EXPEDITIONS }
            Spacer(Modifier.weight(1f))
            if (vm.tool !is Tool.None) CocButton("Soltar herramienta", { vm.useTool(Tool.None) }, color = Pal.red, dark = Pal.redDark, small = true, icon = "✕")
        }
    }
}

@Composable
private fun MainTab(label: String, icon: String, active: Boolean, badge: String? = null, onClick: () -> Unit) {
    Box {
        CocButton(label, onClick, color = if (active) Pal.gold else Pal.darkBtn, dark = if (active) Pal.goldDark else Pal.darkBtnDark, icon = icon)
        if (badge != null) Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, style = shadowStyle,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).clip(RoundedCornerShape(8.dp)).background(Pal.red).padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

@Composable
private fun ToolItems(vm: GameViewModel, w: World, cat: Category) {
    val tool = vm.tool
    when (cat) {
        Category.ENCLOSURE -> {
            for (t in 1..4) {
                val ok = w.fenceUnlocked(t)
                ToolCard(Fence.names[t].removePrefix("Valla "), "🧱", "${Fence.hp[t]} PV · dibuja y confirma", "${Fence.cost[t]}", tool == Tool.FenceTool(t), ok.ok, ok.reason) { vm.useTool(Tool.FenceTool(t)) }
            }
            ToolCard("Puerta", "🚪", "toca una valla", "200", tool is Tool.Gate, true, "") { vm.useTool(Tool.Gate) }
            for (b in GameData.buildings.filter { it.category == cat }) BuildingCard(vm, w, b)
        }
        Category.PATHS -> {
            ToolCard("Camino", "🛤", "dibuja y confirma", "20", tool is Tool.PathTool, true, "") { vm.useTool(Tool.PathTool) }
            for (b in GameData.buildings.filter { it.category == cat }) BuildingCard(vm, w, b)
        }
        Category.TERRAIN -> for (t in TerrainTool.values()) ToolCard(t.label, "⛏", "pincel · por tile", "${t.cost}", tool == Tool.Terraform(t), true, "") { vm.useTool(Tool.Terraform(t)) }
        else -> for (b in GameData.buildings.filter { it.category == cat && it.id != "entrance" }) BuildingCard(vm, w, b)
    }
}

private val buildingIcons = mapOf(
    "feeder_herb" to "🌿", "feeder_carn" to "🥩", "water_trough" to "💧", "dino_shelter" to "⛺", "viewpoint" to "🔭", "gallery" to "🏟",
    "shop_food" to "🍔", "shop_drink" to "🥤", "shop_gift" to "🎁", "toilets" to "🚻", "hotel_small" to "🏨", "hotel_large" to "🏩", "visitor_shelter" to "🛡",
    "generator" to "⚡", "expedition_hq" to "🚁", "lab" to "🧬", "research_center" to "🔬", "ranger_station" to "🎯"
)

@Composable
private fun BuildingCard(vm: GameViewModel, w: World, b: BuildingDef) {
    val u = w.buildingUnlocked(b)
    ToolCard(b.name, buildingIcons[b.id] ?: "🏗", "${b.w}×${b.h}" + (if (b.upkeep > 0) " · ${b.upkeep.toInt()} $/min" else ""), "${b.cost}", vm.tool == Tool.Build(b.id), u.ok, u.reason) { vm.useTool(Tool.Build(b.id)) }
}

@Composable
private fun ToolCard(name: String, icon: String, sub: String, cost: String, active: Boolean, enabled: Boolean, reason: String, onClick: () -> Unit) {
    CocCard(Modifier.width(128.dp).height(78.dp), selected = active, dim = !enabled, onClick = onClick, padding = 6) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (enabled) icon else "🔒", fontSize = 16.sp)
            Spacer(Modifier.width(5.dp))
            Text(name, color = if (enabled) Pal.text else Pal.text3, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (enabled) sub else reason, color = Pal.text3, fontSize = 9.sp, maxLines = 2, lineHeight = 11.sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        CostBadge(cost, color = if (enabled) Pal.gold else Pal.grey)
    }
}

// ------------------------------------------------------------------ panel de selección
@Composable
private fun SelectionPanel(vm: GameViewModel, w: World) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        CocFrame(Modifier.fillMaxWidth(), padding = 10) {
            when (val sel = vm.selection) {
                is Selection.DinoSel -> { val d = w.s.dinos.firstOrNull { it.id == sel.id }; if (d == null) { vm.select(null); return@CocFrame } else DinoPanel(vm, w, d) }
                is Selection.BuildingSel -> { val b = w.s.buildings.firstOrNull { it.id == sel.id }; if (b == null) { vm.select(null); return@CocFrame } else BuildingPanel(vm, w, b) }
                is Selection.EdgeSel -> EdgePanel(vm, w, sel.edge)
                null -> {}
            }
        }
        CloseButton({ vm.select(null) }, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
private fun DinoPanel(vm: GameViewModel, w: World, d: Dino) {
    var transporting by remember(d.id) { mutableStateOf(false) }
    val def = d.def
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1.2f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(def.colorBody)).border(1.dp, Pal.frame, RoundedCornerShape(4.dp))); Spacer(Modifier.width(8.dp))
                Title(def.name); Spacer(Modifier.width(8.dp))
                Label("${if (def.diet == Diet.HERBIVORE) "Herbívoro" else "Carnívoro"} · ${def.size.label} · peligro ${def.danger}")
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
            Body(state, if (d.state == DinoState.ESCAPED || d.state == DinoState.ATTACK) Pal.red else Pal.text2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { Bar("Comida", d.food); Bar("Agua", d.water, color = Pal.blue); Bar("Espacio", d.space, color = Pal.gold) }
                Column(Modifier.weight(1f)) { Bar("Compañía", d.social, color = Pal.gold); Bar("Bienestar", d.wellbeing); Bar("Estrés", d.stress, color = Pal.red) }
            }
        }
        Column(Modifier.weight(0.8f).padding(end = 44.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (transporting) {
                Label("TRANSPORTAR A…")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in w.enclosures()) CocButton(r.name, { vm.act(w.transport(d, r.id)); transporting = false }, color = Pal.gold, dark = Pal.goldDark, small = true, sub = "${r.tiles} tiles · ${w.regionDinos(r.id).size} dinos")
                    CocButton("Cancelar", { transporting = false }, color = Pal.red, dark = Pal.redDark, small = true)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val urgent = d.state == DinoState.ESCAPED || d.stress >= 75f
                    CocButton("Dardo", { vm.act(w.dart(d)) }, color = if (urgent) Pal.red else Pal.darkBtn, dark = if (urgent) Pal.redDark else Pal.darkBtnDark, small = true, icon = "🎯",
                        sub = "${d.dartHits}/${def.darts}" + (if (w.dartCooldown > 0f) " · ${w.dartCooldown.toInt()} s" else ""))
                    if (d.sick) CocButton("Curar", { vm.act(w.cure(d)) }, small = true, icon = "💊", sub = "${def.size.cureCost} $")
                    if (d.sleep > 0f) CocButton("Transportar", { transporting = true }, color = Pal.gold, dark = Pal.goldDark, small = true, icon = "🚚", sub = "${def.size.transportCost} $")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CocButton("Ir", { vm.pendingFocus = Pair(d.x, d.y) }, color = Pal.blue, dark = Pal.blueDark, small = true, icon = "📍")
                    CocButton("Vender", { vm.act(w.sellDino(d)); vm.select(null) }, color = Pal.grey, dark = Pal.greyDark, small = true, sub = "+${def.cost / 4} $")
                }
                Body("Valla mínima ${Fence.names[def.fenceMin].removePrefix("Valla ").lowercase()} · grupo ${def.groupMin}–${def.groupMax} · ${def.spaceMin} tiles/dino")
            }
        }
    }
}

@Composable
private fun BuildingPanel(vm: GameViewModel, w: World, b: Building) {
    val def = b.def
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(buildingIcons[def.id] ?: "🏗", fontSize = 18.sp); Spacer(Modifier.width(8.dp)); Title(def.name)
            }
            Body(def.desc)
            val notes = ArrayList<String>()
            if (def.needsPower) notes.add(if (b.powered) "Con energía" else "⚡ SIN ENERGÍA: construye un Generador a menos de 10 tiles")
            if (def.feederDiet != null) notes.add("Raciones: ${b.stock}/${def.stock}")
            if (def.upkeep > 0) notes.add("Mantenimiento ${def.upkeep.toInt()} $/min")
            if (def.viewRange > 0) notes.add("Especies visibles: ${w.visitorSim.visibleSpecies(b).joinToString { GameData.species(it).name }.ifEmpty { "ninguna" }}")
            if (def.id == "research_center") notes.add("Produce ${w.researchRate().toInt()} PI/min en total · ${w.s.researchPoints.toInt()} PI acumulados")
            if (def.needsPath && !w.grid.hasAdjacentPath(b.x, b.y, b.w, b.h)) notes.add("⚠ Sin camino adyacente: los visitantes no llegan")
            for (n in notes) Body(n, if (n.startsWith("⚡") || n.startsWith("⚠")) Pal.red else Pal.text2)
        }
        Column(Modifier.padding(end = 44.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (def.id == "entrance") {
                Label("PRECIO DE ENTRADA")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..2) CocButton("${GameData.ENTRY_PRICES[i]} $", { w.setEntryPrice(i) }, small = true, color = if (w.s.entryPrice == i) Pal.green else Pal.darkBtn, dark = if (w.s.entryPrice == i) Pal.greenDark else Pal.darkBtnDark)
                }
                Body("Reputación ${w.s.reputation.toInt()} · ferry cada 20 s")
            } else {
                if (def.feederDiet != null) CocButton("Reponer", { vm.act(w.refillFeeder(b)) }, small = true, icon = "🥕", sub = "${def.refillCost} $")
                CocButton("Demoler", { vm.act(w.demolishBuilding(b)); vm.select(null) }, small = true, color = Pal.red, dark = Pal.redDark, icon = "🔨", sub = "devuelve ${(def.cost * GameData.DEMOLISH_REFUND).toInt()} $")
            }
        }
    }
}

@Composable
private fun EdgePanel(vm: GameViewModel, w: World, e: EdgeRef) {
    val t = w.grid.fenceType(e)
    if (t == 0) { vm.select(null); return }
    val hp = w.grid.fenceHp(e); val maxHp = Fence.hp[t]
    val flags = w.grid.fenceFlags(e)
    val (ra, rb) = w.grid.regionsOfEdge(e)
    val region = w.grid.regions[if (ra != 0) ra else rb]
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Title(if (flags and EdgeFlag.GATE != 0) "Puerta (${Fence.names[t]})" else Fence.names[t])
            Bar("Resistencia", hp.toFloat(), maxHp.toFloat(), color = if (hp < maxHp / 2) Pal.red else Pal.green)
            if (t == Fence.ELECTRIC) Body(if (w.grid.edgePowered(e)) "Electrificada: ataque recibido ×0,3" else "⚡ Sin energía: se comporta como valla ligera", if (w.grid.edgePowered(e)) Pal.text2 else Pal.red)
            if (region != null) {
                val dinos = w.regionDinos(region.id)
                Body("${region.name}: ${region.tiles} tiles (${region.water} agua, ${region.forest} bosque) · ${dinos.size} dinos · valla más débil: ${Fence.names[region.weakestLevel.coerceIn(0, 4)].removePrefix("Valla ")}")
                val weak = dinos.filter { it.def.fenceMin > region.weakestLevel }
                if (weak.isNotEmpty()) Body("⚠ Valla insuficiente para: ${weak.map { it.def.name }.distinct().joinToString()}", Pal.red)
            } else Body("Este tramo no cierra ningún recinto", Pal.text3)
        }
        Column(Modifier.padding(end = 44.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CocButton("Reparar", { vm.act(w.repairFence(e)) }, small = true, enabled = hp < maxHp, icon = "🔧", sub = "${(Fence.cost[t] * GameData.REPAIR_FRACTION).toInt()} $")
                CocButton("Reparar todo", { val c = w.repairAllFences(); vm.message("$c tramos reparados") }, small = true, color = Pal.blue, dark = Pal.blueDark)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CocButton(if (flags and EdgeFlag.GATE == 0) "Hacer puerta" else if (flags and EdgeFlag.OPEN != 0) "Cerrar puerta" else "Abrir puerta", { vm.act(w.toggleGate(e)) }, small = true, color = Pal.gold, dark = Pal.goldDark, icon = "🚪")
                CocButton("Quitar", { vm.act(w.removeFence(e)); vm.select(null) }, small = true, color = Pal.red, dark = Pal.redDark)
            }
        }
    }
}

// ------------------------------------------------------------------ parque, fin de partida y marco de panel
@Composable
fun PauseScreen(vm: GameViewModel, w: World) {
    val s = w.s
    OverlayFrame("Parque · ${w.def.name}", onClose = { vm.overlay = null }, w = w, vm = vm) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CocButton("Seguir jugando", { vm.overlay = null }, small = true, icon = "▶")
            CocButton("Guardar y salir", { vm.exitToMenu() }, small = true, color = Pal.blue, dark = Pal.blueDark, icon = "💾")
            if (w.def.sandbox) CocButton("Ver especies", { val c = w.spawnShowcase(); vm.message("$c dinosaurios de ${GameData.species.size} especies desplegados"); vm.overlay = null; w.s.buildings.firstOrNull { it.type == "entrance" }?.let { e -> vm.focusOn(e.x, e.y - 30) } }, small = true, color = Pal.gold, dark = Pal.goldDark, icon = "🦕")
            if (!w.def.sandbox) CocButton(if (s.tutorialActive) "Quitar tutorial" else "Tutorial", { if (s.tutorialActive) w.skipTutorial() else w.restartTutorial(); vm.overlay = null }, small = true, color = Pal.darkBtn, dark = Pal.darkBtnDark, icon = "🎓")
            Spacer(Modifier.weight(1f))
            AudioToggle("♪", vm.audio.musicOn) { vm.setAudio(music = !vm.audio.musicOn) }
            AudioToggle("🔊", vm.audio.sfxOn) { vm.setAudio(sfx = !vm.audio.sfxOn) }
            AudioToggle("📳", vm.audio.vibrateOn) { vm.setAudio(vibrate = !vm.audio.vibrateOn) }
        }
        Spacer(Modifier.height(8.dp))
        if (!w.def.sandbox) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CocCard(padding = 6) {
                    Text(stars(s.stars), color = Pal.gold, fontSize = 22.sp, fontWeight = FontWeight.Black, style = shadowStyle)
                    Text(if (s.completed) "isla completada" else "5★ durante 60 s", color = Pal.text3, fontSize = 10.sp)
                }
                Bar("Beneficios", s.idxProfit, modifier = Modifier.weight(1f)); Bar("Seguridad", s.idxSafety, color = Pal.blue, modifier = Modifier.weight(1f))
                Bar("Bienestar", s.idxWelfare, modifier = Modifier.weight(1f)); Bar("Atractivo", s.idxAttraction, color = Pal.gold, modifier = Modifier.weight(1f))
                Bar("Reputación", s.reputation, color = Pal.purple, modifier = Modifier.weight(1f))
            }
            Text("5★ = los cuatro índices > 90 · objetivo ${w.def.incomeTarget.toInt()} $/min" + (w.challengeDef?.let { ch -> (if (s.challengeDone) " · ✓ Reto superado: " else " · 🏁 Reto: ") + ch.name + " (${ch.reward} Ámbar a ${ch.starsRequired.toInt()}★)" } ?: ""), color = Pal.text3, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile(money(s.money), "saldo", Modifier.weight(1f), if (s.money < 0) Pal.red else Pal.text)
            StatTile("+" + money(s.incomeMin), "ingr./min", Modifier.weight(1f), Pal.ok)
            StatTile("−" + money(s.expenseMin), "gast./min", Modifier.weight(1f), Pal.red)
            StatTile("${(s.time / 60).toInt()} min", "tiempo", Modifier.weight(1f))
            StatTile("${s.dinos.size}", "dinos", Modifier.weight(1f))
            StatTile("${s.visitors.size + s.virtualVisitors.sumOf { it[1].toInt() }}", "visitantes", Modifier.weight(1f))
            StatTile("${s.buildings.size - 1}", "edificios", Modifier.weight(1f))
            StatTile("${s.amberEarned}", "Ámbar", Modifier.weight(1f), Pal.goldDark)
        }
        if (w.def.sandbox || !s.tutorialActive) {
            Spacer(Modifier.height(8.dp))
            Label("CÓMO EMPEZAR")
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepTile("1", "Recinto", "Construir → Recintos → valla ligera. Arrastra un rectángulo sobre la hierba.", Modifier.weight(1f).fillMaxHeight())
                StepTile("2", "Cuidados", "Dentro: comedero y bebedero. Fuera: camino desde la entrada y un mirador pegado a la valla.", Modifier.weight(1f).fillMaxHeight())
                StepTile("3", "Centros", "Generador, Centro de Expediciones y Laboratorio junto a un camino.", Modifier.weight(1f).fillMaxHeight())
                StepTile("4", "Vida", "Expediciones → envía un equipo. ADN → con 50 % elige recinto e incuba.", Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun AudioToggle(icon: String, on: Boolean, onClick: () -> Unit) {
    CocButton(if (on) "$icon sí" else "$icon no", onClick, color = if (on) Pal.green else Pal.grey, dark = if (on) Pal.greenDark else Pal.greyDark, small = true)
}

@Composable
private fun StepTile(n: String, title: String, text: String, modifier: Modifier) {
    CocCard(modifier, padding = 8) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(Pal.green), contentAlignment = Alignment.Center) { Text(n, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, style = shadowStyle) }
            Spacer(Modifier.width(6.dp))
            Text(title, color = Pal.text, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(text, color = Pal.text2, fontSize = 10.sp, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GameOverScreen(vm: GameViewModel, w: World) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        CocFrame(Modifier.width(420.dp), padding = 14) {
            Ribbon("El parque ha quebrado", color = Pal.red)
            Spacer(Modifier.height(8.dp))
            Body("Ámbar conservado: ${w.s.amberEarned}. Puedes empezar de nuevo en esta isla.")
            Spacer(Modifier.height(10.dp))
            CocButton("Volver al menú", { vm.abandon(w.s.island); vm.world = null; vm.screen = Screen.MENU }, Modifier.fillMaxWidth(), color = Pal.blue, dark = Pal.blueDark)
        }
    }
}

/** Panel lateral claro con marco, cinta de título, reloj y botón rojo de cerrar. Sin desplazamiento vertical. */
@Composable
fun OverlayFrame(title: String, onClose: () -> Unit, w: World? = null, vm: GameViewModel? = null, onTitleClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    vm?.frame
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 50.dp, bottom = 62.dp, end = 6.dp).fillMaxWidth(0.76f).fillMaxHeight()) {
            CocFrame(Modifier.fillMaxSize(), padding = 10) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Ribbon(title, onClick = onTitleClick)
                    Spacer(Modifier.weight(1f))
                    if (w != null) {
                        val t = w.s.time.toInt()
                        val running = w.s.speed > 0 && !w.s.gameOver
                        Text((if (running) "▶ " else "⏸ ") + String.format("%02d:%02d", t / 60, t % 60) + " · " + money(w.s.money), color = if (running) Pal.ok else Pal.red, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(48.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxSize(), content = content)
            }
            CloseButton(onClose, Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
        }
    }
}
