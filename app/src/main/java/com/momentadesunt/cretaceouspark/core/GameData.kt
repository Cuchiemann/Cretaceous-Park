package com.momentadesunt.cretaceouspark.core

/** Tablas de datos. Fuente de diseño: docs/BALANCE.md y los JSON de assets/data. */
object GameData {

    private fun c(hex: String): Int = hex.removePrefix("#").toLong(16).toInt() or 0xFF000000.toInt()

    val species: List<SpeciesDef> = listOf(
        SpeciesDef("gallimimus", "Gallimimus", Diet.HERBIVORE, Size.S, 8000, 4, 3, 10, 2, 1, 5, Fence.LIGHT, 1, "canon_rojo", c("C9A227"), c("7A5C1E")),
        SpeciesDef("dryosaurus", "Dryosaurus", Diet.HERBIVORE, Size.S, 7000, 4, 2, 8, 2, 1, 5, Fence.LIGHT, 1, "canon_rojo", c("6FA84F"), c("3E6B2E")),
        SpeciesDef("parasaurolophus", "Parasaurolophus", Diet.HERBIVORE, Size.M, 18000, 9, 2, 8, 4, 2, 20, Fence.MEDIUM, 2, "canon_rojo", c("D9773B"), c("8C4A21")),
        SpeciesDef("stegosaurus", "Stegosaurus", Diet.HERBIVORE, Size.M, 22000, 10, 1, 5, 5, 3, 35, Fence.MEDIUM, 2, "estepa_gris", c("5B8C5A"), c("C0392B")),
        SpeciesDef("ankylosaurus", "Ankylosaurus", Diet.HERBIVORE, Size.M, 26000, 10, 1, 4, 5, 3, 45, Fence.MEDIUM, 2, "costa_de_sal", c("8B7355"), c("4A3B2A")),
        SpeciesDef("triceratops", "Triceratops", Diet.HERBIVORE, Size.L, 40000, 16, 1, 5, 6, 4, 70, Fence.HEAVY, 3, "costa_de_sal", c("7D8B6A"), c("E8E1C9")),
        SpeciesDef("brachiosaurus", "Brachiosaurus", Diet.HERBIVORE, Size.L, 60000, 25, 1, 4, 8, 2, 60, Fence.HEAVY, 3, "bosque_petrificado", c("6B8E9F"), c("3F5B68")),
        SpeciesDef("diplodocus", "Diplodocus", Diet.HERBIVORE, Size.L, 55000, 24, 1, 4, 7, 2, 55, Fence.HEAVY, 3, "bosque_petrificado", c("9A8C6B"), c("5E533F")),
        SpeciesDef("compsognathus", "Compsognathus", Diet.CARNIVORE, Size.S, 9000, 3, 4, 12, 3, 1, 8, Fence.LIGHT, 1, "canon_rojo", c("4E9E8F"), c("2C5E55")),
        SpeciesDef("velociraptor", "Velociraptor", Diet.CARNIVORE, Size.S, 20000, 6, 2, 6, 6, 6, 25, Fence.MEDIUM, 1, "estepa_gris", c("B5651D"), c("2E2E2E")),
        SpeciesDef("dilophosaurus", "Dilophosaurus", Diet.CARNIVORE, Size.M, 24000, 8, 2, 5, 5, 5, 30, Fence.MEDIUM, 2, "estepa_gris", c("7B9E3C"), c("E1B12C")),
        SpeciesDef("ceratosaurus", "Ceratosaurus", Diet.CARNIVORE, Size.M, 32000, 12, 1, 2, 6, 6, 45, Fence.MEDIUM, 2, "costa_de_sal", c("A63D40"), c("4B1D1F")),
        SpeciesDef("carnotaurus", "Carnotaurus", Diet.CARNIVORE, Size.M, 36000, 12, 1, 2, 6, 7, 50, Fence.HEAVY, 2, "bosque_petrificado", c("8E3B46"), c("D4A15A")),
        SpeciesDef("allosaurus", "Allosaurus", Diet.CARNIVORE, Size.L, 55000, 20, 1, 2, 8, 8, 80, Fence.HEAVY, 3, "desierto_blanco", c("B08D57"), c("5A3E1B")),
        SpeciesDef("spinosaurus", "Spinosaurus", Diet.CARNIVORE, Size.L, 75000, 24, 1, 1, 9, 9, 90, Fence.HEAVY, 3, "desierto_blanco", c("3E7C8F"), c("E0762E"), requiresWaterTiles = 4),
        SpeciesDef("tyrannosaurus", "Tyrannosaurus", Diet.CARNIVORE, Size.L, 100000, 24, 1, 1, 10, 10, 120, Fence.HEAVY, 3, "glaciar_norte", c("6B5B4E"), c("2B2320"))
    )
    val speciesById: Map<String, SpeciesDef> = species.associateBy { it.id }
    fun species(id: String): SpeciesDef = speciesById.getValue(id)

