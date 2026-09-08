package com.momentadesunt.cretaceouspark.core

/**
 * Tutorial guiado de la primera isla: objetivos encadenados que se comprueban solos,
 * con una instrucción breve y una pista de dónde está la herramienta. Sin recompensas: solo enseña a jugar.
 */
class TutorialStep(
    val title: String,
    val text: String,
    val reward: Int,
    val category: Category? = null,          // pestaña de construcción que conviene abrir
    val check: (World) -> Boolean
)

object Tutorial {
    val steps: List<TutorialStep> = listOf(
        TutorialStep("Bienvenido a Isla Brote",
            "Arrastra con un dedo para mover la cámara y usa + / − o el pellizco para el zoom. El botón ⌂ te devuelve a la entrada del parque.",
            0) { it.cameraMoved },
        TutorialStep("Construye un recinto",
            "Abre Construir → Recintos, elige Valla ligera y arrastra un rectángulo sobre la hierba de al menos 6×6 tiles. Deja hueco: las vallas no pueden tocar caminos.",
            0, Category.ENCLOSURE) { w -> w.enclosures().any { it.tiles >= 36 } },
        TutorialStep("Comida para herbívoros",
            "Coloca un Comedero de herbívoros dentro del recinto. Toca el recinto para ver la sombra verde y toca de nuevo para confirmar.",
            0, Category.ENCLOSURE) { w -> w.s.buildings.any { it.type == "feeder_herb" && w.grid.regionAt(it.x, it.y) > 0 } },
        TutorialStep("Agua en el recinto",
            "Los dinosaurios también beben. Pon un Bebedero dentro del recinto o valla una zona con un lago.",
            0, Category.ENCLOSURE) { w -> w.enclosures().any { it.water > 0 } },
        TutorialStep("Un camino hasta el recinto",
            "Abre Construir → Caminos y arrastra desde el camino de la entrada hasta llegar junto a la valla. Los visitantes solo se mueven por caminos.",
            0, Category.PATHS) { w -> w.pathReachesEnclosure() },
        TutorialStep("Un mirador para los visitantes",
            "Coloca un Mirador pegado por fuera a la valla, con un camino al lado. Desde ahí los visitantes verán a tus dinosaurios.",
            0, Category.PATHS) { w -> w.s.buildings.any { it.type == "viewpoint" || it.type == "gallery" } },
        TutorialStep("Energía",
            "Abre Construir → Centros y construye un Generador junto a un camino. Da energía en 10 tiles a laboratorios, tiendas y vallas eléctricas.",
            0, Category.CENTERS) { w -> w.hasBuilding("generator") },
        TutorialStep("Centro de Expediciones",
            "Construye el Centro de Expediciones junto a un camino. Sus equipos traen los fósiles con el ADN de cada especie.",
            0, Category.CENTERS) { w -> w.hasBuilding("expedition_hq") },
        TutorialStep("Envía una expedición",
            "Abre la pestaña Expediciones (abajo) y envía un equipo al Cañón Rojo. Tarda 60 s y vuelve con fósiles de herbívoros pequeños.",
            0) { w -> w.s.expeditions.isNotEmpty() || w.s.fossilLog.isNotEmpty() },
        TutorialStep("Laboratorio e Incubadora",
            "Construye el Laboratorio junto a un camino y dentro del alcance del Generador (necesita energía).",
            0, Category.CENTERS) { w -> w.s.buildings.any { it.type == "lab" && it.powered } },
        TutorialStep("Tu primer dinosaurio",
            "Abre la pestaña ADN (abajo) y toca una especie con ADN ≥ 50 %. En su ficha elige el recinto y pulsa Incubar. Si falta ADN, envía más expediciones.",
            0) { w -> w.s.dinos.isNotEmpty() },
        TutorialStep("Compañía",
            "Los Gallimimus viven en grupos de 3 o más. Incuba hasta tener tres del mismo tipo en el recinto, o se estresarán.",
            0) { w -> w.s.dinos.groupBy { it.species }.any { (sp, list) -> list.size >= GameData.species(sp).groupMin } },
        TutorialStep("Servicios para visitantes",
            "Abre Construir → Servicios y coloca una Tienda de comida junto al camino y con energía. Los visitantes cómodos gastan más.",
            0, Category.SERVICES) { w -> w.hasBuilding("shop_food") || w.hasBuilding("shop_drink") },
        TutorialStep("Investigación",
            "Construye un Centro de Investigación y en la pestaña Investigar empieza un nodo: la valla media y los comederos automáticos son una buena primera compra.",
            0, Category.CENTERS) { w -> w.s.researchActive != null || w.s.researchDone.isNotEmpty() },
        TutorialStep("Seguridad",
            "Construye un Centro de Rangers. Con él puedes dormir con dardos a un dinosaurio fugado, curar enfermos y transportarlos.",
            0, Category.CENTERS) { w -> w.hasBuilding("ranger_station") },
        TutorialStep("Primera estrella",
            "Abre ☰ para ver los cuatro índices. Sube el Atractivo con más especies y el Bienestar cuidando las necesidades hasta lograr 1★.",
            0) { w -> w.s.stars >= 1f }
    )

    val count get() = steps.size
}
