# Cretaceous Park — Documento de Diseño de Juego (GDD)

> Gestión de parque de dinosaurios para Android. Mecánicas de *Jurassic World Evolution 1*, estética voxel de *Crossy Road*. Sin historia ni personajes: una isla vacía, un presupuesto y la meta de llegar a cinco estrellas.

Documentos relacionados: [BALANCE.md](BALANCE.md) (tablas de datos y fórmulas) · [TECH.md](TECH.md) (arquitectura sobre esta plantilla GameActivity/C++).

---

## 1. Visión

**Pitch en una frase:** construye un parque de dinosaurios en una isla de bloques, clona especies a partir de fósiles y mantén el equilibrio entre dinero, seguridad y bienestar animal antes de que algo rompa una valla.

**Pilares de diseño**

| Pilar | Qué significa en el juego |
|---|---|
| **Crecer** | Cada minuto de juego debe producir algo nuevo: un edificio, un dinosaurio, una investigación. El parque se ve crecer en pantalla. |
| **Equilibrar** | Beneficios, seguridad y bienestar tiran en direcciones opuestas. Nunca se optimizan los tres a la vez; el jugador elige qué sacrificar. |
| **Contener** | El peligro es real pero legible: un dinosaurio estresado avisa antes de escapar, y una fuga se resuelve con acciones claras, no con pánico. |
| **Legible al instante** | Colores planos y formas de bloque: un vistazo basta para saber qué es cada cosa y en qué estado está. Sin texto pequeño, sin menús profundos. |

**Plataforma y formato**

- Android 11+ (minSdk 30), teléfonos y tablets, orientación **horizontal**.
- Sesiones de 5 a 10 minutos. Una isla completa (0 → 5 estrellas) tarda 45–90 minutos repartidos en varias sesiones.
- Guardado automático continuo. Desbloqueos permanentes entre partidas (meta-progresión con "Ámbar").
- Un solo jugador, sin conexión obligatoria.

**Qué NO es**

- No hay campaña, cinemáticas, personajes ni diálogos.
- No hay control directo de vehículos ni disparos en primera persona. El jugador es "la mano" que gestiona.
- No hay simulación profunda de genoma; la genética son 3 ranuras claras por especie.

---

## 2. Estilo visual

### 2.1 Voxel plano (Crossy Road)

- **Todo son bloques.** Terreno, edificios, dinosaurios, visitantes y vallas se modelan en cubos de una rejilla de 1/8 de tile. Sin texturas: un color plano por cara.
- **Iluminación de tres tonos.** Una luz direccional fija. Cada color base se pinta en tres valores: cara superior (base), caras hacia la luz (base −10 % luminosidad), caras en sombra (base −25 %). Sin sombras proyectadas suaves; cada entidad tiene una **sombra plana redondeada** debajo.
- **Paleta limitada.** 32 colores para el mundo + 8 colores de interfaz. Cada especie usa 2 colores propios (cuerpo + detalle) para reconocerse a distancia.
- **Animación por saltos y aplastamiento.** Los dinosaurios se mueven con pasos cortos que "aplastan y estiran" el modelo (squash & stretch), igual que el pollo de Crossy Road. Los visitantes avanzan de tile en tile con un pequeño salto. Los edificios "rebotan" al colocarse.
- **Sin partículas realistas.** Lluvia, polvo, fuego y chispas de valla son cubos pequeños de colores planos.

### 2.2 Cámara isométrica fija

- Cámara **ortográfica**, inclinación de 35°, girada 45° sobre la rejilla. No rota libremente: hay **4 orientaciones** (0/90/180/270°) que se cambian con un botón; la transición es una rotación animada de 0,3 s.
- Zoom en **4 niveles fijos** (cerca: 11 tiles de ancho; medio: 18; lejos: 30; vista general: 52). Nunca se ve el horizonte: la isla siempre está sobre un mar plano.
- El mundo es una rejilla de **tiles de 1 × 1 unidad**. Todo se coloca alineado a tile. Los dinosaurios ocupan 1 × 1 (pequeños), 2 × 2 (medianos) o 3 × 3 (grandes) tiles.

### 2.3 Escala y lectura

