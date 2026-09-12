package com.momentadesunt.cretaceouspark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.momentadesunt.cretaceouspark.render.DinoPreviewView

// ------------------------------------------------------------------ ADN: tira de especies + ficha compacta
@Composable
fun LabScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    val st = rememberIncubState()
    val chosen = st.chosen
    OverlayFrame(if (chosen == null) "ADN e incubación" else "← " + (if ((s.dna[chosen] ?: 0) > 0 || s.cloned.contains(chosen)) GameData.species(chosen).name else "Especie desconocida"),
        onClose = { if (st.chosen != null) st.chosen = null else vm.overlay = null }, w = w, vm = vm,
        onTitleClick = if (chosen != null) ({ st.chosen = null }) else null) {
        if (chosen == null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("ESPECIES · ${s.dna.count { it.value > 0 }} descubiertas de ${GameData.species.size} · toca una para ver su ficha e incubar")
                Spacer(Modifier.weight(1f))
                if (!w.hasBuilding("lab")) CocButton("Sin Laboratorio", {}, color = Pal.red, dark = Pal.redDark, small = true, icon = "⚠", sub = "Construir → Centros")
                CostBadge("Incubando ${s.incubations.size}/${w.maxIncubations()}", color = if (s.incubations.isEmpty()) Pal.frameLight else Pal.purple, icon = "🧬")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 8) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 2) {
                        val sp = GameData.species[row * 8 + col]
                        val dna = s.dna[sp.id] ?: 0
                        val known = dna > 0 || s.cloned.contains(sp.id)
                        val ready = dna >= 50
                        CocCard(Modifier.width(142.dp).height(96.dp), dim = !known, onClick = { st.chosen = sp.id; st.region = -1; st.genes = 0 }, padding = 7) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(if (known) Color(sp.colorBody) else Pal.grey).border(1.dp, Pal.frame, RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(if (known) sp.name else "???", color = if (known) Pal.text else Pal.text3, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.weight(1f))
                            if (known) {
                                Text("${if (sp.diet == Diet.HERBIVORE) "Herbívoro" else "Carnívoro"} · ${sp.size.label}", color = Pal.text2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                MiniBar(dna.toFloat(), if (ready) Pal.green else Pal.gold, Modifier.fillMaxWidth())
                                Text(if (ready) "ADN $dna % · listo" else "ADN $dna %", color = if (ready) Pal.ok else Pal.text3, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Sin datos", color = Pal.text3, fontSize = 10.sp)
                                Text("🔍 " + (GameData.siteById[sp.site]?.name ?: ""), color = Pal.text3, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else SpeciesSheet(vm, w, st, GameData.species(chosen))
    }
}

/** Ficha compacta: vista previa | datos + genes + ADN | Incubar + recintos. */
@Composable
private fun SpeciesSheet(vm: GameViewModel, w: World, st: IncubState, sp: SpeciesDef) {
    vm.frame   // observa el contador de fotogramas: sin esto Compose salta la recomposición (strong skipping)
    val s = w.s
    val dna = s.dna[sp.id] ?: 0
    val known = dna > 0 || s.cloned.contains(sp.id)
    val geneCount = Integer.bitCount(st.genes)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(132.dp).clip(RoundedCornerShape(12.dp)).background(Pal.frame).padding(3.dp).clip(RoundedCornerShape(10.dp)).background(Pal.bodyDark)) {
            if (known) AndroidView(factory = { ctx -> DinoPreviewView(ctx, sp.id, if (st.genes and 1 != 0) 1 else 0) }, update = { it.species = sp.id; it.skin = if (st.genes and 1 != 0) 1 else 0 }, modifier = Modifier.fillMaxSize())
            else Text("???", color = Pal.text3, fontSize = 36.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!known) {
                Text("???", color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Body("Todavía no tienes ADN de esta especie. Sus fósiles aparecen en ${GameData.siteById[sp.site]?.name ?: "?"}: envía una expedición desde la pestaña Expediciones.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sp.name, color = Pal.text, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    CostBadge(if (sp.diet == Diet.HERBIVORE) "Herbívoro" else "Carnívoro", color = if (sp.diet == Diet.HERBIVORE) Pal.green else Pal.red, icon = "")
                    Spacer(Modifier.width(4.dp))
                    CostBadge(sp.size.label, color = Pal.frameLight, icon = "")
                }
                Text("Grupo ${sp.groupMin}–${sp.groupMax} · ${sp.spaceMin} tiles/animal · valla ${Fence.names[sp.fenceMin].removePrefix("Valla ").lowercase()} · peligro ${sp.danger}/10 · atractivo ${sp.attraction}/10" + (if (sp.requiresWaterTiles > 0) " · ${sp.requiresWaterTiles} tiles de agua" else ""), color = Pal.text2, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
            }
            Label("MODIFICACIONES GENÉTICAS")
            val skinOk = s.researchDone.contains("G3") || vm.ownsSkin(sp.id)
            val g5 = s.researchDone.contains("G5")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GeneSquare("Piel alternativa", "colores invertidos · +1 atractivo", st.genes and 1 != 0, skinOk && known, if (skinOk) "" else "Gen de Piel (G3) o tienda", Modifier.weight(1f)) { st.genes = st.genes xor 1 }
                GeneSquare("Resistencia", "menos enfermedad y estrés", st.genes and 2 != 0, g5 && known, "Genes (G5)", Modifier.weight(1f)) { st.genes = st.genes xor 2 }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GeneSquare("Dócil", "ataque ×0,5 · −1 atractivo", st.genes and 4 != 0, g5 && known, "Genes (G5)", Modifier.weight(1f)) { st.genes = if (st.genes and 4 != 0) st.genes and 4.inv() else (st.genes or 4) and 8.inv() }
                GeneSquare("Vistoso", "+2 atractivo · estrés ×1,3", st.genes and 8 != 0, g5 && known, "Genes (G5)", Modifier.weight(1f)) { st.genes = if (st.genes and 8 != 0) st.genes and 8.inv() else (st.genes or 8) and 4.inv() }
            }
        }
        Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val encl = w.enclosures()
            val can = if (!known) Result.fail("Sin ADN") else if (st.region > 0) w.canIncubate(sp.id, st.region) else Result.fail(if (encl.isEmpty()) "Sin recintos cerrados" else "Elige un recinto")
            CocButton("INCUBAR", { vm.act(w.startIncubation(sp.id, st.region, st.genes)) }, Modifier.fillMaxWidth(), enabled = can.ok, icon = "🥚",
                sub = if (!known) can.reason else "${money(sp.cost.toFloat())} · ${w.viability(sp.id, geneCount)} % viabilidad")
            if (known && !can.ok) Text("✖ " + can.reason, color = Pal.red, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis)
            if (known) Text("ADN $dna %" + (if (dna < 50) " (mín. 50 %)" else "") + " · ${sp.size.incubationSeconds.toInt()} s de incubación" + if (geneCount > 0) " · −5 % viabilidad/gen" else "", color = Pal.text2, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis)
            Label("RECINTO · ${encl.size}")
            if (encl.isEmpty()) Text("Valla un rectángulo con Construir → Recintos.", color = Pal.red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in encl) {
                    val dinos = w.regionDinos(r.id)
                    val active = st.region == r.id
                    Column(
                        Modifier.width(94.dp).height(58.dp).clip(RoundedCornerShape(8.dp)).background(if (active) Pal.red else Pal.redDark.copy(alpha = 0.55f))
                            .border(2.dp, if (active) Pal.gold else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { st.region = r.id; vm.highlightRegion = r.id }.padding(6.dp), verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(r.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, style = shadowStyle)
                        Text("${r.tiles} tiles · ${dinos.size} dinos", color = Color.White, fontSize = 9.sp, maxLines = 1)
                        Text(Fence.names[r.weakestLevel.coerceIn(0, 4)].removePrefix("Valla "), color = Color.White, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
            if (st.region > 0) {
                val warns = w.incubationWarnings(sp.id, st.region)
                if (warns.isNotEmpty()) Text("⚠ " + warns.first() + if (warns.size > 1) " (+${warns.size - 1})" else "", color = Pal.red, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GeneSquare(name: String, desc: String, active: Boolean, enabled: Boolean, lockText: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(60.dp).clip(RoundedCornerShape(8.dp)).background(if (!enabled) Pal.greyDark else Pal.purpleDark).clickable(enabled = enabled, onClick = onClick)) {
        Column(
            Modifier.fillMaxSize().padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (!enabled) Pal.grey else if (active) Pal.purple else Pal.purple.copy(alpha = 0.55f))
                .border(2.dp, if (active) Pal.gold else Color.Transparent, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp), verticalArrangement = Arrangement.Center
        ) {
            Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, style = shadowStyle)
            Text(if (enabled) desc else "🔒 $lockText", color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------------ investigación
@Composable
fun ResearchScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    var branch by remember { mutableStateOf(Branch.WELFARE) }
    OverlayFrame("Investigación", onClose = { vm.overlay = null }, w = w, vm = vm) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile("${s.researchPoints.toInt()}", "puntos (PI)", Modifier.weight(1f), Pal.blueDark)
            StatTile("+${w.researchRate().toInt()}", "PI / min", Modifier.weight(1f), if (w.researchRate() > 0f) Pal.text else Pal.red)
            StatTile("${s.researchDone.size}/${GameData.research.size}", "investigado", Modifier.weight(1f))
            val active = s.researchActive?.let { GameData.researchById[it] }
            CocCard(Modifier.weight(3f).height(54.dp), padding = 6) {
                if (active != null) {
                    Text("🔬 ${active.name} · ${(active.seconds - s.researchProgress).toInt()} s", color = Pal.text, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    MiniBar((s.researchProgress / active.seconds * 100f), Pal.blue, Modifier.fillMaxWidth())
                } else if (!w.hasBuilding("research_center")) {
                    Text("⚠ Sin Centro de Investigación", color = Pal.red, fontWeight = FontWeight.Black, fontSize = 12.sp); Text("Construir → Centros", color = Pal.text3, fontSize = 10.sp)
                } else {
                    Text("Ningún nodo en curso", color = Pal.text, fontWeight = FontWeight.Black, fontSize = 12.sp); Text("elige uno abajo", color = Pal.text3, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val icons = mapOf(Branch.WELFARE to "🌿", Branch.FUN to "🎡", Branch.GENETICS to "🧬")
            for (b in Branch.values()) {
                val done = GameData.research.count { it.branch == b && s.researchDone.contains(it.id) }
                CocButton("${b.label} · $done/6", { branch = b }, Modifier.weight(1f), color = if (branch == b) Pal.gold else Pal.blue, dark = if (branch == b) Pal.goldDark else Pal.blueDark, small = true, icon = icons[b])
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (node in GameData.research.filter { it.branch == branch }) {
                val done = s.researchDone.contains(node.id)
                val activeNode = s.researchActive == node.id
                val can = w.canResearch(node.id)
                CocCard(Modifier.width(212.dp).height(134.dp), selected = activeNode, dim = !can.ok && !done && !activeNode, padding = 8) {
                    Text(node.name, color = if (can.ok || done || activeNode) Pal.text else Pal.text3, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 2, lineHeight = 16.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(node.desc, color = if (can.ok || done || activeNode) Pal.text2 else Pal.text3, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    when {
                        done -> Text("✓ Investigado", color = Pal.ok, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        activeNode -> Column { Text("En curso · ${(node.seconds - s.researchProgress).toInt()} s", color = Pal.blueDark, fontWeight = FontWeight.Black, fontSize = 12.sp); MiniBar(s.researchProgress / node.seconds * 100f, Pal.blue, Modifier.fillMaxWidth().padding(top = 3.dp)) }
                        can.ok -> CocButton("Investigar", { vm.act(w.startResearch(node.id)) }, Modifier.fillMaxWidth(), small = true, icon = "🔬", sub = "${node.pi} PI · ${node.cost} $ · ${node.seconds.toInt()} s")
                        else -> Column { Row { CostBadge("${node.pi} PI", color = Pal.grey, icon = ""); Spacer(Modifier.width(4.dp)); CostBadge("${node.cost}", color = Pal.grey) }; Text(can.reason, color = Pal.text3, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ expediciones
private val siteColors = mapOf(
    "canon_rojo" to Color(0xFFB5542E), "estepa_gris" to Color(0xFF7C8A80), "costa_de_sal" to Color(0xFF3F8FB5),
    "bosque_petrificado" to Color(0xFF6B4A2B), "desierto_blanco" to Color(0xFFC9B27A), "glaciar_norte" to Color(0xFF6FB3CF)
)

@Composable
fun ExpeditionScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    OverlayFrame("Expediciones", onClose = { vm.overlay = null }, w = w, vm = vm) {
      // Desplazamiento vertical de respaldo: con escalas de fuente grandes el contenido no cabe en el panel fijo.
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile("${w.maxTeams() - s.expeditions.size}/${w.maxTeams()}", "equipos libres", Modifier.weight(1f).fillMaxHeight(), if (s.expeditions.size < w.maxTeams()) Pal.text else Pal.goldDark)
            StatTile("${s.sitesUnlocked.size}/${GameData.sites.size}", "yacimientos", Modifier.weight(1f).fillMaxHeight())
            StatTile("${s.dna.count { it.value > 0 }}", "con ADN", Modifier.weight(1f).fillMaxHeight())
            StatTile("${s.dna.count { it.value >= 50 }}", "incubables", Modifier.weight(1f).fillMaxHeight(), if (s.dna.any { it.value >= 50 }) Pal.ok else Pal.text)
            CocCard(Modifier.weight(2f).fillMaxHeight(), padding = 6) {
                if (!w.hasBuilding("expedition_hq")) { Text("⚠ Sin Centro de Expediciones", color = Pal.red, fontWeight = FontWeight.Black, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1); Text("Construir → Centros", color = Pal.text3, fontSize = 10.sp, lineHeight = 12.sp) }
                else { Text("Cada fósil suma ADN a su especie", color = Pal.text, fontWeight = FontWeight.Black, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("con 50 % ya se puede incubar", color = Pal.text3, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Las tarjetas toman la altura de la más alta (la de más especies) en vez de una fija: así el botón nunca se recorta.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (site in GameData.sites) {
                val unlocked = w.siteUnlocked(site)
                val can = w.canExpedition(site.id)
                val active = s.expeditions.firstOrNull { it.site == site.id }
                val col = siteColors[site.id] ?: Pal.gold
                CocCard(Modifier.width(232.dp).fillMaxHeight(), dim = !unlocked, padding = 0) {
                    Row(Modifier.fillMaxWidth().height(28.dp).background(if (unlocked) col else Pal.grey).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(site.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, style = shadowStyle)
                        Spacer(Modifier.weight(1f))
                        Text("${site.seconds.toInt()} s · ${site.fossils} fósiles", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, style = shadowStyle)
                    }
                    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (spId in site.species) {
                                val sp = GameData.species(spId); val dna = s.dna[spId] ?: 0
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(if (unlocked) Color(sp.colorBody) else Pal.grey)); Spacer(Modifier.width(5.dp))
                                    Text(sp.name, color = if (unlocked) Pal.text else Pal.text3, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                                    if (dna > 0) Text("$dna %", color = if (dna >= 50) Pal.ok else Pal.text3, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        when {
                            active != null -> Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("EN CAMPO", color = Pal.text3, fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold); Text("vuelve en ${active.remaining.toInt()} s", color = Pal.text3, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.height(3.dp))
                                MiniBar((1f - active.remaining / site.seconds) * 100f, col, Modifier.fillMaxWidth())
                            }
                            !unlocked -> Text("🔒 ${unlockText(site.unlock)}", color = Pal.text3, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
                            else -> CocButton("Enviar equipo", { vm.act(w.startExpedition(site.id)) }, Modifier.fillMaxWidth(), enabled = can.ok, small = true, icon = "🚁", sub = if (can.ok) "${site.cost} $" else can.reason)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("FÓSILES ")
            if (s.fossilLog.isEmpty()) Text("sin resultados todavía", color = Pal.text3, fontSize = 11.sp, lineHeight = 14.sp)
            for (f in s.fossilLog.take(12)) {
                val sp = GameData.species(f.species)
                val qColor = when (f.quality) { "Raro" -> Pal.goldDark; "Alta" -> Pal.ok; "Media" -> Pal.blueDark; else -> Pal.text3 }
                Row(Modifier.clip(RoundedCornerShape(6.dp)).background(Pal.card).border(1.dp, Pal.frameLight.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(sp.colorBody))); Spacer(Modifier.width(5.dp))
                    Text(sp.name, color = Pal.text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black); Spacer(Modifier.width(5.dp))
                    Text(f.quality, color = qColor, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black); Spacer(Modifier.width(5.dp))
                    Text(if (f.sold > 0) "vendido ${f.sold} $" else "+${f.dna} %", color = Pal.text2, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
      }
    }
}

private fun unlockText(u: String): String = when {
    u.startsWith("research:") -> "Investiga " + (GameData.researchById[u.removePrefix("research:")]?.name ?: "")
    u.startsWith("island:") -> "Se abre al jugar en " + (GameData.islandById[u.removePrefix("island:")]?.name ?: "")
    else -> ""
}
