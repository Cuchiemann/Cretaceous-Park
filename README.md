# Cretaceous Park

Juego de gestión de un parque de dinosaurios para Android. Mecánicas de *Jurassic World Evolution 1* (isla vacía, presupuesto, recintos, fósiles → ADN → clonación, visitantes, cinco estrellas) con la estética voxel de colores planos y cámara isométrica fija de *Crossy Road*. Sin historia ni personajes; partidas cortas con guardado y desbloqueos permanentes.

## Documentación de diseño

| Documento | Contenido |
|---|---|
| [docs/GDD.md](docs/GDD.md) | Visión, pilares, estilo visual, controles táctiles, bucles de juego, islas, construcción, fósiles/ADN/clonación, dinosaurios (necesidades, estrés, fuga), visitantes y economía, investigación, valoración por estrellas, eventos, meta-progresión (Ámbar), interfaz, audio, guardado, alcance |
| [docs/BALANCE.md](docs/BALANCE.md) | Tablas numéricas: 16 especies, edificios y vallas, árbol de investigación (18 nodos), yacimientos, fórmulas de visitantes, estrés, valoración, eventos por isla, presupuestos, tienda de Ámbar |
| [docs/TECH.md](docs/TECH.md) | Arquitectura sobre esta plantilla GameActivity + C++/GLES3: simulación en C++ con tick fijo, render voxel instanciado, UI en Compose superpuesta, puente JNI, guardado versionado, pruebas y orden de implementación |

Los datos de balance también están en formato máquina para que la simulación los cargue directamente:

- `app/src/main/assets/data/species.json` — especies, yacimientos, calidad de fósiles, clonación, necesidades y estrés
- `app/src/main/assets/data/buildings.json` — vallas, edificios, terreno y parámetros de visitantes

## Estado del proyecto

**Prototipo jugable v0.1** en Kotlin (Canvas isométrico + Jetpack Compose). Incluye: 6 islas (de 63×63 a 139×139 tiles), 16 especies, recintos por vallas, comederos y bebederos, caminos, miradores, tiendas, hoteles, centros, expediciones y ADN, incubación, investigación (18 nodos), visitantes con necesidades y gasto, estrés, fugas y dardos, cuatro eventos aleatorios, valoración por estrellas, guardado automático por isla, Ámbar persistente con tienda de desbloqueos (presupuesto, investigaciones iniciales, ADN de especies) y genes en la incubadora (piel, resistencia, temperamento). Tutorial guiado de 16 objetivos (sin recompensas) en Isla Brote, minimapa táctil y botón Atrás integrado. Pendiente: audio, inglés, retos de isla.

### Compilar e instalar

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Pruebas de simulación sin cabeza (JVM): `./gradlew testDebugUnitTest` ejecuta `HeadlessSimTest`, que construye un parque, lo deja correr 20 minutos, fuerza una fuga y recorre la cadena expedición → ADN → incubación → investigación.

### Cómo empezar a jugar

1. **Recintos → Valla ligera**: arrastra un rectángulo sobre hierba (no puede tocar caminos).
2. Dentro del recinto: **Comedero herbívoros** y **Bebedero**.
3. **Caminos → Camino**: arrastra desde el camino de la entrada; pon un **Mirador** pegado a la valla y con camino al lado.
4. **Centros**: Generador (energía en 10 tiles), Centro de Expediciones, Laboratorio.
5. 🌍 envía una expedición; con ADN ≥ 50 % incuba en 🧬 eligiendo el recinto.
6. Vigila las alertas: un dino con hambre, sed o sin compañía se estresa y rompe la valla. El Centro de Rangers permite dormirlo con dardos y transportarlo.