| Elemento | Tamaño en tiles | Altura aprox. (bloques de 1/8) |
|---|---|---|
| Visitante | 1 × 1 (3 por tile) | 8 |
| Dinosaurio pequeño | 1 × 1 | 6–8 |
| Dinosaurio mediano | 2 × 2 | 12–16 |
| Dinosaurio grande | 3 × 3 | 24–32 |
| Camino | 1 × 1 | 1 |
| Valla | borde de tile | 6 (ligera) / 8 (media) / 12 (pesada) |
| Tienda | 2 × 2 | 12 |
| Hotel | 3 × 3 | 28 |
| Centro (investigación, expediciones, incubadora, rangers) | 3 × 3 | 20 |

**Estados visibles sin texto:**

- Dinosaurio feliz: color normal, camina despacio. Estrés medio: **burbuja amarilla** encima. Estrés alto: burbuja **roja parpadeante** y camina hacia la valla.
- Valla dañada: bloques que faltan; valla eléctrica sin energía: pierde el brillo azul.
- Visitante descontento: burbuja gris con icono (hambre, sed, cansado, aburrido).
- Edificio sin energía o sin personal: icono de rayo o llave inglesa encima, en gris.

---

## 3. Controles táctiles

Regla: **cualquier acción del juego se hace con un dedo, en dos toques como máximo desde la vista principal.** El pellizco es opcional (zoom también por botón).

| Gesto | Acción |
|---|---|
| Arrastrar con un dedo (fondo) | Desplazar cámara |
| Pellizco / botón ± | Zoom (3 niveles) |
| Botón de rotar | Rotar cámara 90° |
| Toque en entidad | Seleccionar → panel inferior con información y acciones |
| Toque en botón de construcción → toque en el mapa | Colocar edificio (previsualización verde/roja; se confirma con ✓) |
| Mantener y arrastrar en modo valla / camino | "Pintar" vallas o caminos tile a tile, como dibujar |
| Toque sobre dinosaurio fugado (con Centro de Rangers) | Lanzar dardo tranquilizante (tiempo de recarga) |
| Doble toque en alerta (HUD) | Centrar cámara en el incidente |

- **Zonas de toque de 48 dp mínimo.** Los botones principales viven en la esquina inferior derecha (mano dominante) y la barra de construcción en la inferior izquierda.
- **Sin gestos ocultos.** Todo lo que hace un gesto tiene un botón equivalente.
- **Pausa / 1× / 2×** siempre visibles arriba a la derecha. Al colocar edificios el juego no se pausa (decisión de tensión), pero abrir cualquier pantalla completa (Laboratorio, Investigación, Expediciones) **pausa** el mundo.

---

## 4. Bucle de juego

### 4.0 Tutorial guiado (Isla Brote)

Al empezar una partida nueva en Isla Brote se activa un tutorial de **16 objetivos encadenados**, al estilo de la primera isla de *Jurassic World Evolution 1*: cada objetivo muestra un título y una instrucción de dos líneas; se comprueba solo cada medio segundo y, al cumplirse, pasa al siguiente. **No da recompensas**: solo enseña a jugar. La pestaña de construcción relevante se abre sola al entrar en cada paso. Se puede **saltar** desde la propia tarjeta y **reiniciar** desde el menú de pausa.

Orden de los objetivos: mover la cámara → recinto de 6×6 → comedero → agua → camino hasta el recinto → mirador → generador → Centro de Expediciones → enviar una expedición → laboratorio con energía → primer dinosaurio → grupo mínimo de la especie → tienda → empezar una investigación → Centro de Rangers → primera estrella.

## 4.1 Bucle de minuto (lo que hace el jugador cada 30–90 s)

1. Mira el HUD: dinero, visitantes, alertas.
2. Reacciona a una alerta (dino con hambre, valla dañada, cola en una tienda) **o** invierte (nuevo edificio, expedición, investigación).
3. Ve el resultado en pantalla: dinos que vuelven al verde, visitantes que entran, dinero que sube.

### 4.2 Bucle de sesión (5–10 min)

Una sesión típica termina cuando el jugador completa **un hito visible**: un recinto nuevo lleno, una especie clonada por primera vez, una estrella más. El juego lo refuerza con la pantalla de "Resumen del día" al salir (opcional, un toque para cerrar).

