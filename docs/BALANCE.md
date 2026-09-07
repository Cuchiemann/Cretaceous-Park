# Cretaceous Park — Datos de balance y fórmulas

Complemento numérico del [GDD](GDD.md). Todos los valores son de **primera iteración** y están pensados para ajustarse con la simulación sin cabeza descrita en [TECH.md §8](TECH.md). Las mismas tablas viven en formato máquina en `app/src/main/assets/data/*.json`.

Convenciones: `$` es la moneda del parque; los tiempos son **segundos reales a velocidad 1×**; "tile" es la celda de la rejilla.

---

## 1. Especies

| Especie | Dieta | Tamaño | Coste incubación | Espacio mín. (tiles/dino) | Grupo (mín–máx) | Atractivo (1–10) | Peligro (1–10) | Fuerza de ataque (PV/golpe) | Valla mínima | Dardos | Yacimiento |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Gallimimus | Herb. | P | 8.000 | 4 | 3–10 | 2 | 1 | 5 | Ligera | 1 | Cañón Rojo |
| Dryosaurus | Herb. | P | 7.000 | 4 | 2–8 | 2 | 1 | 5 | Ligera | 1 | Cañón Rojo |
| Parasaurolophus | Herb. | M | 18.000 | 9 | 2–8 | 4 | 2 | 20 | Media | 2 | Cañón Rojo |
| Stegosaurus | Herb. | M | 22.000 | 10 | 1–5 | 5 | 3 | 35 | Media | 2 | Estepa Gris |
| Ankylosaurus | Herb. | M | 26.000 | 10 | 1–4 | 5 | 3 | 45 | Media | 2 | Costa de Sal |
| Triceratops | Herb. | G | 40.000 | 16 | 1–5 | 6 | 4 | 70 | Pesada | 3 | Costa de Sal |
| Brachiosaurus | Herb. | G | 60.000 | 25 | 1–4 | 8 | 2 | 60 | Pesada | 3 | Bosque Petrificado |
| Diplodocus | Herb. | G | 55.000 | 24 | 1–4 | 7 | 2 | 55 | Pesada | 3 | Bosque Petrificado |
| Compsognathus | Carn. | P | 9.000 | 3 | 4–12 | 3 | 1 | 8 | Ligera | 1 | Cañón Rojo |
| Velociraptor | Carn. | P | 20.000 | 6 | 2–6 | 6 | 6 | 25 | Media | 1 | Estepa Gris |
| Dilophosaurus | Carn. | M | 24.000 | 8 | 2–5 | 5 | 5 | 30 | Media | 2 | Estepa Gris |
| Ceratosaurus | Carn. | M | 32.000 | 12 | 1–2 | 6 | 6 | 45 | Media | 2 | Costa de Sal |
| Carnotaurus | Carn. | M | 36.000 | 12 | 1–2 | 6 | 7 | 50 | Pesada | 2 | Bosque Petrificado |
| Allosaurus | Carn. | G | 55.000 | 20 | 1–2 | 8 | 8 | 80 | Pesada | 3 | Desierto Blanco |
| Spinosaurus | Carn. | G | 75.000 | 24 (+4 tiles de agua) | 1–1 | 9 | 9 | 90 | Pesada | 3 | Desierto Blanco |
| Tyrannosaurus | Carn. | G | 100.000 | 24 | 1–1 | 10 | 10 | 120 | Pesada (eléctrica recomendada) | 3 | Glaciar Norte |

Tamaño: P = 1×1 tile, M = 2×2, G = 3×3.

**Consumo por tamaño**

| Tamaño | Comida (puntos/min) | Agua (puntos/min) | Unidades de comedero por comida | Velocidad (tiles/s) |
|---|---|---|---|---|
| P | 10 | 15 | 1 | 1,5 |
| M | 12 | 15 | 2 | 1,0 |
| G | 15 | 15 | 4 | 0,7 |

Cuando Comida < 40 el dino busca comedero; comer restaura +40 puntos y consume las unidades indicadas. Igual con Agua (bebedero o tile de agua; el agua no se agota).

