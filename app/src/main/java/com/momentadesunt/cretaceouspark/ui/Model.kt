package com.momentadesunt.cretaceouspark.ui

import com.momentadesunt.cretaceouspark.core.EdgeRef
import com.momentadesunt.cretaceouspark.core.TerrainTool

sealed class Tool {
    object None : Tool()
    data class FenceTool(val type: Int) : Tool()
    object Gate : Tool()
    object PathTool : Tool()
    object Demolish : Tool()
    data class Terraform(val t: TerrainTool) : Tool()
    data class Build(val defId: String) : Tool()
}

sealed class Selection {
    data class DinoSel(val id: Int) : Selection()
    data class BuildingSel(val id: Int) : Selection()
    data class EdgeSel(val edge: EdgeRef) : Selection()
}

enum class Screen { MENU, ISLANDS, SHOP, GAME }
enum class Overlay { LAB, RESEARCH, EXPEDITIONS, PAUSE }

/** Artículo de la tienda de Ámbar (desbloqueo permanente). */
data class ShopItem(val id: String, val name: String, val desc: String, val cost: Int, val group: String)

object Shop {
    val items: List<ShopItem> = buildList {
        add(ShopItem("budget1", "Presupuesto +10 %", "Más dinero inicial en toda isla nueva.", 50, "Presupuesto"))
        add(ShopItem("budget2", "Presupuesto +20 %", "Sustituye al +10 %.", 100, "Presupuesto"))
        add(ShopItem("budget3", "Presupuesto +30 %", "Sustituye al +20 %.", 200, "Presupuesto"))
        add(ShopItem("start_B1", "Valla media desde el inicio", "Empiezas con la investigación Valla media y comederos automáticos.", 40, "Edificios"))
        add(ShopItem("start_E1", "Galería desde el inicio", "Empiezas con la investigación Galería.", 60, "Edificios"))
        add(ShopItem("start_E3", "Tienda de recuerdos desde el inicio", "Empiezas con la investigación Tienda de recuerdos.", 60, "Edificios"))
        for (sp in com.momentadesunt.cretaceouspark.core.GameData.species) {
            val cost = when (sp.size) { com.momentadesunt.cretaceouspark.core.Size.S -> 30; com.momentadesunt.cretaceouspark.core.Size.M -> 50; else -> 80 }
            add(ShopItem("dna_${sp.id}", sp.name, "Empieza cada isla con 50 % de ADN de ${sp.name}.", cost, "Especies"))
        }
    }
}
