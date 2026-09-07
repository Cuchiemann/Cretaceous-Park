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
import androidx.compose.runtime.mutableIntStateOf
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

/** Estado compartido del panel de incubación (especie, recinto y genes elegidos). */
class IncubState {
    var chosen by mutableStateOf<String?>(null)
    var region by mutableIntStateOf(-1)
    var genes by mutableIntStateOf(0)   // bit 1 piel, 2 resistencia, 4 dócil, 8 vistoso
}

@Composable
fun rememberIncubState(): IncubState = remember { IncubState() }

/** Panel de incubación reutilizable: elige especie (ADN ≥ 50 %), genes y recinto, e incuba. */
@Composable
fun IncubationPanel(vm: GameViewModel, w: World, st: IncubState, showSpeciesChooser: Boolean) {
    val s = w.s
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("INCUBANDO (${s.incubations.size}/${w.maxIncubations()})")
        if (s.incubations.isEmpty()) Body("Ninguna incubación en curso.", Pal.ink3)
        for (inc in s.incubations) Body("${GameData.species(inc.species).name} → Recinto ${inc.region} · ${inc.remaining.toInt()} s")
        Spacer(Modifier.height(4.dp))
        if (showSpeciesChooser) {
            Label("INCUBAR · especies con ADN ≥ 50 %")
            val ready = GameData.species.filter { (s.dna[it.id] ?: 0) >= 50 }
            if (ready.isEmpty()) Body("Ninguna especie tiene ADN suficiente. Envía expediciones desde la pestaña Expediciones.", Pal.alert)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (sp in ready) {
                    val active = st.chosen == sp.id
                    Row(Modifier.clip(RoundedCornerShape(4.dp)).background(if (active) Pal.grass else Pal.panel2).clickable { st.chosen = sp.id }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(Color(sp.colorBody))); Spacer(Modifier.width(6.dp))
                        Column {
                            Text(sp.name, color = if (active) Color(0xFF0F1A13) else Pal.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${s.dna[sp.id]} % · ${sp.cost} $", color = if (active) Color(0xFF0F1A13) else Pal.ink3, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        val sp = st.chosen?.let { GameData.species(it) }
        if (sp == null) { if (!showSpeciesChooser) Label("INCUBAR"); Body("Elige una especie con ADN ≥ 50 % y un recinto de destino.") }
        else {
            val geneCount = Integer.bitCount(st.genes)
            Title(sp.name)
            Body("Necesita ${sp.spaceMin} tiles por animal, grupo de ${sp.groupMin} a ${sp.groupMax}, ${Fence.names[sp.fenceMin]} o mejor." + (if (sp.requiresWaterTiles > 0) " Requiere ${sp.requiresWaterTiles} tiles de agua." else ""))
            Body("Viabilidad ${w.viability(sp.id, geneCount)} % · coste ${sp.cost} $ · ${sp.size.incubationSeconds.toInt()} s")
            if (s.researchDone.contains("G3") || s.researchDone.contains("G5")) {
                Label("GENES (cada uno resta 5 % de viabilidad)")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (s.researchDone.contains("G3")) GeneChip("Piel alternativa", "+1 atractivo", st.genes and 1 != 0) { st.genes = st.genes xor 1 }
                    if (s.researchDone.contains("G5")) {
                        GeneChip("Resistencia", "menos enfermedad y estrés por hambre", st.genes and 2 != 0) { st.genes = st.genes xor 2 }
                        GeneChip("Dócil", "ataque ×0,5", st.genes and 4 != 0) { st.genes = if (st.genes and 4 != 0) st.genes and 4.inv() else (st.genes or 4) and 8.inv() }
                        GeneChip("Vistoso", "+2 atractivo, estrés ×1,3", st.genes and 8 != 0) { st.genes = if (st.genes and 8 != 0) st.genes and 8.inv() else (st.genes or 8) and 4.inv() }
                    }
                }
            }
            Label("RECINTO DE DESTINO")
            val encl = w.enclosures()
            if (encl.isEmpty()) Body("No hay recintos cerrados. Valla un rectángulo con Construir → Recintos.", Pal.alert)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in encl) {
                    val dinos = w.regionDinos(r.id)
                    BlockButton(r.name, { st.region = r.id; vm.highlightRegion = r.id }, small = true, color = if (st.region == r.id) Pal.grass else Pal.panel2, textColor = if (st.region == r.id) Color(0xFF0F1A13) else Pal.ink,
                        sub = "${r.tiles} tiles · ${dinos.size} dinos · ${Fence.names[r.weakestLevel.coerceIn(0, 4)].removePrefix("Valla ")}")
                }
            }
            if (st.region > 0) {
                for (wmsg in w.incubationWarnings(sp.id, st.region)) Body("⚠ $wmsg", Pal.alert)
                val can = w.canIncubate(sp.id, st.region)
                BlockButton("Incubar ${sp.name}", { vm.act(w.startIncubation(sp.id, st.region, st.genes)) }, enabled = can.ok, sub = if (can.ok) "${sp.cost} $" else can.reason)
            }
        }
    }
}