**Peligro** determina qué hace un dino fugado: 1–2 huye de los visitantes; 3–4 pisotea lo que tenga delante (edificios −PV, visitantes heridos); 5+ persigue visitantes a ≤ 8 tiles.

---

## 2. Edificios y construcciones

### 2.1 Recintos

| Elemento | Tamaño | Coste | Mantenimiento ($/min) | Datos |
|---|---|---|---|---|
| Valla ligera | borde | 50 | 1 | 100 PV |
| Valla media | borde | 120 | 2 | 250 PV. Requiere inv. B1 |
| Valla pesada | borde | 300 | 4 | 600 PV. Requiere inv. B3 |
| Valla eléctrica | borde | 250 | 3 + energía | 400 PV. Con energía: ataque recibido ×0,3. Requiere inv. B6 |
| Puerta de recinto | borde | 200 | 1 | Misma resistencia que la valla adyacente. Se puede abrir/cerrar; cerrada por defecto |
| Comedero herbívoros | 1×1 | 800 | 0 | 50 unidades. Reposición 200 $. Automático con inv. B1 (repone al llegar a 10) |
| Comedero carnívoros | 1×1 | 1.500 | 0 | 20 unidades. Reposición 400 $ |
| Bebedero | 1×1 | 500 | 2 | Sirve a un recinto entero. Con inv. B2 cuenta como 4 tiles de agua (Spinosaurus) |
| Refugio de dinos | 2×2 | 2.000 | 5 | Durante tormenta: estrés −20 a los dinos del recinto. Requiere inv. B4 |

### 2.2 Caminos y vistas

| Elemento | Tamaño | Coste | Mant. | Datos |
|---|---|---|---|---|
| Camino | 1×1 | 20 | 0 | Capacidad 2 visitantes/tile |
| Camino ancho | 2×1 | 50 | 0 | Capacidad 5 visitantes/tile. Requiere inv. E2 |
| Puente | 1×1 sobre agua | 150 | 1 | Como camino |
| Mirador | 1×1 junto a valla | 600 | 5 | 6 visitantes. Alcance de visión 6 tiles |
| Galería | 2×1 junto a valla | 1.500 | 10 | 12 visitantes. Alcance 8 tiles. Techada: cuenta como refugio en tormenta. Requiere inv. E1 |

### 2.3 Servicios

| Elemento | Tamaño | Coste | Mant. | Datos |
|---|---|---|---|---|
| Tienda de comida | 2×2 | 3.000 | 40 | Atiende 1 visitante / 2 s. Precio 15 $. Hambre +60 |
| Tienda de bebida | 2×2 | 2.500 | 30 | 1 visitante / 1,5 s. Precio 8 $. Sed +60 |
| Tienda de recuerdos | 2×2 | 5.000 | 50 | 1 visitante / 3 s. Precio 40 $. Diversión +20. Requiere inv. E3 |
| Aseos | 1×1 | 1.000 | 10 | 1 visitante / 4 s. Gratis. Descanso +30 |
| Hotel pequeño | 3×3 | 12.000 | 120 | 20 camas. 60 $/noche. Descanso → 100. Requiere inv. E4 |
| Hotel grande | 3×3 | 30.000 | 250 | 60 camas. 80 $/noche. Requiere inv. E5 |
| Refugio de visitantes | 2×2 | 4.000 | 20 | 40 personas durante tormenta / fuga |

### 2.4 Centros

| Elemento | Tamaño | Coste | Mant. | Datos |
|---|---|---|---|---|
| Entrada del parque | 3×2 | — (fija) | 50 | Muelle del ferry. Precio de entrada: 20 / 40 / 70 $ |
| Generador | 2×2 | 8.000 | 100 | Energía en radio 10 tiles |
| Centro de Expediciones | 3×3 | 15.000 | 150 | 1 equipo (2 con inv. G2) |
| Laboratorio + Incubadora | 3×3 | 25.000 | 250 | 2 incubaciones a la vez (3 con inv. G6). Necesita energía |
| Centro de Investigación | 3×3 | 20.000 | 200 | 10 PI/min (+5 con energía). Máximo 2 centros |
| Centro de Rangers | 3×3 | 18.000 | 200 | Dardos, curación y transporte en radio 15 tiles. Recarga de dardo 8 s |