    val sites: List<SiteDef> = listOf(
        SiteDef("canon_rojo", "Cañón Rojo", 3000, 60f, 4, "start", listOf("gallimimus", "dryosaurus", "parasaurolophus", "compsognathus")),
        SiteDef("estepa_gris", "Estepa Gris", 5000, 75f, 4, "research:G1", listOf("stegosaurus", "velociraptor", "dilophosaurus")),
        SiteDef("costa_de_sal", "Costa de Sal", 7000, 90f, 4, "research:G4", listOf("ankylosaurus", "triceratops", "ceratosaurus")),
        SiteDef("bosque_petrificado", "Bosque Petrificado", 9000, 100f, 3, "island:ceniza", listOf("brachiosaurus", "diplodocus", "carnotaurus")),
        SiteDef("desierto_blanco", "Desierto Blanco", 11000, 110f, 3, "island:tormenta", listOf("allosaurus", "spinosaurus")),
        SiteDef("glaciar_norte", "Glaciar Norte", 12000, 120f, 3, "island:espejo", listOf("tyrannosaurus"))
    )
    val siteById = sites.associateBy { it.id }

    // ---- Edificios -------------------------------------------------------------------------------------------
    val buildings: List<BuildingDef> = listOf(
        // Recintos
        BuildingDef("feeder_herb", "Comedero herbívoros", Category.ENCLOSURE, 1, 1, 800, 0f, insideEnclosure = true,
            feederDiet = Diet.HERBIVORE, stock = 50, refillCost = 200, color = c("A9743A"), height = 0.5f, desc = "50 raciones. Reponer 200 $."),
        BuildingDef("feeder_carn", "Comedero carnívoros", Category.ENCLOSURE, 1, 1, 1500, 0f, insideEnclosure = true,
            feederDiet = Diet.CARNIVORE, stock = 20, refillCost = 400, color = c("B23A3A"), height = 0.5f, desc = "20 raciones. Reponer 400 $."),
        BuildingDef("water_trough", "Bebedero", Category.ENCLOSURE, 1, 1, 500, 2f, insideEnclosure = true, isWater = true,
            color = c("4FA3D9"), height = 0.35f, desc = "Agua para todo el recinto."),
        BuildingDef("dino_shelter", "Refugio de dinos", Category.ENCLOSURE, 2, 2, 2000, 5f, research = "B4", insideEnclosure = true,
            shelterDinos = true, color = c("7F6A4F"), height = 1.2f, desc = "Menos estrés en tormenta."),
        // Caminos y vistas
        BuildingDef("viewpoint", "Mirador", Category.PATHS, 1, 1, 600, 5f, needsPath = true, attachToFence = true,
            viewRange = 6, visitorCapacity = 6, color = c("D8CBA4"), height = 1.8f, desc = "Los visitantes ven dinos a 6 tiles."),
        BuildingDef("gallery", "Galería", Category.PATHS, 2, 1, 1500, 10f, research = "E1", needsPath = true, attachToFence = true,
            viewRange = 8, visitorCapacity = 12, isShelter = true, shelterCapacity = 12, color = c("E3D9B8"), height = 1.4f, desc = "Vista a 8 tiles y techo."),
        // Servicios
        BuildingDef("shop_food", "Tienda de comida", Category.SERVICES, 2, 2, 3000, 40f, needsPath = true, needsPower = true,
            serveSeconds = 2f, price = 15, restoreNeed = Need.HUNGER, restoreAmount = 60, color = c("E0762E"), height = 1.4f, desc = "15 $ por comida."),
        BuildingDef("shop_drink", "Tienda de bebida", Category.SERVICES, 2, 2, 2500, 30f, needsPath = true, needsPower = true,
            serveSeconds = 1.5f, price = 8, restoreNeed = Need.THIRST, restoreAmount = 60, color = c("3FA7C9"), height = 1.4f, desc = "8 $ por bebida."),
        BuildingDef("shop_gift", "Tienda de recuerdos", Category.SERVICES, 2, 2, 5000, 50f, research = "E3", needsPath = true, needsPower = true,
            serveSeconds = 3f, price = 40, restoreNeed = Need.FUN, restoreAmount = 20, color = c("C75BA0"), height = 1.4f, desc = "40 $ por recuerdo."),
        BuildingDef("toilets", "Aseos", Category.SERVICES, 1, 1, 1000, 10f, needsPath = true,
            serveSeconds = 4f, price = 0, restoreNeed = Need.REST, restoreAmount = 30, color = c("8FB3C9"), height = 1.1f, desc = "Gratis. Descanso +30."),
        BuildingDef("hotel_small", "Hotel pequeño", Category.SERVICES, 3, 3, 12000, 120f, research = "E4", needsPath = true, needsPower = true,
            beds = 20, price = 60, restoreNeed = Need.REST, restoreAmount = 100, color = c("E8E1C9"), height = 2.6f, desc = "20 camas, 60 $/noche."),
        BuildingDef("hotel_large", "Hotel grande", Category.SERVICES, 3, 3, 30000, 250f, research = "E5", needsPath = true, needsPower = true,
            beds = 60, price = 80, restoreNeed = Need.REST, restoreAmount = 100, color = c("F1E9D2"), height = 3.4f, desc = "60 camas, 80 $/noche."),
        BuildingDef("visitor_shelter", "Refugio de visitantes", Category.SERVICES, 2, 2, 4000, 20f, needsPath = true,
            isShelter = true, shelterCapacity = 40, color = c("6E7F8C"), height = 1.0f, desc = "40 personas en tormenta."),
        // Centros
        BuildingDef("generator", "Generador", Category.CENTERS, 2, 2, 8000, 100f, needsPath = true, powerRadius = 10,
            color = c("F2C14E"), height = 1.6f, desc = "Energía en 10 tiles."),
        BuildingDef("expedition_hq", "Centro de Expediciones", Category.CENTERS, 3, 3, 15000, 150f, needsPath = true,
            color = c("C9B27A"), height = 1.8f, desc = "Envía equipos a buscar fósiles."),
        BuildingDef("lab", "Laboratorio e Incubadora", Category.CENTERS, 3, 3, 25000, 250f, needsPath = true, needsPower = true,
            color = c("DDE8EE"), height = 2.0f, desc = "Clona dinosaurios con ADN ≥ 50 %."),
        BuildingDef("research_center", "Centro de Investigación", Category.CENTERS, 3, 3, 20000, 200f, needsPath = true,
            researchPerMin = 10f, maxCount = 2, color = c("9CC5E8"), height = 1.9f, desc = "10 PI/min (15 con energía)."),
        BuildingDef("ranger_station", "Centro de Rangers", Category.CENTERS, 3, 3, 18000, 200f, needsPath = true, actionRadius = 15,
            color = c("5F8F4E"), height = 1.7f, desc = "Dardos, curas y transporte."),
        BuildingDef("entrance", "Entrada del parque", Category.CENTERS, 3, 2, 0, 50f, color = c("B9AE95"), height = 1.6f, desc = "Muelle del ferry.")
    )
    val buildingById = buildings.associateBy { it.id }
    fun building(id: String): BuildingDef = buildingById.getValue(id)

