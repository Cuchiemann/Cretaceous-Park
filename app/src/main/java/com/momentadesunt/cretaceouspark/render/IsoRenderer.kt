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

/** Dibuja el mundo como cajas isométricas de color plano con tres tonos. */
class IsoRenderer(val cam: IsoCamera) {
    private val paint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val linePaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true; strokeWidth = 3f }
    private val path = Path()

    private class Box(val x: Float, val y: Float, val z: Float, val w: Float, val d: Float, val h: Float, val color: Int, val alpha: Int = 255) {
        // límites en espacio de vista (se rellenan en prepare)
        var vx0 = 0f; var vx1 = 0f; var vy0 = 0f; var vy1 = 0f
        var key = 0f
    }
    private class Obj(val key: Float) { val boxes = ArrayList<Box>(6) }

    private val objs = ArrayList<Obj>(1024)
    private val flat = ArrayList<Box>(4096)
    private val groundPaths = HashMap<Int, Path>()

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
    var rectPreview: IntArray? = null   // x0,y0,x1,y1
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
        val n = s.size
        cam.prepare()
        c.drawColor(cSea)
        drawGround(c, s, w)
        collectObjects(w, time)
        lastObjCount = objs.size
        sortBoxes()
        for (b in flat) drawBox(c, b)
        drawOverlays(c, w)
        if (w.stormActive) drawStorm(c, time)
    }

    /**
     * Orden de pintor por caja. Primero una clave aproximada (esquina cercana + altura);
     * después una pasada local que corrige pares mal ordenados con una prueba de separación:
     * A va detrás de B si A termina antes de que empiece B en x, en y o en altura, y no al revés.
     */
    private fun sortBoxes() {
        flat.clear()
        for (o in objs) for (b in o.boxes) {
            val vxA = cam.toViewX(b.x, b.y); val vyA = cam.toViewY(b.x, b.y)
            val vxB = cam.toViewX(b.x + b.w, b.y + b.d); val vyB = cam.toViewY(b.x + b.w, b.y + b.d)
            b.vx0 = min(vxA, vxB); b.vx1 = max(vxA, vxB); b.vy0 = min(vyA, vyB); b.vy1 = max(vyA, vyB)
            b.key = b.vx1 + b.vy1 + b.z * 0.02f + o.key * 0.0001f
            flat.add(b)
        }
        flat.sortBy { it.key }
        val n = flat.size
        val window = 14
        repeat(3) {
            var swapped = false
            for (i in 0 until n - 1) {
                val a = flat[i]
                val end = min(n - 1, i + window)
                for (j in i + 1..end) {
                    val b = flat[j]
                    // b está claramente detrás de a → debería ir antes
                    if (behind(b, a) && !behind(a, b)) { flat[j] = a; flat[i] = b; swapped = true; break }
                }
            }
            if (!swapped) return
        }
    }

    private fun behind(a: Box, b: Box): Boolean {
        val eps = 0.02f
        return a.vx1 <= b.vx0 + eps || a.vy1 <= b.vy0 + eps || a.z + a.h <= b.z + eps
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

    private fun groundPath(color: Int): Path = groundPaths.getOrPut(color) { Path() }

    private fun addQuad(p: Path, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        p.moveTo(x0, y0); p.lineTo(x1, y1); p.lineTo(x2, y2); p.lineTo(x3, y3); p.close()
    }

    /** Añade la cara superior de un tile a un path por color. */
    private fun addTop(p: Path, x: Float, y: Float, z: Float, w: Float, d: Float) {
        val ax = cam.worldSx(x, y); val ay = cam.worldSy(x, y, z)
        val bx = cam.worldSx(x + w, y); val by = cam.worldSy(x + w, y, z)
        val cx = cam.worldSx(x + w, y + d); val cy = cam.worldSy(x + w, y + d, z)
        val dx = cam.worldSx(x, y + d); val dy = cam.worldSy(x, y + d, z)
        addQuad(p, ax, ay, bx, by, cx, cy, dx, dy)
    }

    private fun drawGround(c: Canvas, s: GameState, w: World) {
        for (p in groundPaths.values) p.rewind()
        val n = s.size
        val b = visibleTileBounds(n)
        val cliffL = shade(cCliff, 0.85f); val cliffR = shade(cCliff, 0.7f)
        val waterSideL = shade(cWater, 0.85f); val waterSideR = shade(cWater, 0.7f)
        for (y in b[1]..b[3]) for (x in b[0]..b[2]) {
            if (!cam.visible(x + 0.5f, y + 0.5f, 1.5f)) continue
            val t = s.terrain[s.idx(x, y)]
            val col = when (t) {
                Terrain.GRASS, Terrain.FOREST -> if ((x + y) and 1 == 0) cGrass else cGrass2
                Terrain.SAND -> cSand
                Terrain.ROCK -> cRockGround
                Terrain.WATER -> cWater
                Terrain.PATH -> cPath
                else -> cBridge
            }
            val z = if (t == Terrain.WATER) -0.25f else 0f
            addTop(groundPath(col), x.toFloat(), y.toFloat(), z, 1f, 1f)
            if (t == Terrain.WATER) {
                // paredes del hueco de agua hacia el fondo (lado visible)
                addSideFaces(x.toFloat(), y.toFloat(), -0.25f, 1f, 1f, 0.25f, waterSideL, waterSideR, onlyInner = true, s = s)
            }
            // acantilados en el borde de la isla
            val edgeTile = x == 0 || y == 0 || x == n - 1 || y == n - 1
            if (edgeTile) addSideFaces(x.toFloat(), y.toFloat(), -0.6f, 1f, 1f, 0.6f, cliffL, cliffR, onlyInner = false, s = s, borderOnly = true)
        }
        for ((col, p) in groundPaths) { if (!p.isEmpty) { paint.color = col; c.drawPath(p, paint) } }
        // regiones
        if (showRegions || highlightRegion > 0) {
            for (y in b[1]..b[3]) for (x in b[0]..b[2]) {
                if (!cam.visible(x + 0.5f, y + 0.5f, 1.5f)) continue
                val r = w.grid.region[s.idx(x, y)]
                if (r > 0 && (showRegions || r == highlightRegion)) {
                    path.rewind(); addTop(path, x.toFloat(), y.toFloat(), 0.01f, 1f, 1f)
                    paint.color = if (r == highlightRegion) 0x66FFFFFF else 0x2EFFFFFF
                    c.drawPath(path, paint)
                }
            }
        }
    }

    /** Caras laterales visibles de una caja en el suelo, añadidas a los paths por color. */
    private fun addSideFaces(x: Float, y: Float, z: Float, w: Float, d: Float, h: Float, colL: Int, colR: Int, onlyInner: Boolean, s: GameState, borderOnly: Boolean = false) {
        // esquinas en vista
        val vx0 = min(cam.toViewX(x, y), cam.toViewX(x + w, y + d)); val vx1 = max(cam.toViewX(x, y), cam.toViewX(x + w, y + d))
        val vy0 = min(cam.toViewY(x, y), cam.toViewY(x + w, y + d)); val vy1 = max(cam.toViewY(x, y), cam.toViewY(x + w, y + d))
        // cara izquierda (vy = vy1) y derecha (vx = vx1)
        if (borderOnly) {
            // solo dibujar si el lado da al mar
            val (lx, ly) = Pair(cam.viewToWorldX(vx0 + 0.5f, vy1 + 0.5f), cam.viewToWorldY(vx0 + 0.5f, vy1 + 0.5f))
            if (!s.inBounds(floor(lx).toInt(), floor(ly).toInt())) {
                val p = groundPath(colL)
                addQuad(p, cam.sx(vx0, vy1), cam.sy(vx0, vy1, z + h), cam.sx(vx1, vy1), cam.sy(vx1, vy1, z + h), cam.sx(vx1, vy1), cam.sy(vx1, vy1, z), cam.sx(vx0, vy1), cam.sy(vx0, vy1, z))
            }
            val (rx, ry) = Pair(cam.viewToWorldX(vx1 + 0.5f, vy0 + 0.5f), cam.viewToWorldY(vx1 + 0.5f, vy0 + 0.5f))
            if (!s.inBounds(floor(rx).toInt(), floor(ry).toInt())) {
                val p = groundPath(colR)
                addQuad(p, cam.sx(vx1, vy0), cam.sy(vx1, vy0, z + h), cam.sx(vx1, vy1), cam.sy(vx1, vy1, z + h), cam.sx(vx1, vy1), cam.sy(vx1, vy1, z), cam.sx(vx1, vy0), cam.sy(vx1, vy0, z))
            }
            return
        }
        if (onlyInner) {
            // paredes del agua: las caras traseras (vy0 / vx0) son las visibles desde dentro del hueco
            val (lx, ly) = Pair(cam.viewToWorldX(vx0 - 0.5f, vy0 + 0.5f), cam.viewToWorldY(vx0 - 0.5f, vy0 + 0.5f))
            if (s.inBounds(floor(lx).toInt(), floor(ly).toInt()) && s.terrainAt(floor(lx).toInt(), floor(ly).toInt()) != Terrain.WATER) {
                val p = groundPath(colR)
                addQuad(p, cam.sx(vx0, vy0), cam.sy(vx0, vy0, z + h), cam.sx(vx0, vy1), cam.sy(vx0, vy1, z + h), cam.sx(vx0, vy1), cam.sy(vx0, vy1, z), cam.sx(vx0, vy0), cam.sy(vx0, vy0, z))
            }
            val (rx, ry) = Pair(cam.viewToWorldX(vx0 + 0.5f, vy0 - 0.5f), cam.viewToWorldY(vx0 + 0.5f, vy0 - 0.5f))
            if (s.inBounds(floor(rx).toInt(), floor(ry).toInt()) && s.terrainAt(floor(rx).toInt(), floor(ry).toInt()) != Terrain.WATER) {
                val p = groundPath(colL)
                addQuad(p, cam.sx(vx0, vy0), cam.sy(vx0, vy0, z + h), cam.sx(vx1, vy0), cam.sy(vx1, vy0, z + h), cam.sx(vx1, vy0), cam.sy(vx1, vy0, z), cam.sx(vx0, vy0), cam.sy(vx0, vy0, z))
            }
        }
    }

    // ------------------------------------------------------------------ cajas
    private fun drawBox(c: Canvas, b: Box) {
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

    private fun obj(x: Float, y: Float, w: Float = 1f, d: Float = 1f, zBias: Float = 0f): Obj {
        val vx = max(cam.toViewX(x, y), cam.toViewX(x + w, y + d))
        val vy = max(cam.toViewY(x, y), cam.toViewY(x + w, y + d))
        val o = Obj(vx + vy + zBias)
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
            if (t == Terrain.FOREST) {
                val h = 0.9f + ((x * 7 + y * 13) % 5) * 0.12f
                val o = obj(x.toFloat(), y.toFloat())
                o.boxes.add(Box(x + 0.4f, y + 0.4f, 0f, 0.2f, 0.2f, h * 0.45f, cTrunk))
                o.boxes.add(Box(x + 0.15f, y + 0.15f, h * 0.45f, 0.7f, 0.7f, h * 0.55f, if ((x + y) % 3 == 0) cLeaf2 else cLeaf))
                o.boxes.add(Box(x + 0.3f, y + 0.3f, h, 0.4f, 0.4f, 0.25f, cLeaf2))
            } else if (t == Terrain.ROCK) {
                val o = obj(x.toFloat(), y.toFloat())
                o.boxes.add(Box(x + 0.1f, y + 0.2f, 0f, 0.6f, 0.6f, 0.5f, cRock))
                o.boxes.add(Box(x + 0.5f, y + 0.55f, 0f, 0.4f, 0.35f, 0.3f, shade(cRock, 0.9f)))
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
            addBuilding(bd, i == selectedBuildingIndex(s), time)
        }
        // dinos
        for (d in s.dinos) if (onScreen(d.x, d.y, 4f)) addDino(d, time)
        // visitantes
        for (v in s.visitors) if (onScreen(v.x, v.y, 2f)) addVisitor(v)
        // fantasma
        ghost?.let { g ->
            val o = obj(g.x.toFloat(), g.y.toFloat(), g.w.toFloat(), g.h.toFloat(), 0.5f)
            o.boxes.add(Box(g.x.toFloat(), g.y.toFloat(), 0f, g.w.toFloat(), g.h.toFloat(), g.height, if (g.ok) 0xFF7BE07B.toInt() else 0xFFE06060.toInt(), 150))
        }
    }

    private fun selectedBuildingIndex(s: GameState): Int = if (selectedBuilding < 0) -1 else s.buildings.indexOfFirst { it.id == selectedBuilding }

    private fun addFence(e: EdgeRef, type: Int, hp: Int, flags: Int, w: World) {
        val th = 0.12f
        val height = when (type) { Fence.LIGHT -> 0.5f; Fence.MEDIUM -> 0.65f; Fence.HEAVY -> 0.9f; else -> 0.7f }
        var col = cFence[type]
        val gate = flags and EdgeFlag.GATE != 0
        val open = flags and EdgeFlag.OPEN != 0
        if (gate) col = cGate
        if (type == Fence.ELECTRIC) col = if (w.grid.edgePowered(e)) 0xFF6EC6FF.toInt() else 0xFF5A6E7A.toInt()
        if (hp > 0 && hp < Fence.hp[type] / 2) col = shade(col, 0.75f)
        val x = e.x.toFloat(); val y = e.y.toFloat()
        val o = if (e.h) obj(x, y - th / 2f, 1f, th) else obj(x - th / 2f, y, th, 1f)
        val broken = hp <= 0
        if (broken || open) {
            // solo postes
            val hh = if (open) height * 0.6f else height * 0.5f
            if (e.h) { o.boxes.add(Box(x, y - th / 2f, 0f, th, th, hh, col)); o.boxes.add(Box(x + 1f - th, y - th / 2f, 0f, th, th, hh, col)) }
            else { o.boxes.add(Box(x - th / 2f, y, 0f, th, th, hh, col)); o.boxes.add(Box(x - th / 2f, y + 1f - th, 0f, th, th, hh, col)) }
        } else {
            if (e.h) o.boxes.add(Box(x, y - th / 2f, 0f, 1f, th, height, col)) else o.boxes.add(Box(x - th / 2f, y, 0f, th, 1f, height, col))
            if (type == Fence.HEAVY || type == Fence.MEDIUM) {
                // travesaño superior más oscuro
                if (e.h) o.boxes.add(Box(x, y - th, height, 1f, th * 2f, 0.06f, shade(col, 0.8f))) else o.boxes.add(Box(x - th, y, height, th * 2f, 1f, 0.06f, shade(col, 0.8f)))
            }
        }
        if (selectedEdge == e) o.boxes.add(Box(if (e.h) x else x - 0.2f, if (e.h) y - 0.2f else y, height + 0.15f, if (e.h) 1f else 0.4f, if (e.h) 0.4f else 1f, 0.05f, Color.WHITE, 200))
    }

    private fun addBuilding(b: Building, selected: Boolean, time: Float) {
        val def = b.def
        val x = b.x.toFloat(); val y = b.y.toFloat(); val w = b.w.toFloat(); val h = b.h.toFloat()
        val o = obj(x, y, w, h)
        var col = def.color
        val unpowered = def.needsPower && !b.powered
        if (unpowered) col = shade(col, 0.6f)
        val H = def.height
        when (def.id) {
            "feeder_herb", "feeder_carn" -> {
                o.boxes.add(Box(x + 0.15f, y + 0.15f, 0f, 0.7f, 0.7f, 0.25f, shade(col, 0.8f)))
                if (b.stock > 0) o.boxes.add(Box(x + 0.25f, y + 0.25f, 0.25f, 0.5f, 0.5f, 0.2f * b.stock / def.stock.coerceAtLeast(1), if (def.feederDiet == Diet.HERBIVORE) 0xFF7BC96F.toInt() else 0xFFD9534F.toInt()))
            }
            "water_trough" -> {
                o.boxes.add(Box(x + 0.1f, y + 0.1f, 0f, 0.8f, 0.8f, 0.25f, 0xFF8A7A5A.toInt()))
                o.boxes.add(Box(x + 0.18f, y + 0.18f, 0.25f, 0.64f, 0.64f, 0.02f, col))
            }
            "viewpoint" -> {
                o.boxes.add(Box(x + 0.2f, y + 0.2f, 0f, 0.6f, 0.6f, H, col))
                o.boxes.add(Box(x + 0.05f, y + 0.05f, H, 0.9f, 0.9f, 0.15f, shade(col, 0.85f)))
                o.boxes.add(Box(x + 0.1f, y + 0.1f, H + 0.15f, 0.8f, 0.8f, 0.5f, 0xFF7E6A4A.toInt(), 120))
            }
            "gallery" -> {
                o.boxes.add(Box(x + 0.1f, y + 0.1f, 0f, w - 0.2f, h - 0.2f, H * 0.5f, col))
                o.boxes.add(Box(x, y, H * 0.5f, w, h, 0.12f, 0xFF7E6A4A.toInt()))
                o.boxes.add(Box(x + 0.1f, y + 0.1f, H * 0.5f + 0.12f, w - 0.2f, h - 0.2f, H * 0.5f, col, 110))
            }
            "generator" -> {
                o.boxes.add(Box(x + 0.1f, y + 0.1f, 0f, w - 0.2f, h - 0.2f, H * 0.6f, col))
                o.boxes.add(Box(x + 0.5f, y + 0.5f, H * 0.6f, 0.5f, 0.5f, H * 0.4f, 0xFF6E7F8C.toInt()))
                o.boxes.add(Box(x + 1.1f, y + 0.4f, H * 0.6f, 0.3f, 0.3f, H * 0.3f, 0xFF6E7F8C.toInt()))
            }
            "entrance" -> {
                o.boxes.add(Box(x, y, 0f, w, h, 0.2f, col))
                o.boxes.add(Box(x + 0.1f, y + 0.1f, 0.2f, 0.4f, 0.4f, H, shade(col, 0.85f)))
                o.boxes.add(Box(x + w - 0.5f, y + 0.1f, 0.2f, 0.4f, 0.4f, H, shade(col, 0.85f)))
                o.boxes.add(Box(x, y, H + 0.2f, w, 0.6f, 0.25f, 0xFF8C5A2B.toInt()))
                o.boxes.add(Box(x + 0.6f, y + 0.9f, 0.2f, w - 1.2f, 1f, 0.5f, 0xFFDDE8EE.toInt()))
            }
            else -> {
                o.boxes.add(Box(x + 0.08f, y + 0.08f, 0f, w - 0.16f, h - 0.16f, H, col))
                o.boxes.add(Box(x + 0.25f, y + 0.25f, H, w - 0.5f, h - 0.5f, 0.2f, shade(col, 0.8f)))
                if (def.category == Category.CENTERS) o.boxes.add(Box(x + w / 2f - 0.2f, y + h / 2f - 0.2f, H + 0.2f, 0.4f, 0.4f, 0.4f, shade(col, 0.7f)))
                if (def.beds > 0) for (i in 0 until w.toInt()) o.boxes.add(Box(x + i + 0.3f, y + 0.02f, H * 0.4f, 0.4f, 0.04f, 0.4f, 0xFF4FA3D9.toInt()))
            }
        }
        if (unpowered) o.boxes.add(Box(x + w / 2f - 0.15f, y + h / 2f - 0.15f, H + 0.7f + 0.1f * sin(time * 3f), 0.3f, 0.3f, 0.3f, 0xFFF2C14E.toInt()))
        if (def.feederDiet != null && b.stock <= 0) o.boxes.add(Box(x + 0.35f, y + 0.35f, 0.9f + 0.1f * sin(time * 3f), 0.3f, 0.3f, 0.3f, 0xFFD9483B.toInt()))
        if (selected) o.boxes.add(Box(x - 0.1f, y - 0.1f, 0.02f, w + 0.2f, h + 0.2f, 0f, Color.WHITE, 120))
    }

    private fun addDino(d: Dino, time: Float) {
        val def = d.def
        val scale = DinoModels.scaleOf(def.size)
        val o = obj(d.x - 0.6f * scale, d.y - 0.6f * scale, 1.2f * scale, 1.2f * scale, 0.3f)
        val top = DinoModels.build(d) { x, y, z, w, dd, h, color -> o.boxes.add(Box(x, y, z, w, dd, h, color)) }
        // burbujas de estado
        val cx = d.x; val cy = d.y
        val bz = top + 0.3f + 0.08f * sin(time * 4f)
        val bs = 0.3f
        when {
            d.state == DinoState.ESCAPED -> if ((time * 4f).toInt() % 2 == 0) o.boxes.add(Box(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFFD9483B.toInt()))
            d.sleep > 0f -> o.boxes.add(Box(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFF9AA5B1.toInt()))
            d.sick -> o.boxes.add(Box(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFF7BE07B.toInt()))
            d.stress >= 75f -> if ((time * 4f).toInt() % 2 == 0) o.boxes.add(Box(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFFD9483B.toInt()))
            d.stress >= 40f -> o.boxes.add(Box(cx - bs / 2f, cy - bs / 2f, bz, bs, bs, bs, 0xFFF2C14E.toInt()))
        }
        // sombra plana y anillo de selección
        o.boxes.add(0, Box(cx - 0.45f * scale, cy - 0.35f * scale, 0.005f, 0.9f * scale, 0.7f * scale, 0f, 0xFF000000.toInt(), 55))
        if (d.id == selectedDino) o.boxes.add(Box(cx - 0.7f * scale, cy - 0.7f * scale, 0.01f, 1.4f * scale, 1.4f * scale, 0f, Color.WHITE, 130))
    }

    private fun addVisitor(v: Visitor) {
        val hop = if (v.path.isNotEmpty()) abs(sin(v.hop)) * 0.08f else 0f
        val o = obj(v.x - 0.15f, v.y - 0.15f, 0.3f, 0.3f, 0.2f)
        o.boxes.add(Box(v.x - 0.09f, v.y - 0.09f, hop, 0.18f, 0.18f, 0.18f, cLegs))
        o.boxes.add(Box(v.x - 0.12f, v.y - 0.12f, 0.18f + hop, 0.24f, 0.24f, 0.28f, v.color))
        o.boxes.add(Box(v.x - 0.1f, v.y - 0.1f, 0.46f + hop, 0.2f, 0.2f, 0.2f, cSkin))
        if (v.comfort() < 40f) o.boxes.add(Box(v.x - 0.08f, v.y - 0.08f, 0.85f, 0.16f, 0.16f, 0.16f, 0xFF9AA5B1.toInt()))
    }

    // ------------------------------------------------------------------ superposiciones
    private fun drawOverlays(c: Canvas, w: World) {
        rectPreview?.let { r ->
            val x0 = min(r[0], r[2]).toFloat(); val y0 = min(r[1], r[3]).toFloat()
            val x1 = max(r[0], r[2]) + 1f; val y1 = max(r[1], r[3]) + 1f
            path.rewind()
            path.moveTo(cam.worldSx(x0, y0), cam.worldSy(x0, y0, 0.05f))
            path.lineTo(cam.worldSx(x1, y0), cam.worldSy(x1, y0, 0.05f))
            path.lineTo(cam.worldSx(x1, y1), cam.worldSy(x1, y1, 0.05f))
            path.lineTo(cam.worldSx(x0, y1), cam.worldSy(x0, y1, 0.05f))
            path.close()
            linePaint.color = 0xFFFFFFFF.toInt(); linePaint.strokeWidth = 4f
            c.drawPath(path, linePaint)
            paint.color = 0x33FFFFFF; c.drawPath(path, paint)
        }
    }

    private fun drawStorm(c: Canvas, time: Float) {
        paint.color = 0x55101820
        c.drawRect(0f, 0f, cam.screenW, cam.screenH, paint)
        linePaint.color = 0x88C8DCEB.toInt(); linePaint.strokeWidth = 2f
        val seed = (time * 20f).toInt()
        for (i in 0 until 70) {
            val rx = ((i * 7919 + seed * 31) % 1000) / 1000f * cam.screenW
            val ry = ((i * 104729 + seed * 97) % 1000) / 1000f * cam.screenH
            c.drawLine(rx, ry, rx - 6f, ry + 18f, linePaint)
        }
    }
}