### 2.5 Terreno

| Acción | Coste/tile |
|---|---|
| Talar bosque → hierba | 100 |
| Cavar agua | 150 |
| Rellenar agua → hierba | 150 |
| Plantar bosque | 80 |
| Demoler roca | 400 |
| Demoler edificio | gratis, devuelve el 50 % del coste |

---

## 3. Árbol de investigación

Un nodo activo a la vez. Coste en Puntos de Investigación (PI) y dinero; el tiempo corre solo con un Centro de Investigación en pie y con energía.

### Rama Bienestar

| Id | Nodo | PI | $ | Tiempo | Requiere | Efecto |
|---|---|---|---|---|---|---|
| B1 | Valla media y comederos automáticos | 50 | 2.000 | 45 s | — | Desbloquea valla media; los comederos reponen solos |
| B2 | Bebedero grande | 80 | 3.000 | 60 s | B1 | Bebedero cuenta como 4 tiles de agua |
| B3 | Valla pesada | 150 | 6.000 | 90 s | B1 | Desbloquea valla pesada |
| B4 | Refugio de tormenta | 120 | 4.000 | 60 s | B2 | Desbloquea refugio de dinos |
| B5 | Medicina | 200 | 8.000 | 120 s | B4 | "Curar" desde el panel del dino sin límite de radio; enfermedad −50 % probabilidad |
| B6 | Valla eléctrica | 300 | 12.000 | 150 s | B3 | Desbloquea valla eléctrica |

### Rama Entretenimiento

| Id | Nodo | PI | $ | Tiempo | Requiere | Efecto |
|---|---|---|---|---|---|---|
| E1 | Galería | 60 | 2.500 | 45 s | — | Desbloquea galería |
| E2 | Camino ancho | 80 | 2.000 | 45 s | E1 | Desbloquea camino ancho |
| E3 | Tienda de recuerdos | 120 | 5.000 | 60 s | E1 | Desbloquea tienda de recuerdos |
| E4 | Hotel pequeño | 180 | 8.000 | 90 s | E2, E3 | Desbloquea hotel pequeño |
| E5 | Hotel grande | 280 | 15.000 | 120 s | E4 | Desbloquea hotel grande |
| E6 | Publicidad | 250 | 10.000 | 90 s | E4 | Reputación +10 inmediata y tope de llegadas +25 % |

### Rama Genética

| Id | Nodo | PI | $ | Tiempo | Requiere | Efecto |
|---|---|---|---|---|---|---|
| G1 | Yacimiento Estepa Gris | 60 | 3.000 | 45 s | — | Nuevo yacimiento |
| G2 | Segundo equipo | 120 | 6.000 | 60 s | G1 | 2 expediciones simultáneas |
| G3 | Gen de Piel | 100 | 5.000 | 60 s | G1 | Ranura Piel: +1 atractivo, color alternativo |
| G4 | Yacimiento Costa de Sal | 180 | 8.000 | 90 s | G2 | Nuevo yacimiento |
| G5 | Genes de Resistencia y Temperamento | 220 | 10.000 | 120 s | G3, G4 | Ranuras Resistencia (enfermedad −60 %, hambre estresa −50 %) y Temperamento (Dócil: ataque ×0,5, atractivo −1 · Vistoso: atractivo +2, estrés ×1,3) |
| G6 | Secuenciación rápida | 300 | 15.000 | 150 s | G5 | +5 % ADN por fósil, 3.ª incubadora |

Los yacimientos Bosque Petrificado, Desierto Blanco y Glaciar Norte se desbloquean al **entrar** en las islas 2, 3 y 4 respectivamente (quedan disponibles para siempre).

### Yacimientos