### 4.3 Bucle de partida (una isla, 45–90 min)

```
Isla vacía + presupuesto
   → Recinto básico + 2–3 herbívoros pequeños + camino + tienda
   → Primeros visitantes → primeros ingresos
   → Centro de Expediciones → fósiles → ADN → especies nuevas
   → Centro de Investigación → mejores vallas, tiendas, genética
   → Carnívoros (más atractivo, más riesgo)
   → Hoteles (visitantes pernoctan → más gasto)
   → 5 estrellas → isla completada → recompensa de Ámbar → siguiente isla
```

Fracaso: **quiebra** (saldo negativo durante 60 s reales sin ingresos suficientes) termina la partida. Se conserva el Ámbar ganado.

### 4.4 Bucle meta (entre partidas)

El **Ámbar** se gana por hitos (primera clonación de cada especie, estrellas, islas completadas, retos). Se gasta en desbloqueos **permanentes**: especies disponibles desde el inicio, edificios iniciales, presupuesto inicial mayor, islas nuevas y variantes de color de dinosaurios. Ver §13.

---

## 5. El mundo: islas y terreno

### 5.1 Rejilla

- Cada isla es una rejilla cuadrada de tiles. Fuera de la isla, mar (no construible).
- Tipos de tile: **hierba** (construible), **arena** (construible, los herbívoros no la consideran "espacio de calidad"), **bosque** (no construible hasta talar; cuenta como refugio para dinos), **roca** (no construible; se puede demoler con coste), **agua** (natural o colocada por el jugador; sirve como bebedero, no construible).
- Herramientas de terreno: **Talar** (bosque → hierba), **Cavar agua** (hierba → agua), **Rellenar** (agua → hierba), **Plantar bosque** (hierba → bosque), **Elevar** y **Rebajar** (un bloque de relieve por pincelada). Coste por tile.

### 5.1b Relieve

- Cada tile tiene un **nivel de 0 a 3**. Un nivel mide lo mismo que el hueco del agua (un cuarto de tile de alto), así que las mesetas se leen sin tapar el parque.
- **Desnivel de 1**: dinosaurios y visitantes lo salvan sin más. **Desnivel de 2 o más**: es un cortado; ni las rutas de los visitantes (sobre camino) ni las de los dinos (dentro del recinto) lo cruzan. Un comedero en lo alto de un cortado sin rampa provoca el aviso "no puede llegar al comedero (desnivel)".
- Los edificios exigen **terreno llano** bajo toda su huella ("Terreno desnivelado: nivela primero"). Las vallas y los caminos se colocan a cualquier nivel; el camino solo será transitable donde el desnivel sea de un bloque.
- El agua queda un nivel por debajo de su orilla; no se puede elevar ni rebajar sin rellenarla antes.

### 5.1c Generación procedural

- Un campo de ruido continuo (fBm) reparte los **lagos** en las zonas más bajas y los **niveles de relieve** en el resto por cuantiles, de modo que los porcentajes de agua, bosque y roca de cada isla se respetan.
- Cada isla tiene un **relieve** (1 suave, 2 medio, 3 abrupto) que fija cuánta superficie queda en niveles altos y cuántas mesetas con cortados aparecen. Brote y Espejo son suaves; Ceniza y Corona, abruptas.
- El bosque sigue la humedad (ruido) y se concentra junto al agua y en las cotas bajas; la roca, en las cotas altas y los cortados. Las orillas de los lagos bajan como mucho un nivel por tile y la arena aparece en ellas y en la costa exterior.
- La zona de la entrada queda llana y a nivel 0, con el relieve amortiguado en un radio de diez tiles.

### 5.2 Islas

Las islas son diez veces más extensas que en el primer borrador (lado ×3,2) para que los recintos grandes y varios centros quepan con holgura; el botón ⌂ devuelve la cámara a la entrada.

