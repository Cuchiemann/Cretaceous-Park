package com.momentadesunt.cretaceouspark.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.momentadesunt.cretaceouspark.core.GameData
import com.momentadesunt.cretaceouspark.core.Size

@Composable
fun App(vm: GameViewModel) {
    when (vm.screen) {
        Screen.MENU -> MenuScreen(vm)
        Screen.ISLANDS -> IslandsScreen(vm)
        Screen.SHOP -> ShopScreen(vm)
        Screen.GAME -> GameScreen(vm)
    }
}

/** Fondo de menú: verde oscuro con bloques suaves. */
@Composable
private fun MenuBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Pal.bg)) {
        Box(Modifier.align(Alignment.BottomStart).size(260.dp, 120.dp).offset(x = (-40).dp, y = 30.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1C2C22)))
        Box(Modifier.align(Alignment.TopEnd).size(320.dp, 140.dp).offset(x = 60.dp, y = (-40).dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1C2C22)))
        content()
    }
}

@Composable
private fun AmberBadge(vm: GameViewModel, modifier: Modifier = Modifier) = ResourceBadge("🟧", "${vm.amber}", modifier, valueColor = Pal.gold, sub = "Ámbar")

@Composable
fun MenuScreen(vm: GameViewModel) {
    vm.frame
    MenuBackground {
        AmberBadge(vm, Modifier.align(Alignment.TopStart).padding(16.dp))
        Text("v0.2 · Sin conexión · Guardado automático", color = Pal.ink3, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(5.dp)).background(Pal.green)); Spacer(Modifier.width(5.dp)); Box(Modifier.size(26.dp).clip(RoundedCornerShape(5.dp)).background(Pal.greenDark))
                Spacer(Modifier.width(16.dp))
                Text("CRETACEOUS PARK", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = shadowStyle)
            }
            Text("Gestión de parque de dinosaurios", color = Pal.ink2, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 26.dp))
            val last = vm.lastIsland
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (last != null && vm.hasSave(last)) CocButton("Continuar", { vm.continueGame(last) }, Modifier.width(210.dp), icon = "▶", sub = GameData.islandById[last]?.name)
                CocButton("Islas", { vm.screen = Screen.ISLANDS }, Modifier.width(210.dp), color = Pal.blue, dark = Pal.blueDark, icon = "🏝", sub = "Elegir o empezar partida")
                CocButton("Tienda de Ámbar", { vm.screen = Screen.SHOP }, Modifier.width(210.dp), color = Pal.gold, dark = Pal.goldDark, icon = "🟧", sub = "Desbloqueos permanentes")
            }
            vm.menuNotice?.let { Text(it, color = Pal.red, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp)) }
        }
    }
}

