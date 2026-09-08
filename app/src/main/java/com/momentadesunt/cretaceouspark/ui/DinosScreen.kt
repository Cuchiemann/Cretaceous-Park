package com.momentadesunt.cretaceouspark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momentadesunt.cretaceouspark.core.*

/** Estado compartido de la ficha de incubación (especie, recinto y genes elegidos). */
class IncubState {
    var chosen by mutableStateOf<String?>(null)
    var region by mutableIntStateOf(-1)
    var genes by mutableIntStateOf(0)   // bit 1 piel, 2 resistencia, 4 dócil, 8 vistoso
}

@Composable
fun rememberIncubState(): IncubState = remember { IncubState() }

/** Pestaña Dinosaurios: cifras de estado y fichas por especie en tira horizontal. */
@Composable
fun DinosScreen(vm: GameViewModel, w: World) {
    vm.frame
    val s = w.s
    OverlayFrame("Dinosaurios", onClose = { vm.overlay = null }, w = w, vm = vm) {
        val species = s.dinos.map { it.species }.toSet()
        val escaped = s.dinos.count { it.state == DinoState.ESCAPED }
        val sick = s.dinos.count { it.sick }
        val stressed = s.dinos.count { it.stress >= 40f }
        val hungry = s.dinos.count { it.food < 40f || it.water < 40f }
        val avg = if (s.dinos.isEmpty()) 0 else s.dinos.map { it.wellbeing }.average().toInt()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatTile("${s.dinos.size}", "animales", Modifier.weight(1f))
            StatTile("${species.size}", "especies", Modifier.weight(1f))
            StatTile("$avg", "bienestar", Modifier.weight(1f), if (avg >= 60 || s.dinos.isEmpty()) Pal.text else Pal.red)
            StatTile("$escaped", "fugados", Modifier.weight(1f), if (escaped > 0) Pal.red else Pal.text)
            StatTile("$sick", "enfermos", Modifier.weight(1f), if (sick > 0) Pal.red else Pal.text)
            StatTile("$stressed", "estrés", Modifier.weight(1f), if (stressed > 0) Pal.goldDark else Pal.text)
            StatTile("$hungry", "hambre", Modifier.weight(1f), if (hungry > 0) Pal.goldDark else Pal.text)
            CocButton("Incubar", { vm.overlay = Overlay.LAB }, Modifier.weight(1.6f), color = Pal.purple, dark = Pal.purpleDark, icon = "🧬", sub = if (s.incubations.isNotEmpty()) "${s.incubations.size} en curso" else "ir a ADN")
        }
        Spacer(Modifier.height(8.dp))
        if (s.dinos.isEmpty()) Body("Aún no tienes dinosaurios. Ve a ADN e incubar: hace falta un recinto cerrado y una especie con ADN ≥ 50 %.", Pal.text3)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((spId, list) in s.dinos.groupBy { it.species }.toSortedMap(compareBy { GameData.species(it).name })) {
                val sp = GameData.species(spId)
                CocCard(Modifier.width(238.dp).height(168.dp), padding = 0) {
                    Row(Modifier.fillMaxWidth().background(Color(sp.colorBody).copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(sp.colorBody)).border(1.dp, Pal.frame, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sp.name, color = Pal.text, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("grupo ${sp.groupMin}–${sp.groupMax}" + if (list.size < sp.groupMin) " · ⚠ faltan ${sp.groupMin - list.size}" else "", color = if (list.size < sp.groupMin) Pal.redDark else Pal.text2, fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                        }
                        Text("${list.size}", color = if (list.size < sp.groupMin) Pal.redDark else Pal.text, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Column(Modifier.padding(6.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in list) {
                            val state = when {
                                d.state == DinoState.ESCAPED -> "¡FUGADO!"
                                d.sleep > 0f -> "Dormido"
                                d.sick -> "Enfermo"
                                d.state == DinoState.ATTACK || d.state == DinoState.TO_FENCE -> "Ataca la valla"
                                d.food < 40f -> "Hambre"
                                d.water < 40f -> "Sed"
                                d.stress >= 40f -> "Estresado"
                                else -> "Tranquilo"
                            }
                            val bad = d.state == DinoState.ESCAPED || d.sick || d.stress >= 75f || d.state == DinoState.ATTACK
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Pal.bodyDark).clickable { vm.select(Selection.DinoSel(d.id)); vm.pendingFocus = Pair(d.x, d.y); vm.overlay = null }.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(state, color = if (bad) Pal.red else if (state == "Tranquilo") Pal.ok else Pal.goldDark, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                        Spacer(Modifier.width(6.dp))
                                        Text(w.grid.regions[d.region]?.name ?: "fuera", color = Pal.text3, fontSize = 10.sp)
                                    }
                                    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MiniBar(d.wellbeing, Pal.green, Modifier.weight(1f)); MiniBar(d.stress, Pal.red, Modifier.weight(1f))
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("›", color = Pal.blueDark, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