| Orden | Nombre | Tamaño | Rasgo | Eventos | Desbloqueo |
|---|---|---|---|---|---|
| 1 | Isla Brote | 63 × 63 | Casi toda hierba; tutorial implícito | Solo tormentas suaves | Inicial |
| 2 | Isla Ceniza | 89 × 89 | Mucha roca; espacio caro | + Enfermedad | 3★ en Brote |
| 3 | Isla Tormenta | 101 × 101 | Tormentas frecuentes y fuertes | + Fugas por tormenta | 4★ en Ceniza |
| 4 | Isla Espejo | 114 × 114 | Muchos lagos; construir puentes-camino | + Sabotaje | 4★ en Tormenta |
| 5 | Isla Corona | 139 × 139 | Todo activado, presupuesto inicial bajo | Todos, frecuencia alta | 5★ en Espejo + 400 Ámbar |

Además: **Isla Libre** (sandbox 126 × 126, dinero casi infinito, toda la investigación y todos los yacimientos desbloqueados, sin estrellas; desde el menú de pausa puede desplegar un recinto por especie para verlas todas) se desbloquea al completar Isla Brote.

---

## 6. Construcción

### 6.1 Categorías (barra inferior, 5 pestañas)

1. **Recintos**: vallas (ligera, media, pesada, eléctrica), puerta de recinto, comedero de herbívoros, comedero de carnívoros (cabra en bloque), bebedero, refugio de tormenta para dinos.
2. **Caminos**: camino, camino ancho (2 tiles, más flujo), puente (camino sobre agua), mirador (torre 1 × 1 junto a valla), galería (2 × 1 pegada a valla, techada).
3. **Servicios**: tienda de comida, tienda de bebida, tienda de recuerdos, aseos, hotel pequeño, hotel grande, refugio de emergencia para visitantes.
4. **Centros**: Centro de Expediciones, Laboratorio de ADN + Incubadora (un edificio), Centro de Investigación, Centro de Rangers, Generador, Entrada del parque (única, colocada de inicio en el muelle).
5. **Terreno**: talar, cavar agua, rellenar, plantar, demoler.

Costes, tamaños, mantenimiento y requisitos: ver [BALANCE.md §2](BALANCE.md).

### 6.2 Reglas de colocación

- Todo edificio necesita **camino adyacente** (excepto vallas, comederos, bebederos y refugios de dinos, que van dentro del recinto).
- Los centros y tiendas necesitan **energía**: radio de 10 tiles de un Generador. La valla eléctrica también.
- Un **recinto** es cualquier región de tiles completamente cerrada por vallas (con o sin puerta). Se detecta automáticamente al cerrar el último hueco y se colorea un instante para confirmarlo.
- Los caminos no pueden atravesar recintos. Los miradores y galerías se pegan al lado exterior de una valla.

### 6.3 Vallas

| Valla | Resistencia (PV) | Coste/tile | Mantenimiento | Nota |
|---|---|---|---|---|
| Ligera | 100 | 50 | bajo | Basta para herbívoros pequeños |
| Media | 250 | 120 | medio | Herbívoros medianos, carnívoros pequeños |
| Pesada | 600 | 300 | alto | Grandes de cualquier tipo |
| Eléctrica | 400 + disuasión | 250 | medio + energía | Un dino estresado reduce su ataque 70 % si tiene energía. Sin energía = valla ligera |

Un dinosaurio golpea la valla cuando su estrés llega al máximo. Cada golpe resta `fuerza_ataque` PV. Reparar cuesta el 30 % del precio del tramo. Ver fórmulas en [BALANCE.md §5](BALANCE.md).

---

## 7. Fósiles, ADN y clonación

Cadena de tres pasos, cada uno en su propio edificio y con una pantalla sencilla:

### 7.1 Expediciones (Centro de Expediciones)

- Pantalla: mapa mundi de bloques con **yacimientos** desbloqueados. Cada yacimiento lista 2–4 especies que puede devolver.
- Enviar equipo: coste en dinero, dura **60–120 s reales** (2× si el juego va a 2×). Un solo equipo al principio; investigable el segundo.
- Al volver, devuelve 3–5 **fósiles**: cada uno de una especie del yacimiento, con **calidad** (Baja 5–10 % ADN, Media 10–20 %, Alta 20–35 %) y a veces un **fósil raro** (pieza de meta, vendible o "Ámbar" +5).
- El ADN de cada especie se **acumula**: `ADN% += calidad del fósil`, tope 100 %.

### 7.2 Genoma (Laboratorio)

