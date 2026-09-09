package com.momentadesunt.cretaceouspark.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.momentadesunt.cretaceouspark.core.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Dibuja el mundo como cajas isométricas de color plano con tres tonos.
 *
 * El suelo tiene relieve (niveles de Terrain.STEP), así que se pinta por diagonales de vista (atrás → delante)
 * intercalado con los objetos de esa misma diagonal: un tile alto tapa lo que queda detrás de él y los objetos
 * situados delante se pintan encima. Dentro de una diagonal ni las caras ni las cimas se solapan, por lo que
 * ahí sí se agrupan por color en un solo Path.
 */
class IsoRenderer(val cam: IsoCamera) {
    private val paint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val linePaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true; strokeWidth = 3f }
    private val path = Path()

    private val objs = ArrayList<BoxGroup>(1024)
    private val flat = ArrayList<RBox>(4096)
    private val sorter = BoxSorter(cam)
    private val groundPaths = HashMap<Int, Path>()
    private val usedPaths = ArrayList<Path>(16)
    private val usedColors = ArrayList<Int>(16)

    // colores
    private val cGrass = 0xFF5FA85A.toInt()
    private val cGrass2 = 0xFF63AE5E.toInt()
    private val cSand = 0xFFE3C97A.toInt()
    private val cWater = 0xFF4FA3D9.toInt()
    private val cSea = 0xFF3B8FC4.toInt()
    private val cPath = 0xFFB9AE95.toInt()
    private val cBridge = 0xFFA98F6A.toInt()
    private val cRockGround = 0xFF8B8F8A.toInt()
    private val cRock = 0xFF9A9E9A.toInt()
    private val cTrunk = 0xFF6B4A2B.toInt()
    private val cLeaf = 0xFF2E6B3A.toInt()
    private val cLeaf2 = 0xFF3F8C4E.toInt()
    private val cCliff = 0xFF8A7A5A.toInt()
    private val cFence = intArrayOf(0, 0xFFC9B27A.toInt(), 0xFFB0A088.toInt(), 0xFF6F7278.toInt(), 0xFF4FA3D9.toInt())
    private val cGate = 0xFF8C5A2B.toInt()
    private val cSkin = 0xFFF1D9B5.toInt()
    private val cLegs = 0xFF3A3F4A.toInt()

    // overlay / herramientas
    var ghost: Ghost? = null
    /** Plano pendiente: claves de borde (vallas) o índices de tile (caminos), con validez por elemento. */
    var planKeys: IntArray = IntArray(0)
    var planOk: BooleanArray = BooleanArray(0)
    var planFenceType = 0        // 0 = el plano son tiles de camino
    var brush: FloatArray? = null   // x, y, radio (en tiles)
    var selectedDino = -1
    var selectedBuilding = -1
    var selectedEdge: EdgeRef? = null
    var showRegions = false
    var highlightRegion = -1
    var lastObjCount = 0

    class Ghost(val x: Int, val y: Int, val w: Int, val h: Int, val ok: Boolean, val height: Float, val color: Int)

    fun shade(c: Int, f: Float): Int {
        val r = (Color.red(c) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(c) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) * f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    // ------------------------------------------------------------------ dibujo principal
    fun draw(c: Canvas, w: World, time: Float) {
        val s = w.s
        cam.prepare()
        c.drawColor(cSea)
        collectObjects(w, time)
        lastObjCount = objs.size
        sorter.sort(objs, flat)
        drawGroundAndObjects(c, s)
        drawOverlays(c, w)
        if (w.stormActive) drawStorm(c, time)
    }

    /** Vista previa: un dino sobre 4×4 tiles de hierba, sin mundo. */
    fun drawPreview(c: Canvas, d: Dino, time: Float) {
        cam.prepare()
        beginGround()
        for (y in 1..4) for (x in 1..4) {
            val cx = floor(cam.toViewX(x + 0.5f, y + 0.5f)).toInt(); val cy = floor(cam.toViewY(x + 0.5f, y + 0.5f)).toInt()
            val wl = floor(cam.viewToWorldX(cx + 0.5f, cy + 1.5f)).toInt() to floor(cam.viewToWorldY(cx + 0.5f, cy + 1.5f)).toInt()
            val wr = floor(cam.viewToWorldX(cx + 1.5f, cy + 0.5f)).toInt() to floor(cam.viewToWorldY(cx + 1.5f, cy + 0.5f)).toInt()
            val zl = if (wl.first in 1..4 && wl.second in 1..4) 0f else -0.3f
            val zr = if (wr.first in 1..4 && wr.second in 1..4) 0f else -0.3f
            addTile(cx, cy, 0f, if ((x + y) and 1 == 0) cGrass else cGrass2, zl, -1f, zr, -1f)
        }
        flushGround(c)
        objs.clear()
        selectedDino = -1
        addDino(d, time, 0f)
        sorter.sort(objs, flat)
        for (b in flat) drawBox(c, b)
    }

    private fun visibleTileBounds(n: Int): IntArray {
        // límites aproximados de tiles visibles (4 esquinas de pantalla)
        var minX = n; var minY = n; var maxX = -1; var maxY = -1
        for ((px, py) in listOf(0f to 0f, cam.screenW to 0f, 0f to cam.screenH + cam.zUnit * 4, cam.screenW to cam.screenH + cam.zUnit * 4)) {
            val (wx, wy) = cam.screenToWorld(px, py)
            minX = min(minX, floor(wx).toInt()); maxX = max(maxX, floor(wx).toInt() + 1)
            minY = min(minY, floor(wy).toInt()); maxY = max(maxY, floor(wy).toInt() + 1)
        }
        return intArrayOf(max(0, minX - 1), max(0, minY - 1), min(n - 1, maxX + 1), min(n - 1, maxY + 1))
    }

    private fun groundPath(color: Int): Path {
        val p = groundPaths.getOrPut(color) { Path() }
        if (p.isEmpty) { usedPaths.add(p); usedColors.add(color) }
        return p
    }

    private fun beginGround() { for (p in usedPaths) p.rewind(); usedPaths.clear(); usedColors.clear() }

    /** Pinta y vacía los paths de suelo acumulados (una diagonal). */
    private fun flushGround(c: Canvas) {
        for (k in usedPaths.indices) { val p = usedPaths[k]; if (!p.isEmpty) { paint.color = usedColors[k]; c.drawPath(p, paint); p.rewind() } }
        usedPaths.clear(); usedColors.clear()
    }

    private fun addQuad(p: Path, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        p.moveTo(x0, y0); p.lineTo(x1, y1); p.lineTo(x2, y2); p.lineTo(x3, y3); p.close()
    }

    /** Cima de la celda de vista (cx, cy) a altura z. */
    private fun addTopCell(p: Path, cx: Int, cy: Int, z: Float) {
        val vx0 = cx.toFloat(); val vy0 = cy.toFloat(); val vx1 = vx0 + 1f; val vy1 = vy0 + 1f
        addQuad(p, cam.sx(vx0, vy0), cam.sy(vx0, vy0, z), cam.sx(vx1, vy0), cam.sy(vx1, vy0, z), cam.sx(vx1, vy1), cam.sy(vx1, vy1, z), cam.sx(vx0, vy1), cam.sy(vx0, vy1, z))
    }

    /** Cara frontal izquierda (lado vy+1) de la celda, entre las alturas zTop y zBottom. */
    private fun addLeftFace(p: Path, cx: Int, cy: Int, zTop: Float, zBottom: Float) {
        val vx0 = cx.toFloat(); val vy1 = cy + 1f; val vx1 = vx0 + 1f
        addQuad(p, cam.sx(vx0, vy1), cam.sy(vx0, vy1, zTop), cam.sx(vx1, vy1), cam.sy(vx1, vy1, zTop), cam.sx(vx1, vy1), cam.sy(vx1, vy1, zBottom), cam.sx(vx0, vy1), cam.sy(vx0, vy1, zBottom))
    }

    /** Cara frontal derecha (lado vx+1) de la celda. */
    private fun addRightFace(p: Path, cx: Int, cy: Int, zTop: Float, zBottom: Float) {
        val vx1 = cx + 1f; val vy0 = cy.toFloat(); val vy1 = vy0 + 1f
        addQuad(p, cam.sx(vx1, vy0), cam.sy(vx1, vy0, zTop), cam.sx(vx1, vy1), cam.sy(vx1, vy1, zTop), cam.sx(vx1, vy1), cam.sy(vx1, vy1, zBottom), cam.sx(vx1, vy0), cam.sy(vx1, vy0, zBottom))
    }

    private val cliffL by lazy { shade(cCliff, 0.85f) }
    private val cliffR by lazy { shade(cCliff, 0.7f) }
    private val waterSideL by lazy { shade(cWater, 0.85f) }
    private val waterSideR by lazy { shade(cWater, 0.7f) }

    /**
     * Un tile del suelo en la celda de vista (cx, cy): cima a zTop y, hacia cada vecino frontal más bajo, la cara
     * que baja hasta él. Si el vecino es agua (bank ≥ 0 = altura de su orilla) el tramo por debajo de la orilla
     * se pinta con el tono del agua: es el "borde del agua" que define el tamaño del bloque.
     */
    private fun addTile(cx: Int, cy: Int, zTop: Float, colTop: Int, zLeft: Float, bankLeft: Float, zRight: Float, bankRight: Float) {
        addTopCell(groundPath(colTop), cx, cy, zTop)
        if (zLeft < zTop - 1e-3f) {
            if (bankLeft >= 0f && bankLeft < zTop) { addLeftFace(groundPath(cliffL), cx, cy, zTop, bankLeft); addLeftFace(groundPath(waterSideL), cx, cy, bankLeft, zLeft) }
            else if (bankLeft >= 0f) addLeftFace(groundPath(waterSideL), cx, cy, zTop, zLeft)
            else addLeftFace(groundPath(cliffL), cx, cy, zTop, zLeft)
        }
        if (zRight < zTop - 1e-3f) {
            if (bankRight >= 0f && bankRight < zTop) { addRightFace(groundPath(cliffR), cx, cy, zTop, bankRight); addRightFace(groundPath(waterSideR), cx, cy, bankRight, zRight) }
            else if (bankRight >= 0f) addRightFace(groundPath(waterSideR), cx, cy, zTop, zRight)
            else addRightFace(groundPath(cliffR), cx, cy, zTop, zRight)
        }
    }

    /** Colores de cima por terreno, nivel y damero; las cotas altas van un poco más claras para leer el relieve. */
    private val topColors = IntArray(7 * (Terrain.MAX_LEVEL + 1) * 2).also { arr ->
        for (t in 0 until 7) for (lv in 0..Terrain.MAX_LEVEL) for (k in 0 until 2) {
            val base = when (t) {
                Terrain.GRASS, Terrain.FOREST -> if (k == 0) cGrass else cGrass2
                Terrain.SAND -> cSand
                Terrain.ROCK -> cRockGround
                Terrain.WATER -> cWater
                Terrain.PATH -> cPath
                else -> cBridge
            }
            arr[(t * (Terrain.MAX_LEVEL + 1) + lv) * 2 + k] = if (t == Terrain.WATER) base else shade(base, 1f + 0.055f * lv)
        }
    }

    /** Suelo y objetos intercalados por diagonal de vista (ver la nota de la clase). */
    private fun drawGroundAndObjects(c: Canvas, s: GameState) {
        val n = s.size
        val b = visibleTileBounds(n)
        // rango de celdas de vista que cubre el rectángulo visible de mundo
        var cxMin = Int.MAX_VALUE; var cxMax = Int.MIN_VALUE; var cyMin = Int.MAX_VALUE; var cyMax = Int.MIN_VALUE
        for ((wx, wy) in listOf(b[0] to b[1], b[2] to b[1], b[0] to b[3], b[2] to b[3])) {
            val cx = floor(cam.toViewX(wx + 0.5f, wy + 0.5f)).toInt(); val cy = floor(cam.toViewY(wx + 0.5f, wy + 0.5f)).toInt()
            cxMin = min(cxMin, cx); cxMax = max(cxMax, cx); cyMin = min(cyMin, cy); cyMax = max(cyMax, cy)
        }
        val dMin = cxMin + cyMin; val dMax = cxMax + cyMax
        var oi = 0
        while (oi < flat.size && flat[oi].col < dMin) drawBox(c, flat[oi++])
        beginGround()
        for (d in dMin..dMax) {
            for (cx in max(cxMin, d - cyMax)..min(cxMax, d - cyMin)) {
                val cy = d - cx
                val wx = floor(cam.viewToWorldX(cx + 0.5f, cy + 0.5f)).toInt(); val wy = floor(cam.viewToWorldY(cx + 0.5f, cy + 0.5f)).toInt()
                if (!s.inBounds(wx, wy) || !cam.visible(wx + 0.5f, wy + 0.5f, 1.5f)) continue
                val i = s.idx(wx, wy)
                val t = s.terrain[i]
                val zTop = s.groundZ(i)
                val col = topColors[(t * (Terrain.MAX_LEVEL + 1) + s.height[i]) * 2 + ((wx + wy) and 1)]
                // vecinos frontales: izquierda (cx, cy+1) y derecha (cx+1, cy)
                val lx = floor(cam.viewToWorldX(cx + 0.5f, cy + 1.5f)).toInt(); val ly = floor(cam.viewToWorldY(cx + 0.5f, cy + 1.5f)).toInt()
                val rx = floor(cam.viewToWorldX(cx + 1.5f, cy + 0.5f)).toInt(); val ry = floor(cam.viewToWorldY(cx + 1.5f, cy + 0.5f)).toInt()
                var zl = Terrain.SEA_Z; var bl = -1f; var zr = Terrain.SEA_Z; var br = -1f
                if (s.inBounds(lx, ly)) { val li = s.idx(lx, ly); zl = s.groundZ(li); if (s.terrain[li] == Terrain.WATER) bl = s.height[li] * Terrain.STEP }
                if (s.inBounds(rx, ry)) { val ri = s.idx(rx, ry); zr = s.groundZ(ri); if (s.terrain[ri] == Terrain.WATER) br = s.height[ri] * Terrain.STEP }
                addTile(cx, cy, zTop, col, zl, bl, zr, br)
            }
            flushGround(c)
            while (oi < flat.size && flat[oi].col <= d) drawBox(c, flat[oi++])
        }
        while (oi < flat.size) drawBox(c, flat[oi++])
    }

    // ------------------------------------------------------------------ cajas
    private fun drawBox(c: Canvas, b: RBox) {
        val vx0 = b.vx0; val vx1 = b.vx1; val vy0 = b.vy0; val vy1 = b.vy1
        val zt = b.z + b.h
        paint.alpha = 255
        // top
        paint.color = b.color; paint.alpha = b.alpha
        path.rewind()
        addQuad(path, cam.sx(vx0, vy0), cam.sy(vx0, vy0, zt), cam.sx(vx1, vy0), cam.sy(vx1, vy0, zt), cam.sx(vx1, vy1), cam.sy(vx1, vy1, zt), cam.sx(vx0, vy1), cam.sy(vx0, vy1, zt))
        c.drawPath(path, paint)
        if (b.h <= 0.001f) return
        // izquierda (vy1)
        paint.color = shade(b.color, 0.86f); paint.alpha = b.alpha
        path.rewind()
        addQuad(path, cam.sx(vx0, vy1), cam.sy(vx0, vy1, zt), cam.sx(vx1, vy1), cam.sy(vx1, vy1, zt), cam.sx(vx1, vy1), cam.sy(vx1, vy1, b.z), cam.sx(vx0, vy1), cam.sy(vx0, vy1, b.z))
        c.drawPath(path, paint)
        // derecha (vx1)
        paint.color = shade(b.color, 0.7f); paint.alpha = b.alpha
        path.rewind()
        addQuad(path, cam.sx(vx1, vy0), cam.sy(vx1, vy0, zt), cam.sx(vx1, vy1), cam.sy(vx1, vy1, zt), cam.sx(vx1, vy1), cam.sy(vx1, vy1, b.z), cam.sx(vx1, vy0), cam.sy(vx1, vy0, b.z))
        c.drawPath(path, paint)
        paint.alpha = 255
    }

    @Suppress("UNUSED_PARAMETER")
    private fun obj(x: Float, y: Float, w: Float = 1f, d: Float = 1f, zBias: Float = 0f): BoxGroup {
        val o = BoxGroup()
        objs.add(o)
        return o
    }

    private fun onScreen(x: Float, y: Float, margin: Float = 3f): Boolean {
        val sx = cam.worldSx(x, y); val sy = cam.worldSy(x, y, 0f)
        val m = margin * cam.tileW
        return sx > -m && sx < cam.screenW + m && sy > -m && sy < cam.screenH + m
    }

    // ------------------------------------------------------------------ objetos
    private fun collectObjects(w: World, time: Float) {
        objs.clear()
        val s = w.s; val n = s.size
        val b = visibleTileBounds(n)
        // árboles y rocas
        for (y in b[1]..b[3]) for (x in b[0]..b[2]) {
            val t = s.terrain[s.idx(x, y)]
            if (t != Terrain.FOREST && t != Terrain.ROCK) continue
            if (!cam.visible(x + 0.5f, y + 0.5f, 2f)) continue
            val gz = s.groundZ(x, y)
            if (t == Terrain.FOREST) {
                val h = 0.9f + ((x * 7 + y * 13) % 5) * 0.12f
                val o = obj(x.toFloat(), y.toFloat())
                o.add(RBox(x + 0.4f, y + 0.4f, gz, 0.2f, 0.2f, h * 0.45f, cTrunk))
                o.add(RBox(x + 0.15f, y + 0.15f, gz + h * 0.45f, 0.7f, 0.7f, h * 0.55f, if ((x + y) % 3 == 0) cLeaf2 else cLeaf))
                o.add(RBox(x + 0.3f, y + 0.3f, gz + h, 0.4f, 0.4f, 0.25f, cLeaf2))
            } else if (t == Terrain.ROCK) {
                val o = obj(x.toFloat(), y.toFloat())
                o.add(RBox(x + 0.1f, y + 0.2f, gz, 0.6f, 0.6f, 0.5f, cRock))
                o.add(RBox(x + 0.5f, y + 0.55f, gz, 0.4f, 0.35f, 0.3f, shade(cRock, 0.9f)))
            }
        }
        // vallas
        for (y in max(0, b[1])..min(n, b[3] + 1)) for (x in b[0]..b[2]) {
            val t = s.hType[s.hIdx(x, y)]
            if (t != 0 && cam.visible(x + 0.5f, y.toFloat(), 2f)) addFence(EdgeRef(true, x, y), t, s.hHp[s.hIdx(x, y)], s.hFlags[s.hIdx(x, y)], w)
        }
        for (y in b[1]..b[3]) for (x in max(0, b[0])..min(n, b[2] + 1)) {
            val t = s.vType[s.vIdx(x, y)]
            if (t != 0 && cam.visible(x.toFloat(), y + 0.5f, 2f)) addFence(EdgeRef(false, x, y), t, s.vHp[s.vIdx(x, y)], s.vFlags[s.vIdx(x, y)], w)
        }
        // edificios
        for ((i, bd) in s.buildings.withIndex()) {
            if (!onScreen(bd.cx, bd.cy, 4f)) continue
            addBuilding(bd, i == selectedBuildingIndex(s), time, bankZ(s, bd.x, bd.y))
        }
        // cadáveres
        for (c in s.corpses) if (onScreen(c.x, c.y, 3f)) {
            val def = GameData.species(c.species)
            val scale = DinoModels.scaleOf(def.size)
            val gz = groundZAt(s, c.x, c.y)
            val o = obj(c.x - 0.6f * scale, c.y - 0.6f * scale, 1.2f * scale, 1.2f * scale, 0.2f)
            DinoModels.buildCorpse(c.x, c.y, c.facing, def) { x, y, z, w, dd, h, color -> o.add(RBox(x, y, z + gz, w, dd, h, color)) }
            // moscas
            val fz = gz + 0.5f * scale + 0.1f * sin(time * 7f + c.id)
            o.add(RBox(c.x + 0.2f * sin(time * 3f + c.id), c.y + 0.2f * kotlin.math.cos(time * 2.3f + c.id), fz, 0.06f, 0.06f, 0.06f, 0xFF2B2F33.toInt()))
        }
        // dinos
        for (d in s.dinos) if (onScreen(d.x, d.y, 4f)) addDino(d, time, groundZAt(s, d.x, d.y))
        // visitantes
        for (v in s.visitors) if (onScreen(v.x, v.y, 2f)) addVisitor(v, groundZAt(s, v.x, v.y))
        // fantasma: apoyado en la cota más alta de su huella
        ghost?.let { g ->
            var gz = 0f
            for (yy in g.y until g.y + g.h) for (xx in g.x until g.x + g.w) if (s.inBounds(xx, yy)) gz = max(gz, bankZ(s, xx, yy))
            val o = obj(g.x.toFloat(), g.y.toFloat(), g.w.toFloat(), g.h.toFloat(), 0.5f)
            o.add(RBox(g.x.toFloat(), g.y.toFloat(), gz, g.w.toFloat(), g.h.toFloat(), g.height, if (g.ok) 0xFF7BE07B.toInt() else 0xFFE06060.toInt(), 150))
        }
        // plano de vallas pendiente
        if (planFenceType != 0) {
            val th = 0.12f
            val height = when (planFenceType) { Fence.LIGHT -> 0.7f; Fence.MEDIUM -> 0.95f; Fence.HEAVY -> 1.35f; else -> 1.1f }
            val keys = planKeys; val ok = planOk
            for (i in keys.indices) {
                val e = EdgeRef.fromKey(keys[i])
                if (!onScreen(e.x.toFloat(), e.y.toFloat(), 2f)) continue
                val col = if (i < ok.size && ok[i]) 0xFF7BE07B.toInt() else 0xFFE06060.toInt()
                val x = e.x.toFloat(); val y = e.y.toFloat()
                val gz = edgeZ(s, e)
                val o = if (e.h) obj(x, y - th / 2f, 1f, th, 0.5f) else obj(x - th / 2f, y, th, 1f, 0.5f)
                if (e.h) o.add(RBox(x, y - th / 2f, gz, 1f, th, height, col, 160)) else o.add(RBox(x - th / 2f, y, gz, th, 1f, height, col, 160))
            }
        }
    }

    /** Altura de la orilla del tile (el agua cuenta por su borde, no por su superficie). */
    private fun bankZ(s: GameState, x: Int, y: Int): Float = if (s.inBounds(x, y)) s.levelAt(x, y) * Terrain.STEP else 0f
    /** Altura del suelo bajo una posición continua. */
    private fun groundZAt(s: GameState, x: Float, y: Float): Float {
        val tx = floor(x).toInt(); val ty = floor(y).toInt()
        return if (s.inBounds(tx, ty)) s.groundZ(tx, ty) else 0f
    }
    /** Base de una valla: la orilla más alta de los dos tiles que separa. */
    private fun edgeZ(s: GameState, e: EdgeRef): Float =
        if (e.h) max(bankZ(s, e.x, e.y - 1), bankZ(s, e.x, e.y)) else max(bankZ(s, e.x - 1, e.y), bankZ(s, e.x, e.y))

    private fun selectedBuildingIndex(s: GameState): Int = if (selectedBuilding < 0) -1 else s.buildings.indexOfFirst { it.id == selectedBuilding }

    private fun addFence(e: EdgeRef, type: Int, hp: Int, flags: Int, w: World) {
        val th = 0.12f
        val height = when (type) { Fence.LIGHT -> 0.7f; Fence.MEDIUM -> 0.95f; Fence.HEAVY -> 1.35f; else -> 1.1f }
        var col = cFence[type]
        val gate = flags and EdgeFlag.GATE != 0
        val open = flags and EdgeFlag.OPEN != 0
        if (gate) col = cGate
        if (type == Fence.ELECTRIC) col = if (w.grid.edgePowered(e)) 0xFF6EC6FF.toInt() else 0xFF5A6E7A.toInt()
        if (hp > 0 && hp < Fence.hp[type] / 2) col = shade(col, 0.75f)
        val x = e.x.toFloat(); val y = e.y.toFloat()
        val gz = edgeZ(w.s, e)
        val o = if (e.h) obj(x, y - th / 2f, 1f, th) else obj(x - th / 2f, y, th, 1f)
        val broken = hp <= 0
        if (broken || open) {
            // solo postes
            val hh = if (open) height * 0.6f else height * 0.5f
            if (e.h) { o.add(RBox(x, y - th / 2f, gz, th, th, hh, col)); o.add(RBox(x + 1f - th, y - th / 2f, gz, th, th, hh, col)) }
            else { o.add(RBox(x - th / 2f, y, gz, th, th, hh, col)); o.add(RBox(x - th / 2f, y + 1f - th, gz, th, th, hh, col)) }
        } else {
            if (e.h) o.add(RBox(x, y - th / 2f, gz, 1f, th, height, col)) else o.add(RBox(x - th / 2f, y, gz, th, 1f, height, col))
            if (type == Fence.HEAVY || type == Fence.MEDIUM) {
                // travesaño superior más oscuro
                if (e.h) o.add(RBox(x, y - th, gz + height, 1f, th * 2f, 0.06f, shade(col, 0.8f))) else o.add(RBox(x - th, y, gz + height, th * 2f, 1f, 0.06f, shade(col, 0.8f)))
            }
        }
        if (selectedEdge == e) o.add(RBox(if (e.h) x else x - 0.2f, if (e.h) y - 0.2f else y, gz + height + 0.15f, if (e.h) 1f else 0.4f, if (e.h) 0.4f else 1f, 0.05f, Color.WHITE, 200))
    }

    private val cWhite = 0xFFF4F1EA.toInt()
    private val cDark = 0xFF2B2F33.toInt()
    private val cWood = 0xFF8C5A2B.toInt()
    private val cGlass = 0xFF9CD3E8.toInt()
    private val cRed = 0xFFD9483B.toInt()
    private val cYellow = 0xFFF2C14E.toInt()
    private val cMetal = 0xFF6E7F8C.toInt()

    private fun addBuilding(b: Building, selected: Boolean, time: Float, gz: Float) {
        val def = b.def
        val x = b.x.toFloat(); val y = b.y.toFloat(); val w = b.w.toFloat(); val h = b.h.toFloat()
        val o = obj(x, y, w, h)
        var col = def.color
        val unpowered = def.needsPower && !b.powered
        if (unpowered) col = shade(col, 0.6f)
        val H = def.height
        // todas las cajas del modelo se apoyan en la cota del suelo (los edificios exigen terreno llano)
        fun box(bx: Float, by: Float, bz: Float, bw: Float, bd: Float, bh: Float, c: Int, a: Int = 255) = o.add(RBox(bx, by, bz + gz, bw, bd, bh, c, a))
        when (def.id) {
            "feeder_herb", "feeder_carn" -> {
                box(x + 0.15f, y + 0.15f, 0f, 0.7f, 0.7f, 0.25f, shade(col, 0.8f))
                if (b.stock > 0) box(x + 0.25f, y + 0.25f, 0.25f, 0.5f, 0.5f, 0.2f * b.stock / def.stock.coerceAtLeast(1), if (def.feederDiet == Diet.HERBIVORE) 0xFF7BC96F.toInt() else cRed)
            }
            "water_trough" -> {
                box(x + 0.1f, y + 0.1f, 0f, 0.8f, 0.8f, 0.25f, 0xFF8A7A5A.toInt())
                box(x + 0.18f, y + 0.18f, 0.25f, 0.64f, 0.64f, 0.02f, col)
            }
            "dino_shelter" -> {   // cobertizo abierto: cuatro postes y techo
                for ((px, py) in listOf(0.1f to 0.1f, w - 0.3f to 0.1f, 0.1f to h - 0.3f, w - 0.3f to h - 0.3f)) box(x + px, y + py, 0f, 0.2f, 0.2f, H, cWood)
                box(x, y, H, w, h, 0.18f, col)
                box(x + 0.2f, y + 0.2f, H + 0.18f, w - 0.4f, h - 0.4f, 0.12f, shade(col, 0.85f))
            }
            "viewpoint" -> {      // torre mirador con barandilla
                for ((px, py) in listOf(0.15f to 0.15f, 0.7f to 0.15f, 0.15f to 0.7f, 0.7f to 0.7f)) box(x + px, y + py, 0f, 0.15f, 0.15f, H, cWood)
                box(x + 0.05f, y + 0.05f, H, 0.9f, 0.9f, 0.12f, col)
                box(x + 0.05f, y + 0.05f, H + 0.12f, 0.9f, 0.06f, 0.3f, shade(col, 0.8f)); box(x + 0.05f, y + 0.89f, H + 0.12f, 0.9f, 0.06f, 0.3f, shade(col, 0.8f))
                box(x + 0.05f, y + 0.05f, H + 0.12f, 0.06f, 0.9f, 0.3f, shade(col, 0.8f)); box(x + 0.89f, y + 0.05f, H + 0.12f, 0.06f, 0.9f, 0.3f, shade(col, 0.8f))
                box(x + 0.2f, y + 0.2f, H + 0.42f, 0.6f, 0.6f, 0.1f, cWood, 200)
            }
            "gallery" -> {        // galería elevada: plataforma por encima de la valla pesada, con techo y escalera
                val deck = 1.5f
                for (i in 0 until w.toInt()) { box(x + i + 0.08f, y + 0.08f, 0f, 0.16f, 0.16f, deck, cWood); box(x + i + 0.08f, y + h - 0.24f, 0f, 0.16f, 0.16f, deck, cWood); box(x + i + 0.84f, y + 0.08f, 0f, 0.16f, 0.16f, deck, cWood); box(x + i + 0.84f, y + h - 0.24f, 0f, 0.16f, 0.16f, deck, cWood) }
                box(x, y, deck, w, h, 0.12f, col)                                                   // plataforma
                box(x, y, deck + 0.12f, w, 0.06f, 0.35f, shade(col, 0.8f)); box(x, y + h - 0.06f, deck + 0.12f, w, 0.06f, 0.35f, shade(col, 0.8f))   // barandillas
                box(x, y, deck + 0.12f, 0.06f, h, 0.35f, shade(col, 0.8f)); box(x + w - 0.06f, y, deck + 0.12f, 0.06f, h, 0.35f, shade(col, 0.8f))
                for (i in 0 until w.toInt()) { box(x + i + 0.1f, y + 0.1f, deck + 0.12f, 0.1f, 0.1f, 0.8f, cWood); box(x + i + 0.1f, y + h - 0.2f, deck + 0.12f, 0.1f, 0.1f, 0.8f, cWood) }
                box(x - 0.05f, y - 0.05f, deck + 0.92f, w + 0.1f, h + 0.1f, 0.14f, cWood)         // techo
                for (k in 0 until 4) box(x + w / 2f - 0.3f, y + h + k * 0.2f, k * (deck / 4f), 0.6f, 0.2f, deck / 4f, shade(col, 0.9f))   // escalera hacia el camino
            }
            "shop_food" -> {      // quiosco con toldo a rayas y cartel
                box(x + 0.15f, y + 0.15f, 0f, w - 0.3f, h - 0.3f, H * 0.7f, col)
                for (i in 0 until 4) box(x + i * 0.5f, y + h - 0.35f, H * 0.7f, 0.5f, 0.45f, 0.08f, if (i % 2 == 0) cWhite else cRed)   // toldo
                box(x + 0.3f, y + 0.3f, H * 0.7f, w - 0.6f, h - 0.6f, 0.12f, shade(col, 0.8f))
                box(x + 0.6f, y + 0.4f, H * 0.82f, 0.8f, 0.15f, 0.45f, cRed); box(x + 0.7f, y + 0.36f, H * 0.95f, 0.6f, 0.05f, 0.2f, cWhite)   // cartel
            }
            "shop_drink" -> {     // quiosco con vaso gigante y pajita
                box(x + 0.15f, y + 0.15f, 0f, w - 0.3f, h - 0.3f, H * 0.7f, col)
                box(x + 0.3f, y + 0.3f, H * 0.7f, w - 0.6f, h - 0.6f, 0.1f, shade(col, 0.8f))
                box(x + 0.65f, y + 0.65f, H * 0.8f, 0.7f, 0.7f, 0.6f, cWhite); box(x + 0.6f, y + 0.6f, H * 0.8f + 0.6f, 0.8f, 0.8f, 0.08f, cRed)
                box(x + 1.05f, y + 0.8f, H * 0.8f + 0.68f, 0.08f, 0.08f, 0.35f, cRed)
                box(x, y + h - 0.3f, H * 0.5f, w, 0.3f, 0.06f, cWhite)   // mostrador
            }
            "shop_gift" -> {      // carpa escalonada de dos colores
                box(x + 0.1f, y + 0.1f, 0f, w - 0.2f, h - 0.2f, H * 0.45f, shade(col, 0.85f))
                box(x + 0.0f, y + 0.0f, H * 0.45f, w, h, 0.2f, col)
                box(x + 0.35f, y + 0.35f, H * 0.45f + 0.2f, w - 0.7f, h - 0.7f, 0.3f, cWhite)
                box(x + 0.7f, y + 0.7f, H * 0.45f + 0.5f, w - 1.4f, h - 1.4f, 0.3f, col)
                box(x + 0.95f, y + 0.95f, H * 0.45f + 0.8f, 0.1f, 0.1f, 0.35f, cDark); box(x + 0.9f, y + 1.0f, H * 0.45f + 1.05f, 0.35f, 0.04f, 0.15f, cRed)   // banderín
            }
            "toilets" -> {        // caseta con puerta y letrero
                box(x + 0.1f, y + 0.1f, 0f, 0.8f, 0.8f, H, col)
                box(x + 0.05f, y + 0.05f, H, 0.9f, 0.9f, 0.1f, shade(col, 0.75f))
                box(x + 0.35f, y + 0.88f, 0f, 0.3f, 0.04f, 0.6f, cDark)
                box(x + 0.15f, y + 0.9f, 0.7f, 0.25f, 0.03f, 0.2f, 0xFF4FA3D9.toInt()); box(x + 0.6f, y + 0.9f, 0.7f, 0.25f, 0.03f, 0.2f, 0xFFC75BA0.toInt())
            }
            "hotel_small", "hotel_large" -> {   // bloque con ventanas y azotea
                val floors = if (def.id == "hotel_small") 2 else 3
                val fh = H / floors
                box(x + 0.1f, y + 0.1f, 0f, w - 0.2f, h - 0.2f, H, col)
                for (f in 0 until floors) for (i in 0 until w.toInt()) {
                    box(x + i + 0.3f, y + h - 0.12f, f * fh + fh * 0.35f, 0.4f, 0.04f, fh * 0.4f, cGlass)
                    box(x + w - 0.12f, y + i + 0.3f, f * fh + fh * 0.35f, 0.04f, 0.4f, fh * 0.4f, cGlass)
                }
                box(x, y, H, w, h, 0.1f, shade(col, 0.7f))
                if (def.id == "hotel_large") { box(x + 0.4f, y + 0.4f, H + 0.1f, 1.4f, 1.0f, 0.06f, 0xFF4FA3D9.toInt()); box(x + 2.1f, y + 2.1f, H + 0.1f, 0.6f, 0.6f, 0.5f, shade(col, 0.85f)) }
                else box(x + 2.0f, y + 0.4f, H + 0.1f, 0.6f, 0.6f, 0.4f, cMetal)
                box(x + 0.4f, y + h - 0.06f, 0f, 0.6f, 0.06f, 0.7f, cDark)   // puerta
            }
            "visitor_shelter" -> {   // búnker bajo con cruz
                box(x + 0.05f, y + 0.05f, 0f, w - 0.1f, h - 0.1f, H * 0.7f, col)
                box(x + 0.25f, y + 0.25f, H * 0.7f, w - 0.5f, h - 0.5f, H * 0.3f, shade(col, 0.85f))
                box(x + 0.6f, y + h - 0.04f, H * 0.4f, 0.8f, 0.04f, 0.15f, cWhite); box(x + 0.92f, y + h - 0.04f, H * 0.25f, 0.16f, 0.04f, 0.45f, cWhite)
                box(x + 0.7f, y + 0.1f, 0f, 0.6f, 0.15f, H * 0.5f, cDark)   // entrada
            }
            "generator" -> {     // depósito cilíndrico, dos chimeneas y franja de aviso
                box(x + 0.1f, y + 0.1f, 0f, w - 0.2f, h - 0.2f, 0.25f, cMetal)
                box(x + 0.25f, y + 0.25f, 0.25f, 1.5f, 1.5f, 0.9f, col); box(x + 0.4f, y + 0.4f, 1.15f, 1.2f, 1.2f, 0.25f, col); box(x + 0.6f, y + 0.6f, 1.4f, 0.8f, 0.8f, 0.15f, shade(col, 0.85f))
                box(x + 0.15f, y + 1.55f, 0.25f, 0.2f, 0.2f, 1.7f, cDark); box(x + 0.5f, y + 1.55f, 0.25f, 0.2f, 0.2f, 1.4f, cDark)
                for (i in 0 until 6) box(x + 0.1f + i * 0.3f, y + h - 0.12f, 0.1f, 0.3f, 0.02f, 0.12f, if (i % 2 == 0) cYellow else cDark)
                if (!unpowered) { val p = 0.5f + 0.5f * sin(time * 6f); box(x + 0.2f, y + 1.6f, 1.95f + p * 0.3f, 0.12f, 0.12f, 0.12f, 0xFFB0B8BE.toInt(), 120) }
            }
            "expedition_hq" -> {   // hangar con techo escalonado, mástil y todoterreno
                box(x + 0.1f, y + 0.4f, 0f, w - 0.2f, h - 0.5f, H * 0.6f, col)
                box(x + 0.3f, y + 0.55f, H * 0.6f, w - 0.6f, h - 0.8f, H * 0.25f, shade(col, 0.9f))
                box(x + 0.6f, y + 0.75f, H * 0.85f, w - 1.2f, h - 1.2f, H * 0.15f, shade(col, 0.8f))
                box(x + w - 0.35f, y + 0.5f, H, 0.08f, 0.08f, 1.2f, cDark); box(x + w - 0.5f, y + 0.5f, H + 1.0f, 0.4f, 0.04f, 0.2f, cRed)
                box(x + 0.4f, y + 0.02f, 0f, 0.9f, 0.4f, 0.3f, 0xFF5F8F4E.toInt()); box(x + 0.6f, y + 0.06f, 0.3f, 0.5f, 0.32f, 0.2f, cGlass)   // jeep
                box(x + 1.1f, y + h - 0.12f, 0f, 0.8f, 0.12f, H * 0.5f, cDark)   // portón
            }
            "lab" -> {             // laboratorio blanco con cúpula y tubos de incubación
                box(x + 0.1f, y + 0.1f, 0f, w - 0.2f, h - 0.2f, H * 0.6f, col)
                box(x + 0.6f, y + 0.6f, H * 0.6f, 1.8f, 1.8f, 0.3f, cGlass); box(x + 0.9f, y + 0.9f, H * 0.6f + 0.3f, 1.2f, 1.2f, 0.3f, cGlass); box(x + 1.2f, y + 1.2f, H * 0.6f + 0.6f, 0.6f, 0.6f, 0.25f, cGlass)
                for (i in 0 until 3) box(x + 0.25f + i * 0.4f, y + h - 0.45f, H * 0.6f, 0.25f, 0.25f, 0.55f, 0xFF7BE07B.toInt(), 200)
                box(x + w - 0.4f, y + h - 0.6f, H * 0.6f, 0.3f, 0.5f, 0.35f, cMetal)
                box(x + 1.1f, y + h - 0.12f, 0f, 0.8f, 0.12f, H * 0.4f, cGlass)   // entrada acristalada
            }
            "research_center" -> { // bloque acristalado con antena parabólica
                box(x + 0.1f, y + 0.1f, 0f, w - 0.2f, h - 0.2f, H * 0.55f, col)
                box(x + 0.5f, y + 0.5f, H * 0.55f, w - 1.0f, h - 1.0f, H * 0.35f, cGlass)
                box(x + 0.1f, y + 0.1f, H * 0.55f, w - 0.2f, h - 0.2f, 0.08f, shade(col, 0.7f))
                box(x + w - 0.6f, y + 0.4f, H * 0.9f, 0.1f, 0.1f, 0.5f, cDark); box(x + w - 1.0f, y + 0.1f, H * 0.9f + 0.4f, 0.9f, 0.7f, 0.1f, cWhite); box(x + w - 0.65f, y + 0.35f, H * 0.9f + 0.5f, 0.2f, 0.2f, 0.3f, cDark)
                for (i in 0 until 3) box(x + 0.3f + i * 0.9f, y + h - 0.12f, 0.3f, 0.6f, 0.04f, H * 0.3f, cGlass)
            }
            "ranger_station" -> {  // torre de vigilancia de madera, cabaña y helipuerto
                for ((px, py) in listOf(0.2f to 0.2f, 1.0f to 0.2f, 0.2f to 1.0f, 1.0f to 1.0f)) box(x + px, y + py, 0f, 0.15f, 0.15f, H * 0.9f, cWood)
                box(x + 0.1f, y + 0.1f, H * 0.9f, 1.15f, 1.15f, 0.12f, cWood); box(x + 0.25f, y + 0.25f, H * 0.9f + 0.12f, 0.85f, 0.85f, 0.5f, col); box(x + 0.05f, y + 0.05f, H * 0.9f + 0.62f, 1.25f, 1.25f, 0.12f, shade(col, 0.7f))
                box(x + 1.5f, y + 0.2f, 0f, 1.3f, 1.1f, 0.7f, col); box(x + 1.4f, y + 0.1f, 0.7f, 1.5f, 1.3f, 0.12f, shade(col, 0.7f))   // cabaña
                box(x + 1.3f, y + 1.5f, 0f, 1.5f, 1.4f, 0.06f, cDark); box(x + 1.5f, y + 1.7f, 0.06f, 1.1f, 1.0f, 0.02f, cWhite); box(x + 1.7f, y + 1.9f, 0.08f, 0.7f, 0.6f, 0.02f, cDark)   // helipuerto
            }
            "entrance" -> {
                box(x, y, 0f, w, h, 0.2f, col)
                box(x + 0.1f, y + 0.1f, 0.2f, 0.4f, 0.4f, H, shade(col, 0.85f))
                box(x + w - 0.5f, y + 0.1f, 0.2f, 0.4f, 0.4f, H, shade(col, 0.85f))
                box(x, y, H + 0.2f, w, 0.6f, 0.25f, cWood)
                box(x + 0.6f, y + 0.9f, 0.2f, w - 1.2f, 1f, 0.5f, 0xFFDDE8EE.toInt())
            }
            else -> {
                box(x + 0.08f, y + 0.08f, 0f, w - 0.16f, h - 0.16f, H, col)
                box(x + 0.25f, y + 0.25f, H, w - 0.5f, h - 0.5f, 0.2f, shade(col, 0.8f))
            }
        }
        if (unpowered) box(x + w / 2f - 0.15f, y + h / 2f - 0.15f, H + 0.7f + 0.1f * sin(time * 3f), 0.3f, 0.3f, 0.3f, cYellow)
        if (def.feederDiet != null && b.stock <= 0) box(x + 0.35f, y + 0.35f, 0.9f + 0.1f * sin(time * 3f), 0.3f, 0.3f, 0.3f, cRed)
        if (selected) box(x - 0.1f, y - 0.1f, 0.02f, w + 0.2f, h + 0.2f, 0f, Color.WHITE, 120)
    }

    private fun addDino(d: Dino, time: Float, gz: Float) {
        val def = d.def
        val scale = DinoModels.scaleOf(def.size)
        val o = obj(d.x - 0.6f * scale, d.y - 0.6f * scale, 1.2f * scale, 1.2f * scale, 0.3f)
        val top = DinoModels.build(d) { x, y, z, w, dd, h, color -> o.add(RBox(x, y, z + gz, w, dd, h, color)) } + gz
        // burbujas de estado
        val cx = d.x; val cy = d.y
        val bz = top + 0.3f + 0.08f * sin(time * 4f)
        val bs = 0.3f
        when {
            d.state == DinoState.ESCAPED -> if ((time * 4f).toInt() % 2 == 0) o.add(RBox(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFFD9483B.toInt()))
            d.sleep > 0f -> o.add(RBox(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFF9AA5B1.toInt()))
            d.sick -> o.add(RBox(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFF7BE07B.toInt()))
            d.stress >= 75f -> if ((time * 4f).toInt() % 2 == 0) o.add(RBox(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFFD9483B.toInt()))
            d.stress >= 40f -> o.add(RBox(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFFF2C14E.toInt()))
        }
        // sombra plana y anillo de selección
        o.addRaw(RBox(cx - 0.45f * scale, cy - 0.35f * scale, gz + 0.005f, 0.9f * scale, 0.7f * scale, 0f, 0xFF000000.toInt(), 55))
        if (d.id == selectedDino) o.add(RBox(cx - 0.7f * scale, cy - 0.7f * scale, gz + 0.01f, 1.4f * scale, 1.4f * scale, 0f, Color.WHITE, 130))
    }

    private fun addVisitor(v: Visitor, gz: Float) {
        val hop = gz + (if (v.path.isNotEmpty()) abs(sin(v.hop)) * (if (v.state == VisitorState.FLEE) 0.16f else 0.08f) else 0f)
        val o = obj(v.x - 0.15f, v.y - 0.15f, 0.3f, 0.3f, 0.2f)
        o.add(RBox(v.x - 0.09f, v.y - 0.09f, hop, 0.18f, 0.18f, 0.18f, cLegs))
        o.add(RBox(v.x - 0.12f, v.y - 0.12f, 0.18f + hop, 0.24f, 0.24f, 0.28f, v.color))
        o.add(RBox(v.x - 0.1f, v.y - 0.1f, 0.46f + hop, 0.2f, 0.2f, 0.2f, cSkin))
        if (v.state == VisitorState.FLEE) {
            // huida legible: signo de exclamación rojo sobre la cabeza y brazos en alto
            o.add(RBox(v.x - 0.05f, v.y - 0.05f, 0.9f + hop, 0.1f, 0.1f, 0.22f, 0xFFE85A4D.toInt()))
            o.add(RBox(v.x - 0.05f, v.y - 0.05f, 0.8f + hop, 0.1f, 0.1f, 0.06f, 0xFFE85A4D.toInt()))
            o.add(RBox(v.x - 0.2f, v.y - 0.04f, 0.4f + hop, 0.08f, 0.08f, 0.3f, cSkin))
            o.add(RBox(v.x + 0.12f, v.y - 0.04f, 0.4f + hop, 0.08f, 0.08f, 0.3f, cSkin))
        } else if (v.comfort() < 40f) o.add(RBox(v.x - 0.08f, v.y - 0.08f, gz + 0.85f, 0.16f, 0.16f, 0.16f, 0xFF9AA5B1.toInt()))
    }

    // ------------------------------------------------------------------ superposiciones
    /** Rombo de un tile de mundo justo por encima de su suelo. */
    private fun tileDiamond(s: GameState, x: Int, y: Int, lift: Float) {
        val z = s.groundZ(x, y) + lift
        val x0 = x.toFloat(); val y0 = y.toFloat()
        path.rewind()
        path.moveTo(cam.worldSx(x0, y0), cam.worldSy(x0, y0, z))
        path.lineTo(cam.worldSx(x0 + 1f, y0), cam.worldSy(x0 + 1f, y0, z))
        path.lineTo(cam.worldSx(x0 + 1f, y0 + 1f), cam.worldSy(x0 + 1f, y0 + 1f, z))
        path.lineTo(cam.worldSx(x0, y0 + 1f), cam.worldSy(x0, y0 + 1f, z))
        path.close()
    }

    private fun drawOverlays(c: Canvas, w: World) {
        val s = w.s
        // regiones
        if (showRegions || highlightRegion > 0) {
            val b = visibleTileBounds(s.size)
            for (y in b[1]..b[3]) for (x in b[0]..b[2]) {
                if (!cam.visible(x + 0.5f, y + 0.5f, 1.5f)) continue
                val r = w.grid.region[s.idx(x, y)]
                if (r > 0 && (showRegions || r == highlightRegion)) {
                    tileDiamond(s, x, y, 0.01f)
                    paint.color = if (r == highlightRegion) 0x66FFFFFF else 0x2EFFFFFF
                    c.drawPath(path, paint)
                }
            }
        }
        // plano de caminos pendiente: rombos translúcidos
        if (planFenceType == 0 && planKeys.isNotEmpty()) {
            val n = w.n; val keys = planKeys; val ok = planOk
            for (i in keys.indices) {
                val x0 = keys[i] % n; val y0 = keys[i] / n
                if (!s.inBounds(x0, y0) || !onScreen(x0 + 0.5f, y0 + 0.5f, 1f)) continue
                tileDiamond(s, x0, y0, 0.04f)
                paint.color = if (i < ok.size && ok[i]) 0x997BE07B.toInt() else 0x99E06060.toInt()
                c.drawPath(path, paint)
            }
        }
        // pincel: círculo que sigue el relieve
        brush?.let { b ->
            val cx = b[0]; val cy = b[1]; val r = b[2]
            path.rewind()
            for (k in 0 until 24) {
                val a = k * (Math.PI * 2 / 24)
                val wx = cx + r * Math.cos(a).toFloat(); val wy = cy + r * Math.sin(a).toFloat()
                val z = groundZAt(s, wx, wy) + 0.05f
                if (k == 0) path.moveTo(cam.worldSx(wx, wy), cam.worldSy(wx, wy, z)) else path.lineTo(cam.worldSx(wx, wy), cam.worldSy(wx, wy, z))
            }
            path.close()
            paint.color = 0x33FFFFFF; c.drawPath(path, paint)
            linePaint.color = 0xFFFFFFFF.toInt(); linePaint.strokeWidth = 3f
            c.drawPath(path, linePaint)
        }
    }

    private fun drawStorm(c: Canvas, time: Float) {
        paint.color = 0x55101820
        c.drawRect(0f, 0f, cam.screenW, cam.screenH, paint)
        // relámpago breve cada ~6 s
        val cycle = time % 6.3f
        if (cycle < 0.12f) { paint.color = 0x66FFFFFF; c.drawRect(0f, 0f, cam.screenW, cam.screenH, paint) }
        linePaint.color = 0x88C8DCEB.toInt(); linePaint.strokeWidth = 2f
        val seed = (time * 20f).toInt()
        for (i in 0 until 70) {
            val rx = ((i * 7919 + seed * 31) % 1000) / 1000f * cam.screenW
            val ry = ((i * 104729 + seed * 97) % 1000) / 1000f * cam.screenH
            c.drawLine(rx, ry, rx - 6f, ry + 18f, linePaint)
        }
    }
}
