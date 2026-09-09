# Cretaceous Park — Diseño técnico

Cómo construir el juego descrito en el [GDD](GDD.md) sobre este proyecto: plantilla de Android Studio **Game Activity (C++)** con `GameActivity 1.2.2`, OpenGL ES 3 y CMake.

---

> **Nota de implementación (7 de septiembre de 2026).** El prototipo jugable v0.1 se construyó **100 % en Kotlin**: el mundo isométrico se dibuja con `Canvas` en una vista propia (`render/IsoRenderer.kt`, `render/GameView.kt`) como cajas de color plano con tres tonos, y toda la interfaz es Jetpack Compose (`ui/`). La capa C++/OpenGL ES descrita más abajo se descartó para el MVP: sin puente JNI el ciclo de iteración es mucho más corto y el rendimiento de Canvas sobra para islas de 20 a 44 tiles con menos de 200 agentes. La simulación (`core/`) sigue el diseño de las secciones 3 y 4 (tick fijo de 0,1 s, rejilla con bordes para vallas, flood-fill de recintos, agentes, comandos con validación) y el guardado es JSON con kotlinx.serialization en lugar de binario. Si en el futuro hiciera falta más rendimiento gráfico, la ruta GLES sigue siendo válida y `core/` no cambia.

## 1. Decisión de arquitectura

**Mundo en C++ y OpenGL ES 3; interfaz en Kotlin con Jetpack Compose superpuesta al `SurfaceView`.**

| Capa | Tecnología | Por qué |
|---|---|---|
| Simulación (rejilla, dinos, visitantes, economía, eventos, guardado) | C++20, sin dependencias de Android | Determinista, testeable en el PC, rápida con cientos de agentes |
| Render voxel isométrico | OpenGL ES 3.0 desde C++ (lo que ya trae la plantilla) | Instancing y VBOs estáticos: miles de bloques con pocas llamadas |
| Entrada sobre el mundo (arrastrar, pellizco, toque) | Búfer de entrada de GameActivity → reconocedor de gestos en C++ | Latencia mínima, sin cruzar JNI por evento |
| HUD, paneles y pantallas completas | Kotlin + Jetpack Compose sobre la `GameActivity` | Texto, localización, accesibilidad y layouts responsive salen gratis; en C++ costarían semanas |
| Meta-progresión (Ámbar, desbloqueos, ajustes) | Kotlin + DataStore | Datos pequeños que la UI lee directamente |

Descartado: hacer toda la UI en C++ (ImGui o propia). Un juego de gestión es 60 % interfaz; el texto legible, los dos idiomas y los tamaños de pantalla variados pesan más que la pureza de una sola capa. Descartado también cambiar a un motor externo: el proyecto ya está creado sobre esta plantilla y el alcance visual (bloques planos, cámara fija) no necesita más.

**Regla de oro:** el mundo 3D **nunca dibuja texto**. Toda palabra que ve el jugador está en `strings.xml` y la pinta Compose. Las burbujas de estado sobre dinos y visitantes son sprites voxel (iconos), no texto.

---