- Para clonar hace falta **ADN ≥ 50 %**. La **viabilidad** (probabilidad de éxito) sube con el ADN: 50 % → 55 % éxito; 100 % → 98 % éxito.
- **3 ranuras de gen** por especie, cada una con 2–3 opciones desbloqueadas por investigación:
  - **Piel**: color alternativo (solo estético + pequeño bonus de atractivo).
  - **Resistencia**: menos enfermedad / menos estrés por hambre.
  - **Temperamento**: menos agresivo (menos daño a vallas, menos atractivo) o más vistoso (más atractivo, más estrés).
- Cada gen aplicado cuesta dinero y reduce la viabilidad 5 %.

### 7.3 Incubación (Incubadora, parte del Laboratorio)

- Elegir especie + recinto de destino (el recinto debe ser válido: cerrado, tamaño mínimo de la especie, valla con resistencia suficiente; si no, se avisa pero se permite).
- Coste en dinero + **30–90 s reales**. Al final: tirada de viabilidad. Éxito → el dino aparece en la puerta del recinto con un rebote. Fallo → se pierde el dinero, mensaje "Incubación fallida".
- Máximo 2 incubaciones simultáneas (3 con investigación).

---

## 8. Dinosaurios

### 8.1 Especies

Dieciséis especies en el lanzamiento: 8 herbívoros y 8 carnívoros, en tres tamaños. Tabla completa (coste, espacio, grupo, atractivo, peligro, valla requerida) en [BALANCE.md §1](BALANCE.md).

Resumen:

| | Pequeño (1×1) | Mediano (2×2) | Grande (3×3) |
|---|---|---|---|
| **Herbívoros** | Gallimimus, Dryosaurus | Parasaurolophus, Stegosaurus, Ankylosaurus | Triceratops, Brachiosaurus, Diplodocus |
| **Carnívoros** | Compsognathus, Velociraptor | Dilophosaurus, Ceratosaurus, Carnotaurus | Allosaurus, Spinosaurus, Tyrannosaurus |

Reglas de convivencia: herbívoros de distintas especies conviven sin problema. Carnívoros solo con su propia especie. Un carnívoro con herbívoros los caza (herbívoro muere, carnívoro come, pero los herbívoros del recinto suben estrés al máximo).

### 8.2 Necesidades (4 barras, 0–100)

| Necesidad | Cómo se satisface | Cómo se mide |
|---|---|---|
| **Espacio** | Tiles del recinto ≥ `espacio_min` de la especie × nº de dinos (con descuento por grupo) | Se recalcula al cambiar el recinto |
| **Comida** | Comedero del tipo correcto con stock dentro del recinto (herbívoros grandes también comen bosque) | Baja con el tiempo; sube al comer |
| **Agua** | Tile de agua o bebedero en el recinto | Baja con el tiempo; sube al beber |
| **Compañía** | Nº de la misma especie en el recinto dentro de `[grupo_min, grupo_max]` | Instantáneo |

**Bienestar** de un dino = mitad media ponderada (Espacio 30 %, Comida 30 %, Agua 20 %, Compañía 20 %) y mitad la peor de las cuatro necesidades, para que ninguna carencia quede enmascarada.

### 8.3 Estrés y fuga

- **Estrés** (0–100) sube cuando Bienestar < 50 (más rápido cuanto más bajo) y baja cuando Bienestar > 60.
- 40+: burbuja amarilla. 75+: burbuja roja, el dino camina hacia el tramo de valla más débil. **100**: empieza a **golpear la valla**.
- Valla rota → **FUGA**. El dino sale, deambula; los carnívoros persiguen visitantes; los herbívoros grandes pisotean edificios y visitantes en su camino.
- Resolver: con **Centro de Rangers** el jugador toca al dino → dardo (recarga 8 s, 1–3 dardos según tamaño). Dormido 60 s → botón "Transportar" a un recinto válido (coste). Sin Centro de Rangers, la única opción es reconstruir la valla alrededor (caro) o esperar a que el dino muera de hambre (−Bienestar global, −reputación).
- Cada visitante herido: coste de indemnización y −Seguridad. Cada muerte: coste alto, −Seguridad grande, −Reputación.