@Composable
fun GeneChip(name: String, desc: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(4.dp)).background(if (active) Pal.grass else Pal.panel2).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(name, color = if (active) Color(0xFF0F1A13) else Pal.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(desc, color = if (active) Color(0xFF0F1A13) else Pal.ink3, fontSize = 10.sp)
    }
}

/** Pestaña Dinosaurios: tus animales, su estado, y la incubación de nuevos. */
@Composable
fun DinosScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    val st = rememberIncubState()
    OverlayFrame("Dinosaurios", onClose = { vm.overlay = null }, w = w, vm = vm) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val species = s.dinos.map { it.species }.toSet()
                Label("TUS DINOSAURIOS · ${s.dinos.size} animales · ${species.size} especies")
                if (s.dinos.isEmpty()) Body("Aún no tienes dinosaurios. Incuba el primero en el panel de la derecha: hace falta un recinto cerrado y una especie con ADN ≥ 50 %.", Pal.ink3)
                for ((spId, list) in s.dinos.groupBy { it.species }.toSortedMap(compareBy { GameData.species(it).name })) {
                    val sp = GameData.species(spId)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(Color(sp.colorBody))); Spacer(Modifier.width(8.dp))
                        Text(sp.name, color = Pal.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Label("${list.size} · ${if (sp.diet == Diet.HERBIVORE) "herbívoro" else "carnívoro"} · grupo ${sp.groupMin}–${sp.groupMax}" + if (list.size < sp.groupMin) " · ⚠ faltan ${sp.groupMin - list.size}" else "")
                    }
                    for (d in list) {
                        val state = when {
                            d.state == DinoState.ESCAPED -> "¡FUGADO!"
                            d.sleep > 0f -> "Dormido"
                            d.sick -> "Enfermo"
                            d.state == DinoState.ATTACK || d.state == DinoState.TO_FENCE -> "Atacando la valla"
                            d.food < 40f -> "Hambre"
                            d.water < 40f -> "Sed"
                            else -> "Tranquilo"
                        }
                        val bad = d.state == DinoState.ESCAPED || d.sick || d.stress >= 75f
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Pal.panel2).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row { Text(state, color = if (bad) Pal.alert else Pal.ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Label(w.grid.regions[d.region]?.name ?: "fuera") }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Bar("Bienestar", d.wellbeing, modifier = Modifier.weight(1f))
                                    Bar("Estrés", d.stress, color = Pal.alert, modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            BlockButton("Ir", { vm.select(Selection.DinoSel(d.id)); vm.pendingFocus = Pair(d.x, d.y); vm.overlay = null }, small = true, color = Pal.sand)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f)) { IncubationPanel(vm, w, st, showSpeciesChooser = true) }
        }
    }
}