@Composable
fun IslandsScreen(vm: GameViewModel) {
    vm.frame
    BackHandler { vm.screen = Screen.MENU }
    MenuBackground {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ribbon("Islas", color = Pal.blue)
                Spacer(Modifier.width(10.dp))
                Text("Elige dónde construir tu parque", color = Pal.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                AmberBadge(vm)
                Spacer(Modifier.width(8.dp))
                CloseButton({ vm.screen = Screen.MENU })
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (def in GameData.islands) {
                    val unlocked = vm.islandUnlocked(def)
                    val best = vm.bestStars(def.id)
                    val saved = vm.hasSave(def.id)
                    CocFrame(Modifier.width(250.dp).fillMaxHeight(), body = if (unlocked) Pal.body else Pal.bodyDark, padding = 10) {
                        Ribbon(def.name, color = if (def.sandbox) Pal.blue else if (unlocked) Pal.green else Pal.grey)
                        Spacer(Modifier.height(6.dp))
                        Row { CostBadge("${def.size}×${def.size}", color = Pal.frameLight, icon = "🗺"); Spacer(Modifier.width(6.dp)); CostBadge(money(def.budget.toDouble()), color = Pal.gold) }
                        Spacer(Modifier.height(6.dp))
                        Text(def.desc, color = Pal.text2, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!def.sandbox) Text(stars(best), color = Pal.gold, fontSize = 20.sp, fontWeight = FontWeight.Black, style = shadowStyle)
                        Spacer(Modifier.weight(1f))
                        if (!unlocked) {
                            val i = GameData.islands.indexOf(def)
                            Text("🔒 Consigue ${def.requiredStarsPrev.toInt()}★ en ${GameData.islands[i - 1].name}" + if (def.id == "corona") " y 400 Ámbar" else "", color = Pal.red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            if (!def.sandbox) {
                                Label("RETO OPCIONAL")
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (ch in GameData.challenges) {
                                        val done = vm.challengeDone(def.id, ch.id)
                                        val active = vm.pendingChallenge == ch.id
                                        CocButton((if (done) "✓ " else "") + ch.name, { vm.pendingChallenge = if (active) null else ch.id }, color = if (active) Pal.gold else if (done) Pal.green else Pal.grey, dark = if (active) Pal.goldDark else if (done) Pal.greenDark else Pal.greyDark, small = true, sub = "${ch.reward} Ámbar")
                                    }
                                }
                                vm.pendingChallenge?.let { GameData.challengeById[it]?.let { ch -> Text(ch.desc, color = Pal.goldDark, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2) } }
                                Spacer(Modifier.height(6.dp))
                            }
                            if (saved) {
                                CocButton("Continuar", { vm.continueGame(def.id) }, Modifier.fillMaxWidth(), icon = "▶")
                                Spacer(Modifier.height(5.dp))
                                CocButton("Nueva partida", { vm.abandon(def.id); vm.newGame(def.id) }, Modifier.fillMaxWidth(), color = Pal.red, dark = Pal.redDark, small = true, sub = "borra la guardada")
                            } else CocButton("Empezar", { vm.newGame(def.id) }, Modifier.fillMaxWidth(), icon = "▶")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShopScreen(vm: GameViewModel) {
    vm.frame; vm.shopVersion
    BackHandler { vm.screen = Screen.MENU }
    val groups = Shop.items.groupBy { it.group }
    var group by remember { mutableStateOf(groups.keys.first()) }
    MenuBackground {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ribbon("Tienda de Ámbar", color = Pal.gold)
                Spacer(Modifier.width(10.dp))
                Text("Desbloqueos permanentes para toda partida nueva", color = Pal.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                ResourceBadge("🟧", "${vm.amber}", valueColor = Pal.gold, sub = "${vm.amberTotal()} ganado en total")
                Spacer(Modifier.width(8.dp))
                CloseButton({ vm.screen = Screen.MENU })
            }
            Spacer(Modifier.height(10.dp))
            CocFrame(Modifier.fillMaxSize(), padding = 10) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val icons = mapOf("Presupuesto" to "💰", "Edificios" to "🏗", "Especies" to "🧬", "Pieles" to "🎨")
                    for (g in groups.keys) CocButton(g, { group = g }, color = if (group == g) Pal.gold else Pal.blue, dark = if (group == g) Pal.goldDark else Pal.blueDark, small = true, icon = icons[g])
                }
                Spacer(Modifier.height(8.dp))
                val items = groups.getValue(group)
                // dos filas de tarjetas con desplazamiento horizontal
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cols = (items.size + 1) / 2
                    for (c in 0 until cols) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in 0 until 2) {
                            val idx = c * 2 + r
                            if (idx >= items.size) continue
                            val item = items[idx]
                            val owned = vm.owns(item.id)
                            val superseded = item.id == "budget1" && (vm.owns("budget2") || vm.owns("budget3")) || item.id == "budget2" && vm.owns("budget3")
                            val species = if (item.id.startsWith("dna_")) GameData.speciesById[item.id.removePrefix("dna_")] else if (item.id.startsWith("skin_")) GameData.speciesById[item.id.removePrefix("skin_")] else null
                            CocCard(Modifier.width(200.dp).height(118.dp), selected = owned, dim = superseded, padding = 8) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (species != null) {
                                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color(if (item.id.startsWith("skin_")) species.colorDetail else species.colorBody)).border(1.dp, Pal.frame, RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(item.name, color = Pal.text, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(item.desc, color = Pal.text2, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                when {
                                    owned -> Text("✓ Desbloqueado", color = Pal.ok, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    superseded -> Text("Superado por un nivel mayor", color = Pal.text3, fontSize = 11.sp)
                                    else -> CocButton("${item.cost} Ámbar", { if (!vm.buy(item)) vm.message("Ámbar insuficiente") }, Modifier.fillMaxWidth(), color = Pal.gold, dark = Pal.goldDark, enabled = vm.amber >= item.cost, small = true, icon = "🟧")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