### 8.4 Ciclo de vida

- Los dinos **no envejecen ni se reproducen** (sesión corta). Sí **enferman** (evento) y **mueren** por hambre/sed prolongadas (Comida o Agua a 0 durante 150 s) o por depredación.
- Un dino muerto genera un cadáver 30 s (los carnívoros del recinto se lo comen; los visitantes lo ven y baja el Bienestar percibido).

---

## 9. Visitantes y economía

### 9.1 Llegada

- Los visitantes llegan en **ferry** cada 20 s a la Entrada. Cuántos llegan depende del **Atractivo** del parque y la **Reputación**: `llegadas = base × Atractivo × Reputación`, con un tope por capacidad de caminos y hoteles. Ver fórmulas en [BALANCE.md §4](BALANCE.md).
- Pagan **entrada** al llegar (precio ajustable por el jugador: bajo/medio/alto; el alto reduce llegadas si Reputación es baja).

### 9.2 Comportamiento

Cada visitante es un agente simple con 4 necesidades (**Hambre, Sed, Descanso, Diversión**) y un presupuesto de gasto. Camina por caminos, va al mirador/galería con mejor "vista" (dinos visibles ponderados por atractivo), y cuando una necesidad baja busca la tienda/aseo/hotel más cercano. Si no la encuentra en 40 s, su **Comodidad** cae; con Comodidad baja gasta menos y al final se va (y resta Reputación).

- **Ver dinosaurios** sube Diversión según el atractivo de la especie y **la distancia** (los grandes se ven desde más lejos; los pequeños necesitan mirador cercano).
- Un visitante con las 4 necesidades altas gasta el 100 % de su presupuesto antes de irse; con Comodidad media, ~50 %; baja, ~20 %.
- **Hoteles**: un visitante alojado se queda un "día" más (otro ciclo completo de gasto). Sin hotel, se va al final del ciclo (~3 min).

### 9.3 Ingresos y gastos

**Ingresos**: entradas + ventas en tiendas + hoteles + venta de fósiles sobrantes.
**Gastos**: mantenimiento por edificio y tramo de valla (por minuto), comida para comederos (por reposición), salarios de centros (por minuto), expediciones, incubaciones, investigación, reparaciones, indemnizaciones.

El **presupuesto inicial** de cada isla y los precios están en [BALANCE.md](BALANCE.md). El objetivo de ritmo: el jugador debe **poder permitirse algo nuevo cada 60–90 s** en los primeros 15 minutos.

### 9.4 Rendimiento

Máximo **150 visitantes simulados** como agentes visibles. Por encima, el parque sigue "recibiendo" visitantes en forma agregada (ingresos calculados con la Comodidad media) pero solo se dibujan 150. Así el juego se mantiene ligero en móviles modestos.

---

## 10. Investigación

Centro de Investigación genera **Puntos de Investigación (PI)** por minuto (más con Generador y con el segundo Centro). Cada nodo cuesta PI + dinero y tarda un tiempo real. Un nodo en curso a la vez (dos con "Doble equipo").

Tres ramas, 6 nodos cada una. Árbol completo con costes en [BALANCE.md §3](BALANCE.md):

- **Bienestar**: comederos automáticos, bebedero grande, valla media → pesada → eléctrica, refugio de tormenta, medicina (curar enfermedad a distancia), transporte rápido.
- **Entretenimiento**: galería, camino ancho, tienda de recuerdos, hotel pequeño → grande, precios dinámicos, publicidad (+Reputación).
- **Genética**: segundo equipo de expedición, yacimientos nuevos (desbloquean especies), 3ª incubadora, genes de Piel, Resistencia y Temperamento, "secuenciación rápida" (+ADN por fósil).

---

## 11. Valoración: las cinco estrellas

La **Valoración del parque** (0–5★, con medias estrellas) se recalcula cada 10 s como media ponderada de cuatro índices (0–100):

| Índice | Peso | Qué mide |
|---|---|---|
| **Beneficios** | 25 % | Ingresos netos por minuto respecto a un objetivo por isla |
| **Seguridad** | 25 % | Sin fugas ni heridos recientes; vallas adecuadas a cada especie; Centro de Rangers; refugios de visitantes |
| **Bienestar animal** | 25 % | Media del Bienestar de todos los dinos; penalización por muertes recientes |
| **Atractivo** | 25 % | Nº de especies, atractivo de las especies, comodidad media de visitantes, variedad de servicios |