| Yacimiento | Coste expedición | Duración | Fósiles | Especies posibles |
|---|---|---|---|---|
| Cañón Rojo | 3.000 | 60 s | 4 | Gallimimus, Dryosaurus, Parasaurolophus, Compsognathus |
| Estepa Gris | 5.000 | 75 s | 4 | Stegosaurus, Velociraptor, Dilophosaurus |
| Costa de Sal | 7.000 | 90 s | 4 | Ankylosaurus, Triceratops, Ceratosaurus |
| Bosque Petrificado | 9.000 | 100 s | 3 | Brachiosaurus, Diplodocus, Carnotaurus |
| Desierto Blanco | 11.000 | 110 s | 3 | Allosaurus, Spinosaurus |
| Glaciar Norte | 12.000 | 120 s | 3 | Tyrannosaurus |

Calidad de fósil: 50 % Baja (+5–10 % ADN), 35 % Media (+10–20 %), 13 % Alta (+20–35 %), 2 % Raro (+35 % y +5 Ámbar). Un fósil de una especie ya al 100 % se **vende automáticamente** por 1.000 / 2.500 / 5.000 $ según calidad.

**Viabilidad** de incubación: `éxito% = 55 + 43 × (ADN − 50) / 50` para ADN entre 50 y 100 (→ 55 % a 98 %), −5 por cada gen aplicado. Tiempo de incubación: P 30 s, M 60 s, G 90 s.

---

## 4. Visitantes

### 4.1 Llegadas

Cada 20 s llega un ferry con:

```
llegadas = redondear( 2 + 0,25 × IndiceAtractivo × (0,5 + Reputación / 100) ) × modPrecio
modPrecio: entrada baja 1,2 · media 1,0 · alta 0,8 (0,6 si Reputación < 50)
tope     = 2 × tiles de camino + 5 × tiles de camino ancho + camas de hotel
```

Nunca entran más visitantes que `tope − visitantes presentes`. Por encima de **150 visitantes** se dejan de crear agentes: los excedentes se contabilizan como "visitantes agregados" que pagan entrada y gastan `presupuesto × multiplicador(ComodidadMedia)` al final de su ciclo.

### 4.2 Agente

| Parámetro | Valor |
|---|---|
| Necesidades | Hambre, Sed, Descanso, Diversión (0–100; empiezan en 70/70/100/30) |
| Descenso | Hambre −1 cada 6 s · Sed −1 cada 4 s · Descanso −1 cada 8 s · Diversión −1 cada 5 s |
| Umbral de búsqueda | Necesidad < 40 → busca el servicio más cercano por camino |
| Paciencia | 40 s sin encontrar servicio → Comodidad −20 y desiste 60 s |
| Presupuesto de gasto | 120 $ (más 30 $ por noche de hotel) |
| Ciclo de visita | 180 s; con hotel, +180 s por noche (máx. 2 noches) |
| Comodidad | media de las 4 necesidades |
| Multiplicador de gasto | Comodidad ≥ 70 → 1,0 · 40–69 → 0,5 · < 40 → 0,2 |
| Salida enfadada | Comodidad < 30 al irse → Reputación −0,5 |

### 4.3 Ver dinosaurios

Un visitante en mirador o galería gana Diversión cada 5 s:

```
ganancia = Σ especies visibles ( atractivo × factorDistancia × factorNovedad )
factorDistancia = 1,0 si la especie tiene un dino a ≤ 4 tiles; 0,5 si a ≤ alcance del mirador
                  (tamaño G: alcance ×1,5)
factorNovedad   = 1,0 la primera vez que ve la especie; 0,4 después
```

Un visitante ve un cadáver o una fuga: Diversión −30, Comodidad −20 y huye al refugio más cercano.

---

## 5. Dinosaurios: necesidades, estrés y fuga

### 5.1 Índices (0–100)