## 2. Estructura del proyecto

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt
│   ├── main.cpp                  ← android_main: bucle, eventos de GameActivity
│   ├── platform/                 ← todo lo que toca Android
│   │   ├── AndroidOut.*          (plantilla) log a logcat
│   │   ├── Assets.*              lectura de assets/ (JSON, .vmesh)
│   │   ├── Input.*               gestos: tap, drag, pinch, long-press
│   │   └── Bridge.*              JNI: cola de comandos UI→sim y snapshot sim→UI
│   ├── core/                     ← simulación pura (sin includes de Android/GL)
│   │   ├── Grid.*                tiles, bordes (vallas), regiones (recintos)
│   │   ├── Species.*  Data.*     carga de species.json, buildings.json, research.json, islands.json
│   │   ├── Dino.*  Visitor.*     agentes (SoA)
│   │   ├── Economy.*  Research.* Expeditions.*  Events.*  Rating.*
│   │   ├── World.*               estado completo + tick()
│   │   ├── Commands.*            comandos del jugador (Place, Demolish, Incubate…) con validación
│   │   ├── Save.*                serialización binaria versionada
│   │   └── Rng.*                 PCG32 con semilla por partida
│   └── render/
│       ├── Renderer.*  Shader.*  (plantilla, se reescriben)
│       ├── Camera.*              ortográfica isométrica, 4 giros, 3 zooms
│       ├── VoxelMesh.*           carga .vmesh, VBO/VAO
│       ├── InstanceBatch.*       dibujo instanciado por modelo
│       ├── Terrain.*             malla por chunk de 8×8 tiles
│       ├── Picking.*             toque → tile / entidad
│       └── Effects.*             cubos-partícula, sombras planas, burbujas
├── java/com/momentadesunt/cretaceouspark/
│   ├── MainActivity.kt           GameActivity + ComposeView superpuesta
│   ├── bridge/NativeBridge.kt    external fun … ; StateFlow<GameSnapshot>
│   ├── ui/                       Compose: Hud, BuildBar, SelectionPanel, LabScreen, ResearchScreen,
│   │                             ExpeditionScreen, IslandSelect, AmberShop, Settings, Summary
│   └── meta/MetaStore.kt         DataStore: Ámbar, desbloqueos, ajustes
├── assets/
│   ├── data/                     species.json, buildings.json, research.json, islands.json, events.json
│   ├── models/                   *.vmesh (generados desde tools/vox/*.vox)
│   └── audio/
└── res/values{,-en}/strings.xml
tools/
└── vox2mesh.py                   MagicaVoxel .vox → .vmesh (greedy meshing)
```

`core/` se compila **también como biblioteca de PC** (opción CMake `CP_HOST_BUILD`) para pruebas unitarias y simulación sin cabeza (§8).

---

## 3. Bucle principal y tiempos

```
android_main
  └── while (!destroyRequested)
        procesar eventos GameActivity (ventana, foco, entrada)
        Input::poll()  → gestos → Camera / Commands
        Bridge::drainCommands()  → Commands::apply(world)         (desde Compose)
        acumulador += dtReal × velocidad (0, 1, 2)
        while (acumulador ≥ 0,1 s) { world.tick(0,1); acumulador −= 0,1 }   ← 10 Hz fijo
        if (hanPasado 100 ms) Bridge::publishSnapshot(world)      (hacia Compose)
        Renderer::draw(world, camera, interpolación = acumulador / 0,1)
```

- **Tick fijo de 100 ms** independiente del framerate. Velocidad 2× = dos ticks por 100 ms reales. Pausa = 0.
- El render interpola posiciones entre el tick anterior y el actual para que los saltos de los agentes sean suaves a 60 fps.
- Las pantallas completas (Laboratorio, Investigación, Expediciones) ponen velocidad 0 desde Compose.
- Al pasar a segundo plano (`onPause`): guardar, velocidad 0, liberar contexto EGL (la plantilla ya lo hace).

---

## 4. Simulación (`core/`)

### 4.1 Rejilla

```cpp
struct Tile   { uint8_t terrain; uint16_t building; uint16_t region; uint8_t flags; };
struct Edge   { uint8_t fenceType; uint16_t hp; uint8_t flags /*puerta, abierta, energía*/; };
class Grid {
  int size;                         // 20..44
  std::vector<Tile> tiles;          // size*size
  std::vector<Edge> hEdges, vEdges; // (size+1)*size cada uno: vallas viven en bordes, no en tiles
};
```

- **Recintos**: al colocar o romper una valla, flood-fill desde los tiles afectados sin cruzar bordes con valla intacta. Cada región conexa que **no toca el borde del mapa** es un recinto; se le asigna `region` y se recalculan sus tiles, agua, bosque y dinos. Coste O(tiles) solo en cambios de valla; el resto del tiempo es una consulta.
- **Energía**: mapa de distancias desde generadores activos (BFS hasta radio 10), recalculado al colocar/quitar generador o en evento.
- **Grafo de caminos**: los tiles de camino forman el grafo de los visitantes; los servicios se registran en los tiles de camino adyacentes. A* con heurística Manhattan; caché de rutas por (origen, destino) invalidada al cambiar caminos.

### 4.2 Agentes

Estructura de arrays (SoA) para dinos (≤ 60) y visitantes (≤ 150):

```cpp
struct Dinos {
  std::vector<uint16_t> species, region; std::vector<Vec2> pos, prevPos, target;
  std::vector<float> food, water, stress, hp; std::vector<uint8_t> state, genes, sick, darts;
};
```

- **Dino**: máquina de estados `Idle → Wander → SeekFood → SeekWater → Attack(edge) → Escaped → Asleep → Dead`. Movimiento por tiles dentro de la región con BFS corto hacia el objetivo (comedero, agua, tramo de valla más débil). Necesidades y estrés según [BALANCE.md §5](BALANCE.md).
- **Visitante**: `Arrive → Wander → Goto(servicio | mirador) → Use → Flee → Leave`. Elige mirador por puntuación (atractivo visible / distancia). Compra en el tile de servicio: cola simple con capacidad y tiempo por cliente.
- Por encima del cupo de visitantes, un contador agregado con la Comodidad media aplica ingresos sin agentes.

### 4.3 Comandos y validación

Todo lo que hace el jugador es un `Command` con `validate(world) → Result` y `apply(world)`. La UI llama primero a `validate` para pintar la previsualización verde/roja y mostrar el motivo; el mismo código impide estados inválidos. Ejemplos: `PlaceBuilding`, `PaintFence`, `PaintPath`, `Demolish`, `Terraform`, `SendExpedition`, `Incubate`, `Research`, `Dart`, `Cure`, `Transport`, `Repair`, `SetEntryPrice`, `SetSpeed`.

### 4.4 Determinismo

Un único `Rng` (PCG32) sembrado al crear la partida; los eventos y la calidad de los fósiles salen de él. Con la misma semilla y los mismos comandos con sus ticks, la partida se reproduce: sirve para depurar y para las pruebas de balance.

---

## 5. Render (`render/`)

### 5.1 Cámara

Proyección ortográfica. Vista: rotación 45° + k·90° alrededor del eje Y, inclinación 35° hacia abajo. Tres semianchos fijos (6, 10, 16 unidades) para los tres zooms. El giro de 90° y el cambio de zoom se animan 0,3 s con ease-out. El desplazamiento se limita al rectángulo de la isla más 4 tiles.

### 5.2 Modelos voxel

- Se modelan en **MagicaVoxel** (`tools/vox/*.vox`), rejilla de 8 voxels por tile.
- `tools/vox2mesh.py` aplica **greedy meshing** y escribe `.vmesh`: cabecera + vértices `{pos: 3×int8, normalId: uint8, colorIdx: uint8}` + índices `uint16`. La paleta de 32 colores es global (`assets/data/palette.json`) para que las variantes de piel sean solo un cambio de índice.
- El sombreado de tres tonos se hace en el **vertex shader** por `normalId` (arriba 1,0 · lado iluminado 0,9 · lado en sombra 0,75). Sin texturas, sin luz por píxel.
- **Instancing** (`glDrawElementsInstanced`): un buffer por modelo con `{mat4 modelo, vec4 tinte, float squash}`; todos los Gallimimus del parque son una llamada. Presupuesto: < 200 llamadas de dibujo y < 400 k triángulos por fotograma en gama media.
- **Animación**: sin esqueleto. Los agentes se mueven por interpolación de posición + `squash` en el shader (escala Y ↓ y XZ ↑ al aterrizar). Los edificios rebotan al colocarse con la misma variable.

### 5.3 Terreno y vallas

> Estado actual (Kotlin/Canvas): el relieve vive en `GameState.height` (nivel 0..3 por tile, `Terrain.STEP` = 0,25 z, el mismo alto que el hueco del agua). `IsoRenderer.drawGroundAndObjects` pinta el suelo por **diagonales de vista** (atrás → delante) intercalado con las cajas de los objetos de esa diagonal, porque un tile alto tapa lo que hay detrás y lo de delante debe ir encima; dentro de una diagonal ni cimas ni caras se solapan y se agrupan por color. Cada tile pinta su cima y, hacia cada vecino frontal más bajo, la cara que baja hasta él (tramo de orilla en tono agua si el vecino es agua; al mar, hasta `Terrain.SEA_Z`). El toque usa `IsoCamera.pickWorld`, que prueba cada cota de arriba abajo. La regla de movimiento (`GameState.stepOk`, desnivel ≤ 1) está en `Grid.findPath`/`reachableFrom`, que usan tanto los visitantes (sobre camino) como los dinos (`Dino.path` dentro de su región).

- Terreno en **chunks de 8×8 tiles**, cada uno una malla que se regenera al editar cualquiera de sus tiles (caras superiores + laterales solo hacia el mar). Agua: quad plano animado en el shader.
- Vallas: modelo por tipo y orientación, instanciado por borde. Tramo dañado → variante "rota" a partir del 50 % PV; eléctrica con energía → tinte emisivo azul.

### 5.4 Efectos y lectura de estado

- Sombras planas: quad oscuro con alpha bajo bajo cada agente.
- Burbujas de estado: quad con icono voxel (colores según estrés/necesidad) siempre orientado a cámara, a altura del modelo.
- Partículas: cubos pequeños con vida corta (lluvia, chispas, polvo de colocación), un solo buffer dinámico.
- Previsualización de colocación: el modelo con tinte verde/rojo semitransparente, dibujado en último lugar.

### 5.5 Picking

Toque → rayo ortográfico → intersección con el plano del suelo → tile. Para entidades, se prueba contra las cajas envolventes de dinos y visitantes ordenadas por profundidad (pocas decenas, no hace falta buffer de IDs). Los edificios se resuelven por el tile.

---

## 6. Guardado

- Archivo binario **versionado por trozos**: cabecera `{"CPKS", versión, semilla, tick}` y luego secciones `{id, tamaño, datos}` (grid, edges, dinos, visitors, economy, research, expeditions, events). Un lector antiguo ignora secciones desconocidas; un lector nuevo rellena valores por defecto para secciones ausentes.
- Escritura a `save_<isla>.tmp` y `rename()` atómico. Autoguardado cada 30 s de juego y en `onPause`.
- Ruta: `filesDir` pasada desde Kotlin en el arranque (`NativeBridge.init(filesDir.absolutePath)`).
- Meta (Ámbar, desbloqueos, ajustes) en **DataStore Preferences** en Kotlin; la UI la lee sin cruzar JNI y la sim recibe solo lo que necesita al crear la partida (presupuesto extra, especies con ADN inicial, edificios desbloqueados).

---

## 7. Puente Kotlin ⇄ C++

Dos canales, ambos sin bloqueos ni objetos JNI complejos:

**UI → sim: cola de comandos.** `NativeBridge.send(cmdId: Int, a: Int, b: Int, c: Int, d: Int)`; en C++ una cola concurrente que `drainCommands()` vacía al inicio de cada fotograma. Suficiente para todos los comandos (coordenadas, ids de especie, ids de nodo).

**Sim → UI: snapshot.** Cada 100 ms C++ serializa un `GameSnapshot` compacto en un `ByteBuffer` directo compartido: dinero, ingresos/min, visitantes, cuatro índices, estrellas, reputación, alertas (hasta 8: tipo + tile), estado de expediciones/incubaciones/investigación, y el detalle de la **entidad seleccionada**. Kotlin lo decodifica en un `StateFlow<GameSnapshot>` que Compose observa. Nada de llamadas JNI por campo.

La superposición de Compose se añade en `MainActivity.onCreate` con `addContentView(ComposeView(...))`. Los toques que caen en zonas sin controles de Compose pasan al `SurfaceView` (Compose no consume lo que no toca un composable interactivo).

---

## 8. Pruebas y balance

- `core/` compilado en el PC con `-DCP_HOST_BUILD=ON` + **doctest**: recintos (flood-fill), validación de comandos, fórmulas de necesidad/estrés, guardado ida y vuelta, determinismo (misma semilla + mismos comandos ⇒ mismo hash de estado).
- **Simulación sin cabeza** `tools/headless`: ejecuta una partida con un guion de comandos (por ejemplo, el "objetivo de ritmo" de [BALANCE.md §8](BALANCE.md)) a máxima velocidad y vuelca CSV de dinero, visitantes, índices y estrellas por minuto. Es la herramienta principal para ajustar las tablas de balance sin tocar el teléfono.
- Perfilado en dispositivo con **Android GPU Inspector** y `systrace`; objetivo 60 fps en un SoC de gama media de 2021 con 150 visitantes y 40 dinos.

---

## 9. Actualización de la plantilla

Cambios necesarios sobre el proyecto actual antes de escribir código de juego:

| Qué | De | A | Motivo |
|---|---|---|---|
| AGP | 8.4.0 | 8.7+ | Compose y NDK recientes |
| Kotlin | 1.9.0 | 2.0.21 + `org.jetbrains.kotlin.plugin.compose` | Compose sin versión de compilador manual |
| compileSdk / targetSdk | 34 | 35 | Requisito de Play para 2025+ |
| Java | 1.8 | 17 | Requerido por AGP 8.7 |
| Dependencias | appcompat, material | + `activity-compose`, `compose-bom`, `material3`, `datastore-preferences`, `lifecycle-runtime-compose` | UI y meta |
| CMake | — | `CMAKE_CXX_STANDARD 20`, subdirectorios `core/ render/ platform/`, opción `CP_HOST_BUILD` | Estructura §2 |
| `main.cpp` / `Renderer.cpp` | demo de la plantilla | bucle de §3 y render de §5 | — |
| `MainActivity.kt` | `systemUiVisibility` (obsoleto) | `WindowInsetsControllerCompat` + `ComposeView` superpuesta | API moderna y superposición de UI |
| Manifest | — | `android:screenOrientation="sensorLandscape"`, `configChanges` para no recrear la actividad al rotar | Juego horizontal |

---

## 10. Orden de implementación (MVP)

1. Actualizar plantilla (§9). Pantalla vacía con Compose "Hola" sobre el `SurfaceView`.
2. `Grid` + `Terrain` + `Camera` + gestos: isla de hierba que se puede desplazar, girar y ampliar.
3. `vox2mesh` + `VoxelMesh` + `InstanceBatch`: colocar un edificio y una valla desde una barra Compose.
4. Recintos (flood-fill) y previsualización verde/roja con `Command::validate`.
5. Dinos: 4 especies del MVP, necesidades, estrés, ataque a valla, fuga, dardo.
6. Visitantes, entrada, tiendas, economía, snapshot al HUD.
7. Expediciones e incubación; investigación de 6 nodos; valoración por estrellas.
8. Evento Tormenta, guardado, resumen de sesión.
9. Simulación sin cabeza y primer pase de balance sobre Isla Brote.
