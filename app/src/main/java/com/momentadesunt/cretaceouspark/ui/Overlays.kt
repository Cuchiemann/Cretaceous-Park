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
    var chosen by remember { mutableStateOf<String?>(null) }
    var region by remember { mutableStateOf(-1) }
    var genes by remember { mutableStateOf(0) }   // bit 1 piel, 2 resistencia, 4 dócil, 8 vistoso
    val geneCount = Integer.bitCount(genes)
    OverlayFrame("Laboratorio de ADN e Incubadora", onClose = { vm.overlay = null }) {
        if (!w.hasBuilding("lab")) Body("Construye un Laboratorio (Centros) para clonar. Mientras, puedes acumular ADN con expediciones.", Pal.alert)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // lista de especies
            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("ESPECIES · ADN acumulado")
                for (sp in GameData.species) {
                    val dna = s.dna[sp.id] ?: 0
                    if (dna == 0 && !s.cloned.contains(sp.id)) continue
                    val active = chosen == sp.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (active) Pal.grassDark else Pal.panel2).clickable { chosen = sp.id }.padding(8.dp),
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
                if (s.dna.isEmpty()) Body("Aún no hay ADN. Envía una expedición desde 🌍.", Pal.ink3)
                Spacer(Modifier.height(8.dp))
                Label("INCUBANDO (${s.incubations.size}/${w.maxIncubations()})")
                for (inc in s.incubations) Body("${GameData.species(inc.species).name} → Recinto ${inc.region} · ${inc.remaining.toInt()} s")
            }
            // panel de incubación
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val sp = chosen?.let { GameData.species(it) }
                if (sp == null) { Label("INCUBAR"); Body("Elige una especie con ADN ≥ 50 % y un recinto de destino.") }
                else {
                    Title(sp.name)
                    Body("Necesita ${sp.spaceMin} tiles por animal, grupo de ${sp.groupMin} a ${sp.groupMax}, ${Fence.names[sp.fenceMin]} o mejor." + (if (sp.requiresWaterTiles > 0) " Requiere ${sp.requiresWaterTiles} tiles de agua." else ""))
                    Body("Viabilidad ${w.viability(sp.id, geneCount)} % · coste ${sp.cost} $ · ${sp.size.incubationSeconds.toInt()} s")
                    if (s.researchDone.contains("G3") || s.researchDone.contains("G5")) {
                        Label("GENES (cada uno resta 5 % de viabilidad)")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (s.researchDone.contains("G3")) GeneChip("Piel alternativa", "+1 atractivo", genes and 1 != 0) { genes = genes xor 1 }
                            if (s.researchDone.contains("G5")) {
                                GeneChip("Resistencia", "menos enfermedad y estrés por hambre", genes and 2 != 0) { genes = genes xor 2 }
                                GeneChip("Dócil", "ataque ×0,5", genes and 4 != 0) { genes = if (genes and 4 != 0) genes and 4.inv() else (genes or 4) and 8.inv() }
                                GeneChip("Vistoso", "+2 atractivo, estrés ×1,3", genes and 8 != 0) { genes = if (genes and 8 != 0) genes and 8.inv() else (genes or 8) and 4.inv() }
                            }
                        }
                    }
                    Label("RECINTO DE DESTINO")
                    val encl = w.enclosures()
                    if (encl.isEmpty()) Body("No hay recintos cerrados. Valla un rectángulo con Recintos → valla.", Pal.alert)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (r in encl) {
                            val dinos = w.regionDinos(r.id)
                            BlockButton(r.name, { region = r.id; vm.highlightRegion = r.id }, small = true, color = if (region == r.id) Pal.grass else Pal.panel2, textColor = if (region == r.id) Color(0xFF0F1A13) else Pal.ink,
                                sub = "${r.tiles} tiles · ${dinos.size} dinos · ${Fence.names[r.weakestLevel.coerceIn(0, 4)].removePrefix("Valla ")}")
                        }
                    }
                    if (region > 0) {
                        for (wmsg in w.incubationWarnings(sp.id, region)) Body("⚠ $wmsg", Pal.alert)
                        val can = w.canIncubate(sp.id, region)
                        BlockButton("Incubar ${sp.name}", { vm.act(w.startIncubation(sp.id, region, genes)) }, enabled = can.ok, sub = if (can.ok) "${sp.cost} $" else can.reason)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Label("ÚLTIMOS FÓSILES")
                for (f in s.fossilLog.take(6)) Body("${GameData.species(f.species).name} · calidad ${f.quality} · +${f.dna} % ADN" + (if (f.sold > 0) " · vendido ${f.sold} $" else ""))
            }
        }
    }
}

// ------------------------------------------------------------------ investigación
@Composable
fun ResearchScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    OverlayFrame("Investigación", onClose = { vm.overlay = null }) {
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
    OverlayFrame("Expediciones", onClose = { vm.overlay = null }) {
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

@Composable
private fun GeneChip(name: String, desc: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(4.dp)).background(if (active) Pal.grass else Pal.panel2).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(name, color = if (active) Color(0xFF0F1A13) else Pal.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(desc, color = if (active) Color(0xFF0F1A13) else Pal.ink3, fontSize = 10.sp)
    }
}