```
Espacio   = min(100, 100 × tilesRecinto / Σ_dinos( espacioMin × factorGrupo ))
            factorGrupo = max(0,6 ; 1 − 0,05 × (n − 1))   ; n = dinos del recinto
            tiles de arena cuentan 0,5 ; bosque 1,0 (herbívoros) / 0,5 (carnívoros)
Comida    = barra propia (0–100)
Agua      = barra propia (0–100)
Compañía  = 100 si grupoMin ≤ n_especie ≤ grupoMax
          = 100 − 25 × (grupoMin − n_especie) si faltan (mín. 25)
          = 30 si sobran ; −40 adicional si hay carnívoros de otra especie en el recinto
Ponderado = 0,30 Espacio + 0,30 Comida + 0,20 Agua + 0,20 Compañía
Bienestar = 0,5 × Ponderado + 0,5 × min(Espacio, Comida, Agua, Compañía)
            (la peor necesidad pesa la mitad: un dino sin comida no se consuela con espacio)
```

### 5.2 Estrés

```
si Bienestar < 50 :  dEstrés/dt = (50 − Bienestar) / 20   por segundo   (Bienestar 0 → 100 en 40 s)
si Bienestar > 60 :  dEstrés/dt = −(Bienestar − 60) / 20  por segundo
Gen Temperamento "Vistoso": ×1,3 · Gen Resistencia: la parte debida a Comida cuenta ×0,5
Tormenta: +0,5/s a todos los dinos sin refugio
```

Umbrales: 40 burbuja amarilla · 75 burbuja roja y camina hacia la valla más débil del recinto · 100 golpea.

### 5.3 Vallas y fuga

- Un golpe cada 3 s: `daño = fuerzaAtaque × (0,3 si eléctrica con energía)`.
- Tramo a 0 PV → hueco. El dino sale por el hueco. Otros dinos del recinto con estrés > 75 también salen.
- Dino fuera: comportamiento según Peligro (§1). Contacto con visitante: 70 % herido (indemnización 2.000 $, Seguridad −5), 30 % muerte (10.000 $, Seguridad −20, Reputación −5). Un dino ataca como mucho una vez cada 5 s.
- Dardo: tiempo de vuelo 1 s; al recibir `dardos` impactos el dino duerme 60 s. "Transportar" cuesta 2.000 (P) / 5.000 (M) / 10.000 (G) $ y lo lleva a un recinto elegido.
- Reparar hueco: 30 % del coste del tramo, 5 s de trabajo.

### 5.4 Muerte

Comida o Agua a 0 durante 150 s → muerte (antes, hacia los 120 s, el estrés llega a 100 y el dino intenta escapar: la fuga precede a la muerte por diseño). Cadáver 30 s. Bienestar del parque −10 por muerte durante 5 min.

---

## 6. Valoración y reputación

Cada índice se calcula cada 10 s y se **suaviza** con un factor 0,3 hacia el nuevo valor (evita saltos).

```
Beneficios = clamp(0..100, 100 × netoPorMinuto / objetivoIsla)
             objetivoIsla: Brote 400 · Ceniza 800 · Tormenta 1.200 · Espejo 1.800 · Corona 2.500

Seguridad  = clamp(0..100, 100 − penalizaciones)
             fuga activa                            −40
             cada herido en los últimos 3 min        −5
             cada muerte de visitante en 5 min      −20
             cada dino con valla < valla mínima      −5   (máx. −30)
             carnívoros presentes sin Centro Rangers −15
             > 50 visitantes sin plazas de refugio  −10

Bienestar  = media(Bienestar de cada dino) − 10 × muertes de dinos en los últimos 5 min
             (0 si no hay dinos)

Atractivo  = 40 × min(1, especiesVivas / 8)
           + 30 × ComodidadMedia / 100
           + 30 × min(1, Σ atractivo de especies vivas / 40)

Valoración100 = media de los cuatro
Estrellas     = redondearAMedia(Valoración100 / 20)
5★ solo si los cuatro índices ≥ 90 ; si no, tope 4,5★
```

Isla completada: 5★ mantenidas 60 s.