Fórmulas y curvas en [BALANCE.md §6](BALANCE.md). Para llegar a **5★** hace falta ≥ 90 en cada índice, no solo en la media: de ahí el equilibrio. Al alcanzar 5★ y mantenerlo 60 s, la isla se marca como completada (se puede seguir jugando).

**Reputación** (0–100) es un valor lento que sigue a la Valoración con inercia: sube si la Valoración es mayor, baja si es menor. Afecta a llegadas y al precio de entrada que los visitantes toleran.

---

## 12. Eventos aleatorios

Un evento cada 3–6 minutos (según isla). Nunca dos a la vez. Aviso previo de 10–20 s en el HUD para que el jugador reaccione.

| Evento | Aviso | Efecto | Contramedidas |
|---|---|---|---|
| **Tormenta** | 20 s (cielo oscurece, viento) | 60–120 s. Daña vallas (−PV aleatorio), puede apagar el Generador, los visitantes corren a refugios; sin refugio, Comodidad cae y heridos si hay fuga | Refugios de visitantes, refugio de dinos (−estrés), valla pesada, reparar rápido |
| **Enfermedad** | 10 s (un dino con burbuja verde) | Un dino enferma: Bienestar −40, contagia a otros del recinto cada 30 s. Sin curar, muere en 3 min | Centro de Rangers: toque en dino → "Curar" (coste). Investigación "Medicina" cura a distancia. Gen Resistencia baja la probabilidad |
| **Fuga** | 15 s (dino sube estrés de golpe) | Un dino aleatorio pasa a estrés 100 e intenta romper la valla | Vallas fuertes, eléctrica, Rangers con dardos |
| **Sabotaje** | 10 s (icono de alerta en un edificio) | Un saboteador apaga el Generador 60 s (vallas eléctricas caen a ligeras), o abre una puerta de recinto, o vacía un comedero | Segundo Generador, Rangers cerca (reduce duración), puertas cerradas por defecto |

Probabilidad ponderada por isla en [BALANCE.md §7](BALANCE.md). Los eventos son la principal fuente de "tensión" en un parque estable: sin ellos, un parque de 5★ sería estático.

---

## 13. Meta-progresión: Ámbar

El **Ámbar** es la moneda permanente. Se gana solo jugando (sin compras):

| Hito | Ámbar |
|---|---|
| Primera clonación de una especie (una vez por especie) | 10 |
| Cada estrella nueva en una isla (una vez por isla y estrella) | 15 |
| Isla completada (5★) | 100 |
| Fósil raro encontrado | 5 |
| Retos de partida (p. ej. "60 s sin alertas con 3 carnívoros") | 10–30 |

**Desbloqueos permanentes** (tienda de Ámbar en el menú principal):

- **Inicio con especie** (30–80 Ámbar por especie): la especie empieza con 50 % ADN en toda isla nueva.
- **Edificio inicial** (40–100): valla media, tienda de recuerdos o Centro de Rangers disponibles desde el minuto 0.
- **Presupuesto extra** (50/100/200): +10 %, +20 %, +30 % de presupuesto inicial.
- **Pieles** (20): variantes de color de dinosaurios (estético).
- **Islas** (ver §5.2).
- **Retos** de isla, elegibles al empezar partida: *Sin carnívoros* (50 Ámbar), *Tormentas constantes* (100), *Presupuesto ajustado* (75). Se superan al alcanzar 4★ con el reto activo; una vez por isla y reto.
- **Pieles** (20 Ámbar por especie): la piel alternativa queda disponible al incubar sin investigar el gen.

Nada del Ámbar hace el juego trivial: la Isla Corona está diseñada para requerir dominio incluso con todos los desbloqueos.

---

## 14. Interfaz

### 14.1 HUD en partida