    // ---- Investigación ----------------------------------------------------------------------------------------
    val research: List<ResearchDef> = listOf(
        ResearchDef("B1", "Valla media y comederos automáticos", Branch.WELFARE, 50, 2000, 45f, listOf(), "Desbloquea la valla media. Los comederos se reponen solos."),
        ResearchDef("B2", "Bebedero grande", Branch.WELFARE, 80, 3000, 60f, listOf("B1"), "El bebedero cuenta como 4 tiles de agua."),
        ResearchDef("B3", "Valla pesada", Branch.WELFARE, 150, 6000, 90f, listOf("B1"), "Desbloquea la valla pesada (600 PV)."),
        ResearchDef("B4", "Refugio de tormenta", Branch.WELFARE, 120, 4000, 60f, listOf("B2"), "Desbloquea el refugio de dinos."),
        ResearchDef("B5", "Medicina", Branch.WELFARE, 200, 8000, 120f, listOf("B4"), "Curar sin límite de distancia. Enfermedad −50 %."),
        ResearchDef("B6", "Valla eléctrica", Branch.WELFARE, 300, 12000, 150f, listOf("B3"), "Desbloquea la valla eléctrica."),
        ResearchDef("E1", "Galería", Branch.FUN, 60, 2500, 45f, listOf(), "Desbloquea la galería techada."),
        ResearchDef("E2", "Camino ancho", Branch.FUN, 80, 2000, 45f, listOf("E1"), "Los caminos admiten más visitantes."),
        ResearchDef("E3", "Tienda de recuerdos", Branch.FUN, 120, 5000, 60f, listOf("E1"), "Desbloquea la tienda de recuerdos."),
        ResearchDef("E4", "Hotel pequeño", Branch.FUN, 180, 8000, 90f, listOf("E2", "E3"), "Desbloquea el hotel pequeño."),
        ResearchDef("E5", "Hotel grande", Branch.FUN, 280, 15000, 120f, listOf("E4"), "Desbloquea el hotel grande."),
        ResearchDef("E6", "Publicidad", Branch.FUN, 250, 10000, 90f, listOf("E4"), "Reputación +10 y más llegadas."),
        ResearchDef("G1", "Yacimiento Estepa Gris", Branch.GENETICS, 60, 3000, 45f, listOf(), "Stegosaurus, Velociraptor, Dilophosaurus."),
        ResearchDef("G2", "Segundo equipo", Branch.GENETICS, 120, 6000, 60f, listOf("G1"), "Dos expediciones a la vez."),
        ResearchDef("G3", "Gen de Piel", Branch.GENETICS, 100, 5000, 60f, listOf("G1"), "Color alternativo, +1 atractivo."),
        ResearchDef("G4", "Yacimiento Costa de Sal", Branch.GENETICS, 180, 8000, 90f, listOf("G2"), "Ankylosaurus, Triceratops, Ceratosaurus."),
        ResearchDef("G5", "Genes de Resistencia y Temperamento", Branch.GENETICS, 220, 10000, 120f, listOf("G3", "G4"), "Resistencia: menos enfermedad. Dócil/Vistoso."),
        ResearchDef("G6", "Secuenciación rápida", Branch.GENETICS, 300, 15000, 150f, listOf("G5"), "+5 % ADN por fósil y tercera incubadora.")
    )
    val researchById = research.associateBy { it.id }

