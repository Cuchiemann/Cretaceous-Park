package com.momentadesunt.cretaceouspark.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momentadesunt.cretaceouspark.core.*

// ------------------------------------------------------------------ laboratorio
@Composable
fun LabScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    val st = rememberIncubState()
    OverlayFrame("ADN y Laboratorio", onClose = { vm.overlay = null }, w = w, vm = vm) {
        if (!w.hasBuilding("lab")) Body("Construye un Laboratorio (Construir → Centros) para clonar. Mientras, puedes acumular ADN con expediciones.", Pal.alert)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("ESPECIES · ADN acumulado")
                for (sp in GameData.species) {
                    val dna = s.dna[sp.id] ?: 0
                    if (dna == 0 && !s.cloned.contains(sp.id)) continue
                    val active = st.chosen == sp.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (active) Pal.grassDark else Pal.panel2).clickable { st.chosen = sp.id }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).background(Color(sp.colorBody)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sp.name, color = Pal.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Label("${if (sp.diet == Diet.HERBIVORE) "Herbívoro" else "Carnívoro"} · ${sp.size.label} · ${sp.cost} $ · atractivo ${sp.attraction}")
                            Bar("ADN", dna.toFloat(), color = if (dna >= 50) Pal.grass else Pal.sand)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (dna >= 50) "${w.viability(sp.id, 0)} %" else "—", color = if (dna >= 50) Pal.ok else Pal.ink3, fontSize = 13.sp)
                    }
                }
                if (s.dna.isEmpty()) Body("Aún no hay ADN. Envía una expedición desde la pestaña Expediciones.", Pal.ink3)
                Spacer(Modifier.height(8.dp))
                Label("ÚLTIMOS FÓSILES")
                for (f in s.fossilLog.take(6)) Body("${GameData.species(f.species).name} · calidad ${f.quality} · +${f.dna} % ADN" + (if (f.sold > 0) " · vendido ${f.sold} $" else ""))
            }
            Column(Modifier.weight(1f)) { IncubationPanel(vm, w, st, showSpeciesChooser = false) }
        }
    }
}

// ------------------------------------------------------------------ investigación
@Composable
fun ResearchScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    OverlayFrame("Investigación", onClose = { vm.overlay = null }, w = w, vm = vm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip("PI: ${s.researchPoints.toInt()}", color = Pal.panel2)
            Chip("+${w.researchRate().toInt()} PI/min", color = Pal.panel2)
            val active = s.researchActive?.let { GameData.researchById[it] }
            if (active != null) Chip("En curso: ${active.name} · ${(active.seconds - s.researchProgress).toInt()} s", color = Pal.grassDark)
            if (!w.hasBuilding("research_center")) Chip("Construye un Centro de Investigación", color = Pal.alert, textColor = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (branch in Branch.values()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(branch.label.uppercase())
                    for (node in GameData.research.filter { it.branch == branch }) {
                        val done = s.researchDone.contains(node.id)
                        val activeNode = s.researchActive == node.id
                        val can = w.canResearch(node.id)
                        val bg = when { done -> Pal.grassDark; activeNode -> Pal.sand; can.ok -> Pal.panel2; else -> Pal.line }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(bg).clickable(enabled = can.ok) { vm.act(w.startResearch(node.id)) }.padding(8.dp)) {
                            Text(node.name, color = if (activeNode) Color(0xFF0F1A13) else Pal.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(node.desc, color = if (activeNode) Color(0xFF0F1A13) else Pal.ink2, fontSize = 11.sp)
                            Text(
                                when { done -> "✓ Investigado"; activeNode -> "En curso"; can.ok -> "${node.pi} PI · ${node.cost} $ · ${node.seconds.toInt()} s · toca para investigar"; else -> "${node.pi} PI · ${node.cost} $ · ${can.reason}" },
                                color = if (activeNode) Color(0xFF0F1A13) else if (done) Pal.ok else Pal.ink3, fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ expediciones
@Composable
fun ExpeditionScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    OverlayFrame("Expediciones", onClose = { vm.overlay = null }, w = w, vm = vm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip("Equipos: ${s.expeditions.size}/${w.maxTeams()}", color = Pal.panel2)
            if (!w.hasBuilding("expedition_hq")) Chip("Construye un Centro de Expediciones", color = Pal.alert, textColor = Color.White)
            for (e in s.expeditions) Chip("${GameData.siteById.getValue(e.site).name}: vuelve en ${e.remaining.toInt()} s", color = Pal.grassDark)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (site in GameData.sites) {
                    val unlocked = w.siteUnlocked(site)
                    val can = w.canExpedition(site.id)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (unlocked) Pal.panel2 else Pal.line).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(site.name, color = if (unlocked) Pal.ink else Pal.ink3, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Body(site.species.joinToString { GameData.species(it).name }, if (unlocked) Pal.ink2 else Pal.ink3)
                            Label("${site.cost} $ · ${site.seconds.toInt()} s · ${site.fossils} fósiles" + if (!unlocked) " · 🔒 " + unlockText(site.unlock) else "")
                        }
                        Spacer(Modifier.width(10.dp))
                        BlockButton("Enviar", { vm.act(w.startExpedition(site.id)) }, enabled = can.ok, small = true, sub = if (can.ok) "${site.cost} $" else can.reason)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("FÓSILES RECIENTES")
                if (s.fossilLog.isEmpty()) Body("Sin resultados todavía. Los fósiles suman ADN a cada especie; con ADN ≥ 50 % se puede incubar en 🧬.", Pal.ink3)
                for (f in s.fossilLog) Body("${GameData.species(f.species).name} · ${f.quality} · +${f.dna} %" + (if (f.sold > 0) " · vendido ${f.sold} $" else ""))
                Spacer(Modifier.height(10.dp))
                Label("ADN POR ESPECIE")
                for ((sp, dna) in s.dna.entries.sortedByDescending { it.value }) Bar(GameData.species(sp).name, dna.toFloat(), color = if (dna >= 50) Pal.grass else Pal.sand)
            }
        }
    }
}

private fun unlockText(u: String): String = when {
    u.startsWith("research:") -> "investigación " + (GameData.researchById[u.removePrefix("research:")]?.name ?: "")
    u.startsWith("island:") -> "se abre al jugar en " + (GameData.islandById[u.removePrefix("island:")]?.name ?: "")
    else -> ""
}