**Reputación** (0–100, inicial 50): `dR/dt = (Valoración100 − R) / 60` por segundo, más los impactos puntuales (salidas enfadadas, muertes, Publicidad).

---

## 7. Eventos

Tras cada evento se sortea el intervalo hasta el siguiente. Un evento no empieza si hay otro activo o una fuga en curso.

| Isla | Intervalo | Tormenta | Enfermedad | Fuga | Sabotaje |
|---|---|---|---|---|---|
| Brote | 300–420 s | 100 % (suave) | — | — | — |
| Ceniza | 240–360 s | 50 % | 50 % | — | — |
| Tormenta | 200–300 s | 50 % (fuerte) | 25 % | 25 % | — |
| Espejo | 180–300 s | 30 % | 25 % | 20 % | 25 % |
| Corona | 150–240 s | 30 % (fuerte) | 25 % | 20 % | 25 % |

**Tormenta**: aviso 20 s; dura 60 s (suave) / 90 s / 120 s (fuerte). Cada 10 s, cada tramo de valla tiene 10 % (suave) / 20 % / 30 % (fuerte) de perder 30 PV. Cada 30 s, 20 % de apagar el Generador durante 30 s. Visitantes fuera de refugio: Comodidad −2/s.

**Enfermedad**: aviso 10 s. Un dino al azar: Bienestar −40 (multiplicador 0,6 mientras dure). Cada 30 s contagia a otro del recinto (50 %). Sin cura, muere a los 180 s. Curar: 1.500 $ (P) / 3.000 (M) / 6.000 (G).

**Fuga**: aviso 15 s. Un dino con Peligro ≥ 3 pasa a estrés 100.

**Sabotaje**: aviso 10 s. Uno de: Generador apagado 60 s · una puerta de recinto abierta · un comedero vaciado. Con Centro de Rangers a ≤ 15 tiles la duración baja a 30 s.

---

## 8. Islas: presupuesto y objetivos

| Isla | Presupuesto inicial | Objetivo beneficio ($/min) | Hierba / Bosque / Roca / Agua / Arena (%) |
|---|---|---|---|
| Brote 63×63 | 150.000 | 400 | 70 / 15 / 3 / 5 / 7 |
| Ceniza 89×89 | 200.000 | 800 | 45 / 10 / 30 / 5 / 10 |
| Tormenta 101×101 | 220.000 | 1.200 | 55 / 20 / 5 / 10 / 10 |
| Espejo 114×114 | 250.000 | 1.800 | 45 / 15 / 5 / 30 / 5 |
| Corona 139×139 | 180.000 | 2.500 | 50 / 20 / 10 / 15 / 5 |

**Objetivo de ritmo en Isla Brote** (primeros 15 min): recinto de 6×6 con valla ligera (24 tramos = 1.200 $) + 10 tiles de camino (200) + comedero (800) + bebedero (500) + tienda de comida (3.000) + Centro de Expediciones (15.000) + Laboratorio (25.000) + Generador (8.000) = ~54.000 $. Quedan ~96.000 $ para 2 expediciones e incubar 3 Gallimimus (24.000) con margen. Primer ingreso neto positivo esperado en el minuto 6–8.

---

## 9. Tienda de Ámbar

| Desbloqueo | Coste (Ámbar) |
|---|---|
| Inicio con especie P / M / G (50 % ADN en toda isla nueva) | 30 / 50 / 80 |
| Edificio inicial: valla media / tienda de recuerdos / Centro de Rangers | 40 / 60 / 100 |
| Presupuesto +10 % / +20 % / +30 % | 50 / 100 / 200 |
| Piel alternativa (por especie) | 20 |
| Isla Corona | 400 |
| Reto de isla (modificador) | gratis; recompensa 50–150 Ámbar |

Ámbar total posible en una vuelta completa: 16 especies × 10 + 5 islas × 5 estrellas × 15 + 5 × 100 ≈ 1.035 + raros + retos. El coste de comprarlo todo es ≈ 1.900, así que la segunda vuelta con retos sigue teniendo objetivo.