```
+--------------------------------------------------------------+
| $ 12.450 (+340/min)   Visitantes 87   3,5 estrellas  [||][1x][2x] |  <- barra superior
|                                                              |
|  ! Stegosaurus tiene sed          <- alertas apiladas (max 3)|
|  ! Valla dañada (Recinto 2)                                  |
|                                                              |
|                    [ MUNDO ISOMETRICO ]                      |
|                                                              |
|                                                  [rot][+][-] |  <- camara
| [Recintos][Caminos][Servicios][Centros][Terreno] [ADN][Inv][Exp] |  <- construccion / pantallas
+--------------------------------------------------------------+
```

- Al **seleccionar** algo, un panel desplegable ocupa el tercio inferior: nombre, barras de estado, 1–3 botones de acción grandes (Reparar, Reponer, Vender, Curar, Dardo…).
- Las **alertas** son toques directos: centran la cámara en el problema y abren su panel.
- Los iconos ADN (Laboratorio), Inv (Investigación) y Exp (Expediciones) abren pantallas completas que pausan el juego.

### 14.2 Pantallas

1. **Menú principal**: Continuar, Islas, Tienda de Ámbar, Ajustes. Fondo: la última isla jugada rotando despacio.
2. **Selección de isla**: tarjetas con estrellas obtenidas, requisitos, retos.
3. **Partida** (HUD anterior).
4. **Expediciones**: mapa de bloques con yacimientos; fósiles obtenidos como fila de tarjetas.
5. **Laboratorio**: lista de especies con barra de ADN, botón "Incubar" y 3 ranuras de gen.
6. **Investigación**: tres columnas verticales (una por rama), nodos como bloques que se iluminan.
7. **Resumen** (al pausar/salir): ingresos, gastos, estrellas, Ámbar ganado.
8. **Ajustes**: sonido, música, vibración, calidad (sombras planas on/off, visitantes máximos), idioma (ES/EN), borrar datos.

### 14.3 Texto e idiomas

Español e inglés de salida. Todo el texto en `strings.xml`. Sin texto por debajo de 14 sp. Los números se abrevian (12,4k) a partir de 5 cifras.

---

## 15. Audio

> Implementación actual: todo el audio se **sintetiza en tiempo real** (sin archivos): efectos PCM generados al arrancar y dos temas generativos (pentatónico en calma a 84 bpm, menor con pulso a 126 bpm) mezclados con fundido según haya fugas, tormenta o alertas rojas. Rugidos por tamaño de especie al incubar, escapar o morir; golpes a la valla, dardo, colocación, demolición, error, campanilla de logro y trueno. Tres interruptores en el panel Parque: música, sonidos, vibración.


- Música: dos temas de loop suave (día tranquilo, alerta) con crossfade según haya alertas activas. Estilo: marimba/sintetizador ligero, tono lúdico.
- Efectos: cada especie tiene un rugido corto y "cuadrado". Sonidos de colocación (pop), dinero (tintineo), valla eléctrica (zumbido), tormenta (viento en cubos).
- Vibración corta al colocar y al recibir alerta roja. Todo desactivable.

---

## 16. Guardado

- **Autoguardado** cada 30 s y al pasar a segundo plano. Una ranura por isla + estado meta (Ámbar, desbloqueos) en un archivo aparte.
- Al volver a la app, el juego aparece **pausado** en el punto exacto. No hay simulación offline (evita la ansiedad de "qué habrá pasado").
- Formato binario versionado; ver [TECH.md §6](TECH.md).

---

## 17. Alcance y plan

### MVP jugable (vertical slice, Isla Brote)

- Rejilla, cámara, controles, colocación de caminos/vallas/2 tiendas. Tutorial guiado de 16 objetivos en Isla Brote.
- 4 especies (Gallimimus, Stegosaurus, Velociraptor, Tyrannosaurus), necesidades, estrés, fuga y dardo.
- Visitantes básicos, entrada, tiendas, dinero.
- Expediciones e incubación sin genes. Investigación de 6 nodos. Estrellas. Evento Tormenta. Guardado.

### Alfa

- 16 especies, 5 islas, árbol completo, 4 eventos, genes, hoteles, Ámbar y tienda.

### Beta / lanzamiento

- Balance, rendimiento en gama baja, audio final, localización EN, accesibilidad (daltonismo: patrones en las burbujas de estado), retos de isla.

Detalles de implementación y estructura del código en [TECH.md](TECH.md).
