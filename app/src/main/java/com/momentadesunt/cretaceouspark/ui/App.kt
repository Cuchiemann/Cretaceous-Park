package com.momentadesunt.cretaceouspark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momentadesunt.cretaceouspark.core.GameData
import androidx.activity.compose.BackHandler

@Composable
fun App(vm: GameViewModel) {
    when (vm.screen) {
        Screen.MENU -> MenuScreen(vm)
        Screen.ISLANDS -> IslandsScreen(vm)
        Screen.SHOP -> ShopScreen(vm)
        Screen.GAME -> GameScreen(vm)
    }
}

@Composable
fun MenuScreen(vm: GameViewModel) {
    vm.frame
    Box(Modifier.fillMaxSize().background(Pal.bg)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(Pal.grass)); Spacer(Modifier.width(4.dp)); Box(Modifier.size(22.dp).background(Pal.grassDark))
                Spacer(Modifier.width(14.dp))
                Text("CRETACEOUS PARK", color = Pal.ink, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
            Text("Gestión de parque de dinosaurios · prototipo jugable", color = Pal.ink3, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))
            val last = vm.lastIsland
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (last != null && vm.hasSave(last)) {
                    BlockButton("Continuar", { vm.continueGame(last) }, Modifier.width(200.dp), sub = GameData.islandById[last]?.name)
                }
                BlockButton("Islas", { vm.screen = Screen.ISLANDS }, Modifier.width(200.dp), color = Pal.sand, sub = "Elegir o empezar partida")
                BlockButton("Tienda de Ámbar", { vm.screen = Screen.SHOP }, Modifier.width(200.dp), color = Pal.amber, sub = "Desbloqueos permanentes")
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).background(Pal.amber))
                Spacer(Modifier.width(8.dp))
                Text("Ámbar: ${vm.amber}", color = Pal.amber, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text("Se gana con clonaciones, estrellas e islas completadas y se gasta en la tienda.", color = Pal.ink3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text("v0.1 · Sin conexión · Guardado automático", color = Pal.ink3, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
fun IslandsScreen(vm: GameViewModel) {
    vm.frame
    BackHandler { vm.screen = Screen.MENU }
    Column(Modifier.fillMaxSize().background(Pal.bg).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlockButton("← Menú", { vm.screen = Screen.MENU }, color = Pal.panel2, textColor = Pal.ink, small = true)
            Spacer(Modifier.width(16.dp))
            Text("Islas", color = Pal.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Ámbar ${vm.amber}", color = Pal.amber, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(GameData.islands) { def ->
                val unlocked = vm.islandUnlocked(def)
                val best = vm.bestStars(def.id)
                val saved = vm.hasSave(def.id)
                Column(
                    Modifier.width(230.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Pal.panel2).padding(14.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(if (def.sandbox) Pal.water else if (unlocked) Pal.grass else Pal.line))
                    Spacer(Modifier.height(10.dp))
                    Text(def.name, color = Pal.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Label("${def.size} × ${def.size} · ${money(def.budget.toDouble())}")
                    Spacer(Modifier.height(8.dp))
                    Body(def.desc)
                    Spacer(Modifier.height(8.dp))
                    if (!def.sandbox) Text(stars(best), color = Pal.amber, fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    if (!unlocked) {
                        val i = GameData.islands.indexOf(def)
                        Body("Bloqueada: consigue ${def.requiredStarsPrev.toInt()}★ en ${GameData.islands[i - 1].name}", Pal.alert)
                    } else {
                        if (saved) {
                            BlockButton("Continuar", { vm.continueGame(def.id) }, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            BlockButton("Nueva partida", { vm.abandon(def.id); vm.newGame(def.id) }, Modifier.fillMaxWidth(), color = Pal.panel, textColor = Pal.ink2, small = true, sub = "borra la guardada")
                        } else BlockButton("Empezar", { vm.newGame(def.id) }, Modifier.fillMaxWidth())
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
    Column(Modifier.fillMaxSize().background(Pal.bg).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlockButton("← Menú", { vm.screen = Screen.MENU }, color = Pal.panel2, textColor = Pal.ink, small = true)
            Spacer(Modifier.width(16.dp))
            Text("Tienda de Ámbar", color = Pal.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).background(Pal.amber)); Spacer(Modifier.width(8.dp))
                Text("${vm.amber} disponible · ${vm.amberTotal()} ganado en total", color = Pal.amber, fontWeight = FontWeight.Bold)
            }
        }
        Body("Los desbloqueos son permanentes y se aplican a cada partida nueva. Isla Corona requiere 400 Ámbar ganados en total.")
        Spacer(Modifier.height(12.dp))
        val groups = Shop.items.groupBy { it.group }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(groups.keys.toList()) { g ->
                Column(Modifier.width(300.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Pal.panel2).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(g.uppercase())
                    for (item in groups.getValue(g)) {
                        val owned = vm.owns(item.id)
                        val superseded = item.id == "budget1" && (vm.owns("budget2") || vm.owns("budget3")) || item.id == "budget2" && vm.owns("budget3")
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (owned) Pal.grassDark else Pal.panel).padding(8.dp)) {
                            Text(item.name, color = Pal.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Body(item.desc)
                            Spacer(Modifier.height(4.dp))
                            if (owned || superseded) Label(if (owned) "✓ Desbloqueado" else "Superado por un nivel mayor")
                            else BlockButton("Comprar · ${item.cost} Ámbar", { if (!vm.buy(item)) vm.message("Ámbar insuficiente") }, small = true, enabled = vm.amber >= item.cost, color = Pal.amber)
                        }
                    }
                }
            }
        }
    }
}
