package com.momentadesunt.cretaceouspark.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.momentadesunt.cretaceouspark.core.*
import com.momentadesunt.cretaceouspark.render.GameView

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("meta", Context.MODE_PRIVATE)
    private val dir = app.filesDir

    var world by mutableStateOf<World?>(null)
    var frame by mutableIntStateOf(0)
    var screen by mutableStateOf(Screen.MENU)
    var overlay by mutableStateOf<Overlay?>(null)
    var tool by mutableStateOf<Tool>(Tool.None)
    var category by mutableStateOf<Category?>(null)
    var selection by mutableStateOf<Selection?>(null)
    var ghost by mutableStateOf<Pair<Int, Int>?>(null)
    var ghostOk by mutableStateOf(false)
    var ghostReason by mutableStateOf("")
    var highlightRegion by mutableIntStateOf(-1)
    var uiMessage by mutableStateOf<String?>(null)
    private var uiMessageTime = 0L
    var pendingFocus: Pair<Float, Float>? = null
    var view: GameView? = null

    // meta
    var amber by mutableIntStateOf(prefs.getInt("amber", 0))
    fun bestStars(island: String) = prefs.getFloat("stars_$island", 0f)
    fun hasSave(island: String) = Save.exists(dir, island)
    var lastIsland: String? = prefs.getString("last", null)

    fun islandUnlocked(def: IslandDef): Boolean {
        if (def.sandbox) return true
        val i = GameData.islands.indexOf(def)
        if (i == 0) return true
        val prev = GameData.islands[i - 1]
        if (def.id == "corona" && amberTotal() < 400) return false
        return bestStars(prev.id) >= def.requiredStarsPrev
    }

    // ------------------------------------------------------------------ tienda de Ámbar
    fun amberTotal() = prefs.getInt("amber_total", amber)
    fun owns(id: String) = prefs.getBoolean("shop_$id", false)
    var shopVersion by mutableIntStateOf(0)

    fun buy(item: ShopItem): Boolean {
        if (owns(item.id) || amber < item.cost) return false
        amber -= item.cost
        prefs.edit().putInt("amber", amber).putBoolean("shop_${item.id}", true).apply()
        shopVersion++
        return true
    }

    /** Aplica los desbloqueos permanentes a una partida nueva. */
    private fun applyMeta(s: GameState) {
        var bonus = 0f
        if (owns("budget3")) bonus = 0.30f else if (owns("budget2")) bonus = 0.20f else if (owns("budget1")) bonus = 0.10f
        s.money += s.money * bonus
        for (sp in GameData.species) if (owns("dna_${sp.id}")) s.dna[sp.id] = maxOf(s.dna[sp.id] ?: 0, 50)
        if (owns("start_B1")) s.researchDone.add("B1")
        if (owns("start_E1")) s.researchDone.add("E1")
        if (owns("start_E3")) s.researchDone.add("E3")
    }

    // ------------------------------------------------------------------ ciclo de partida
    fun newGame(islandId: String) {
        val def = GameData.islandById.getValue(islandId)
        val s = IslandGen.generate(def, System.currentTimeMillis())
        applyMeta(s)
        startWorld(World(s))
    }

    fun continueGame(islandId: String): Boolean {
        val s = Save.read(dir, islandId) ?: return false
        startWorld(World(s))
        return true
    }

    private fun startWorld(w: World) {
        world = w
        w.afterLoad()
        tool = Tool.None; category = null; selection = null; ghost = null; overlay = null
        screen = Screen.GAME
        lastIsland = w.s.island
        prefs.edit().putString("last", w.s.island).apply()
        if (w.s.time < 1f) w.say("Bienvenido a ${w.def.name}. Construye un recinto con vallas y un camino desde la entrada.")
    }

    fun save(async: Boolean = true) {
        val w = world ?: return
        try { Save.write(dir, w.s, async) } catch (_: Exception) {}
        bankMeta(w)
    }

    private fun bankMeta(w: World) {
        val e = prefs.edit()
        val best = bestStars(w.s.island)
        if (w.s.stars > best) e.putFloat("stars_${w.s.island}", w.s.stars)
        if (w.s.amberEarned > w.s.amberBanked) {
            val gained = w.s.amberEarned - w.s.amberBanked
            amber += gained
            w.s.amberBanked = w.s.amberEarned
            e.putInt("amber", amber)
            e.putInt("amber_total", amberTotal() + gained)
        }
        e.apply()
    }

    fun exitToMenu() {
        save(async = false)
        world = null
        screen = Screen.MENU
        overlay = null
    }

    fun abandon(islandId: String) {
        Save.delete(dir, islandId)
        frame++
    }

    // ------------------------------------------------------------------ desde la vista
    fun onFrame() {
        frame++
        val w = world ?: return
        if (w.amberFlash > 0) { w.amberFlash = 0; bankMeta(w) }
        if (uiMessage != null && System.currentTimeMillis() - uiMessageTime > 3000) uiMessage = null
        if (ghost != null) refreshGhost()
    }

    fun message(msg: String) { uiMessage = msg; uiMessageTime = System.currentTimeMillis() }

    fun select(sel: Selection?) {
        selection = sel
        highlightRegion = -1
        val w = world ?: return
        when (sel) {
            is Selection.DinoSel -> w.s.dinos.firstOrNull { it.id == sel.id }?.let { highlightRegion = it.region }
            is Selection.EdgeSel -> { val (a, b) = w.grid.regionsOfEdge(sel.edge); highlightRegion = if (a != 0) a else b }
            else -> {}
        }
    }

    fun useTool(t: Tool) {
        tool = t
        ghost = null
        if (t !is Tool.None) selection = null
    }

    fun setGhost(x: Int, y: Int) { ghost = Pair(x, y); refreshGhost() }

    private fun refreshGhost() {
        val w = world ?: return
        val t = tool as? Tool.Build ?: return
        val g = ghost ?: return
        val r = w.canPlaceBuilding(GameData.building(t.defId), g.first, g.second)
        ghostOk = r.ok; ghostReason = r.reason
    }

    fun focusOn(x: Int, y: Int) { if (x >= 0 && y >= 0) pendingFocus = Pair(x + 0.5f, y + 0.5f) }

    fun focusAlert(a: Alert) {
        val w = world ?: return
        if (a.entityId > 0) {
            w.s.dinos.firstOrNull { it.id == a.entityId }?.let { select(Selection.DinoSel(it.id)); pendingFocus = Pair(it.x, it.y); return }
            w.s.buildings.firstOrNull { it.id == a.entityId }?.let { select(Selection.BuildingSel(it.id)); focusOn(it.x, it.y); return }
        }
        focusOn(a.x, a.y)
    }

    fun selectedDino(): Dino? = (selection as? Selection.DinoSel)?.let { s -> world?.s?.dinos?.firstOrNull { it.id == s.id } }
    fun selectedBuilding(): Building? = (selection as? Selection.BuildingSel)?.let { s -> world?.s?.buildings?.firstOrNull { it.id == s.id } }
    fun selectedEdge(): EdgeRef? = (selection as? Selection.EdgeSel)?.edge

    fun act(r: Result) { if (!r.ok) message(r.reason) }
}