    // ---- Islas ------------------------------------------------------------------------------------------------
    val islands: List<IslandDef> = listOf(
        IslandDef("brote", "Isla Brote", 63, 150000, 400f, 15, 3, 5, 7, 300f, 420f, 100, 0, 0, 0, 1, 0f, "Casi toda hierba. Aprende las bases."),
        IslandDef("ceniza", "Isla Ceniza", 89, 200000, 800f, 10, 30, 5, 10, 240f, 360f, 50, 50, 0, 0, 2, 3f, "Mucha roca: el espacio es caro. Aparece la enfermedad."),
        IslandDef("tormenta", "Isla Tormenta", 101, 220000, 1200f, 20, 5, 10, 10, 200f, 300f, 50, 25, 25, 0, 3, 4f, "Tormentas fuertes y fugas."),
        IslandDef("espejo", "Isla Espejo", 114, 250000, 1800f, 15, 5, 30, 5, 180f, 300f, 30, 25, 20, 25, 2, 4f, "Muchos lagos y sabotajes."),
        IslandDef("corona", "Isla Corona", 139, 180000, 2500f, 20, 10, 15, 5, 150f, 240f, 30, 25, 20, 25, 3, 5f, "Todo activado y poco presupuesto."),
        IslandDef("libre", "Isla Libre", 126, 5000000, 1f, 15, 5, 10, 8, 300f, 480f, 40, 20, 20, 20, 2, 0f, "Sandbox: dinero casi infinito, sin estrellas.", sandbox = true)
    )
    val islandById = islands.associateBy { it.id }

    // ---- Constantes de simulación ------------------------------------------------------------------------------
    const val MAX_VISITOR_AGENTS = 150
    const val FERRY_INTERVAL = 20f
    val ENTRY_PRICES = intArrayOf(20, 40, 70)
    const val VISITOR_BUDGET = 120f
    const val VISIT_SECONDS = 180f
    const val REPAIR_FRACTION = 0.3f
    const val DEMOLISH_REFUND = 0.5f
    const val INJURY_COST = 2000
    const val DEATH_COST = 10000
    const val AUTOSAVE_SECONDS = 30f
}
