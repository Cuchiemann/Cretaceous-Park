package com.momentadesunt.cretaceouspark.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.momentadesunt.cretaceouspark.core.EdgeRef
import com.momentadesunt.cretaceouspark.core.GameData
import com.momentadesunt.cretaceouspark.core.Terrain
import com.momentadesunt.cretaceouspark.core.DinoState
import com.momentadesunt.cretaceouspark.ui.GameViewModel
import com.momentadesunt.cretaceouspark.ui.Selection
import com.momentadesunt.cretaceouspark.ui.Tool
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

/** Vista del mundo: bucle de simulación, render y gestos. */
@SuppressLint("ViewConstructor")
class GameView(context: Context, val vm: GameViewModel) : View(context) {
    val cam = IsoCamera(20)
    val renderer = IsoRenderer(cam)
    private var lastNanos = 0L
    private var acc = 0f
    private var uiAcc = 0f
    private var animTime = 0f
    private var camMap = -1

    private val choreo = Choreographer.getInstance()
    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastNanos != 0L) {
                val dt = min(0.1f, (frameTimeNanos - lastNanos) / 1e9f)
                step(dt)
            }
            lastNanos = frameTimeNanos
            invalidate()
            if (isAttachedToWindow && looping) choreo.postFrameCallback(this)
        }
    }

    private var looping = false
    private fun startLoop() { if (!looping) { looping = true; lastNanos = 0L; choreo.postFrameCallback(frameCb) } }
    private fun stopLoop() { looping = false; choreo.removeFrameCallback(frameCb) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (windowVisibility == VISIBLE) startLoop() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stopLoop() }
    /** En segundo plano no se simula ni se dibuja: sin sonidos ni consumo. */
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) startLoop() else stopLoop() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); cam.setViewport(w, h) }

    private fun step(dt: Float) {
        val world = vm.world ?: return
        if (camMap != world.s.size) { camMap = world.s.size; cam.mapSize = world.s.size; centerOnEntrance() }
        animTime += dt
        val speed = if (world.s.gameOver) 0 else world.s.speed   // los menús no detienen el parque
        acc += dt * speed
        var ticks = 0
        val ts0 = System.nanoTime()
        while (acc >= 0.1f && ticks < 6) { world.tick(0.1f); acc -= 0.1f; ticks++ }
        simAcc += System.nanoTime() - ts0
        if (acc > 0.6f) acc = 0f
        uiAcc += dt
        if (uiAcc >= 0.15f) { uiAcc = 0f; vm.onFrame() }
        if (world.saveRequested) { world.saveRequested = false; vm.save() }
        if (vm.pendingFocus != null) { val f = vm.pendingFocus!!; cam.focus(f.first, f.second); vm.pendingFocus = null }
    }

    /** Centra la camara en la entrada del parque. */
    fun centerOnEntrance() {
        val world = vm.world ?: return
        val e = world.s.buildings.firstOrNull { it.type == "entrance" }
        if (e != null) cam.focus(e.cx, e.cy - 4f) else cam.focus(world.s.size / 2f, world.s.size * 0.7f)
    }

    private var drawAcc = 0L; private var drawCount = 0; private var drawMax = 0L; private var simAcc = 0L
    // ------------------------------------------------------------------ minimapa
    private var miniBitmap: Bitmap? = null
    private var miniPixels: IntArray? = null
    private var miniRefresh = 0f
    private val miniRect = RectF()
    private val miniPaint = Paint().apply { isFilterBitmap = false }
    private val miniLine = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFFFFFFFF.toInt(); isAntiAlias = true }
    private val miniFill = Paint().apply { style = Paint.Style.FILL }
    private val miniPath = Path()

    private fun miniLayout() {
        val d = resources.displayMetrics.density
        val size = 96f * d
        val right = width - 12f * d
        val top = 58f * d
        miniRect.set(right - size, top, right, top + size)
    }

    private fun rebuildMinimap(world: com.momentadesunt.cretaceouspark.core.World) {
        val n = world.s.size
        var bmp = miniBitmap
        if (bmp == null || bmp.width != n) { bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888); miniBitmap = bmp; miniPixels = IntArray(n * n) }
        val px = miniPixels!!
        val s = world.s
        for (i in 0 until n * n) {
            val base = when (s.terrain[i]) {
                Terrain.GRASS -> 0xFF5FA85A.toInt()
                Terrain.SAND -> 0xFFE3C97A.toInt()
                Terrain.FOREST -> 0xFF2E6B3A.toInt()
                Terrain.ROCK -> 0xFF8B8F8A.toInt()
                Terrain.WATER -> 0xFF4FA3D9.toInt()
                else -> 0xFFB9AE95.toInt()
            }
            val lit = if (s.terrain[i] == Terrain.WATER) base else blend(base, 0xFFFFFFFF.toInt(), 0.12f * s.height[i])   // más claro cuanto más alto
            px[i] = if (world.grid.region[i] > 0) blend(lit, 0xFFFFFFFF.toInt(), 0.25f) else lit
        }
        for (b in s.buildings) for (yy in b.y until b.y + b.h) for (xx in b.x until b.x + b.w) if (s.inBounds(xx, yy)) px[s.idx(xx, yy)] = b.def.color
        bmp.setPixels(px, 0, n, 0, 0, n, n)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(sh: Int) = (((a shr sh) and 255) * (1 - t) + ((b shr sh) and 255) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun drawMinimap(c: Canvas, world: com.momentadesunt.cretaceouspark.core.World, dt: Float) {
        if (width == 0) return
        miniLayout()
        miniRefresh -= dt
        if (miniBitmap == null || world.dirty || miniRefresh <= 0f) { rebuildMinimap(world); world.dirty = false; miniRefresh = 2f }
        val bmp = miniBitmap ?: return
        val n = world.s.size
        miniFill.color = 0xAA0F1A13.toInt()
        c.drawRoundRect(miniRect.left - 4f, miniRect.top - 4f, miniRect.right + 4f, miniRect.bottom + 4f, 6f, 6f, miniFill)
        c.drawBitmap(bmp, null, miniRect, miniPaint)
        val sx = miniRect.width() / n; val sy = miniRect.height() / n
        // dinos y visitantes
        for (d in world.s.dinos) {
            miniFill.color = if (d.state == DinoState.ESCAPED) 0xFFE85A4D.toInt() else 0xFFFFFFFF.toInt()
            c.drawRect(miniRect.left + d.x * sx - 2f, miniRect.top + d.y * sy - 2f, miniRect.left + d.x * sx + 2f, miniRect.top + d.y * sy + 2f, miniFill)
        }
        // rombo de la camara: esquinas de pantalla a mundo
        miniPath.rewind()
        var first = true
        for ((px, py) in listOf(0f to 0f, cam.screenW to 0f, cam.screenW to cam.screenH, 0f to cam.screenH)) {
            val (wx, wy) = cam.screenToWorld(px, py)
            val mx = miniRect.left + wx.coerceIn(0f, n.toFloat()) * sx
            val my = miniRect.top + wy.coerceIn(0f, n.toFloat()) * sy
            if (first) { miniPath.moveTo(mx, my); first = false } else miniPath.lineTo(mx, my)
        }
        miniPath.close()
        c.drawPath(miniPath, miniLine)
    }

    private var lastDrawNanos = 0L
    override fun onDraw(canvas: Canvas) {
        val world = vm.world ?: return
        syncOverlayState()
        val t0 = System.nanoTime()
        renderer.draw(canvas, world, animTime)
        val now = System.nanoTime()
        val frameDt = if (lastDrawNanos == 0L) 0.016f else ((now - lastDrawNanos) / 1e9f).coerceAtMost(0.1f)
        lastDrawNanos = now
        drawMinimap(canvas, world, frameDt)
        val dtNs = System.nanoTime() - t0
        drawAcc += dtNs; drawCount++; if (dtNs > drawMax) drawMax = dtNs
        if (drawCount >= 60) {
            android.util.Log.d("CretaceousPerf", "draw avg ${drawAcc / drawCount / 1000} us, max ${drawMax / 1000} us, sim avg ${simAcc / drawCount / 1000} us, objs ${renderer.lastObjCount}, zoom ${cam.zoom}")
            drawAcc = 0; drawCount = 0; drawMax = 0; simAcc = 0
        }
    }

    private fun syncOverlayState() {
        val sel = vm.selection
        renderer.selectedDino = (sel as? Selection.DinoSel)?.id ?: -1
        renderer.selectedBuilding = (sel as? Selection.BuildingSel)?.id ?: -1
        renderer.selectedEdge = (sel as? Selection.EdgeSel)?.edge
        val t = vm.tool
        renderer.showRegions = t is Tool.FenceTool || t is Tool.Gate || (t is Tool.Build && GameData.building(t.defId).insideEnclosure)
        renderer.highlightRegion = vm.highlightRegion
        val g = vm.ghost
        renderer.ghost = if (g != null && t is Tool.Build) {
            val def = GameData.building(t.defId)
            IsoRenderer.Ghost(g.first, g.second, def.w, def.h, vm.ghostOk, def.height, def.color)
        } else null
        renderer.planKeys = vm.planKeys; renderer.planOk = vm.planOk
        renderer.planFenceType = (t as? Tool.FenceTool)?.type ?: 0
        renderer.brush = vm.brushAt
    }

    // ------------------------------------------------------------------ selección con relieve
    /** Punto de mundo bajo el píxel, sobre la superficie que realmente se ve (cimas altas incluidas). */
    private fun pickWorld(px: Float, py: Float): Pair<Float, Float> {
        val s = vm.world?.s ?: return cam.screenToWorld(px, py)
        return cam.pickWorld(px, py) { x, y -> if (s.inBounds(x, y)) s.groundZ(x, y) else null }
    }
    private fun pickTile(px: Float, py: Float): Pair<Int, Int> { val (wx, wy) = pickWorld(px, py); return Pair(floor(wx).toInt(), floor(wy).toInt()) }

    // ------------------------------------------------------------------ gestos
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f; private var downY = 0f; private var downTime = 0L
    private var lastX = 0f; private var lastY = 0f
    private var dragging = false
    private var painting = false
    private var movingGhost = false
    private var ghostOffX = 0; private var ghostOffY = 0
    private var multi = false
    private var lastCorner: Pair<Int, Int>? = null   // vértice anterior al dibujar vallas
    private var lastPaintTile = -1                    // tile anterior al pintar caminos / pincel
    private var scaleAcc = 1f
    private var miniDrag = false
    private val strokeCells = ArrayList<Int>()
    private val brushCells = HashSet<Int>()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleAcc *= detector.scaleFactor
            if (scaleAcc > 1.35f) { cam.zoomIn(); scaleAcc = 1f; vm.world?.cameraMoved = true }
            if (scaleAcc < 0.74f) { cam.zoomOut(); scaleAcc = 1f; vm.world?.cameraMoved = true }
            return true
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (miniRect.contains(e.x, e.y) && vm.world != null) {
                    val n = vm.world!!.s.size
                    cam.focus((e.x - miniRect.left) / miniRect.width() * n, (e.y - miniRect.top) / miniRect.height() * n)
                    miniDrag = true
                    return true
                }
                miniDrag = false
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; downTime = System.currentTimeMillis()
                dragging = false; painting = false; movingGhost = false; multi = false; scaleAcc = 1f
                lastCorner = null; lastPaintTile = -1
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> { multi = true; endStroke() }
            MotionEvent.ACTION_MOVE -> {
                if (miniDrag) {
                    val n = vm.world?.s?.size ?: return true
                    cam.focus(((e.x - miniRect.left) / miniRect.width() * n).coerceIn(0f, n.toFloat()), ((e.y - miniRect.top) / miniRect.height() * n).coerceIn(0f, n.toFloat()))
                    return true
                }
                if (multi) {
                    // desplazamiento con dos dedos: media del movimiento del primer puntero
                    val dx = e.getX(0) - lastX; val dy = e.getY(0) - lastY
                    if (e.pointerCount >= 2) cam.panBy(dx * 0.7f, dy * 0.7f)
                    lastX = e.getX(0); lastY = e.getY(0)
                    return true
                }
                val moved = hypot(e.x - downX, e.y - downY) > slop
                if (moved && !dragging && !painting && !movingGhost) beginStroke()
                if (dragging) { cam.panBy(e.x - lastX, e.y - lastY); vm.world?.cameraMoved = true }
                else if (painting) strokeTo(e.x, e.y)
                else if (movingGhost) { val (tx, ty) = pickTile(e.x, e.y); vm.setGhost(tx + ghostOffX, ty + ghostOffY) }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                if (miniDrag) { miniDrag = false; return true }
                if (multi) { multi = false; return true }
                if (painting) endStroke()
                else if (!dragging && !movingGhost && System.currentTimeMillis() - downTime < 400) tap(e.x, e.y)
                dragging = false; movingGhost = false
            }
            MotionEvent.ACTION_CANCEL -> { endStroke(); dragging = false; movingGhost = false; multi = false }
        }
        return true
    }

    /** Primer movimiento con un dedo: decide entre mover cámara, dibujar o arrastrar el plano. */
    private fun beginStroke() {
        val w = vm.world ?: run { dragging = true; return }
        when (val t = vm.tool) {
            is Tool.FenceTool -> { painting = true; lastCorner = snapCorner(downX, downY, w.n) }
            is Tool.PathTool -> { painting = true; lastPaintTile = -1; strokeTo(downX, downY) }
            is Tool.Terraform, is Tool.Demolish -> { painting = true; lastPaintTile = -1; strokeTo(downX, downY) }
            is Tool.Build -> {
                val g = vm.ghost
                val (tx, ty) = pickTile(downX, downY)
                val def = GameData.building(t.defId)
                if (g != null && tx >= g.first && tx < g.first + def.w && ty >= g.second && ty < g.second + def.h) {
                    movingGhost = true; ghostOffX = g.first - tx; ghostOffY = g.second - ty
                } else dragging = true
            }
            else -> dragging = true
        }
    }

    private fun strokeTo(px: Float, py: Float) {
        val w = vm.world ?: return
        when (val t = vm.tool) {
            is Tool.FenceTool -> drawFenceTo(px, py, w.n)
            is Tool.PathTool -> { lineTo(px, py, w); vm.planAddAll(strokeCells) }
            is Tool.Terraform -> { lineTo(px, py, w); stampBrush(w) { w.terraformMany(t.t, it) } }
            is Tool.Demolish -> { lineTo(px, py, w); stampBrush(w) { w.demolishBrush(it) } }
            else -> {}
        }
    }

    private fun endStroke() {
        painting = false; lastCorner = null; lastPaintTile = -1; vm.brushAt = null
    }

    /** Vértice de la rejilla más cercano al punto de pantalla, acotado a la isla. */
    private fun snapCorner(px: Float, py: Float, n: Int): Pair<Int, Int> {
        val (wx, wy) = pickWorld(px, py)
        return Pair(Math.round(wx).coerceIn(0, n), Math.round(wy).coerceIn(0, n))
    }

    /** Dibujo de vallas: une el vértice anterior con el actual siguiendo la rejilla y añade los bordes al plano. */
    private fun drawFenceTo(px: Float, py: Float, n: Int) {
        val (tx, ty) = snapCorner(px, py, n)
        val from = lastCorner ?: Pair(tx, ty).also { lastCorner = it }
        var cx = from.first; var cy = from.second
        strokeCells.clear()
        var guard = 0
        while ((cx != tx || cy != ty) && guard++ < 400) {
            if (abs(tx - cx) >= abs(ty - cy)) {
                if (tx > cx) { strokeCells.add(EdgeRef(true, cx, cy).key()); cx++ } else { cx--; strokeCells.add(EdgeRef(true, cx, cy).key()) }
            } else {
                if (ty > cy) { strokeCells.add(EdgeRef(false, cx, cy).key()); cy++ } else { cy--; strokeCells.add(EdgeRef(false, cx, cy).key()) }
            }
        }
        lastCorner = Pair(tx, ty)
        vm.planAddAll(strokeCells)
    }

    /** Línea 4-conexa desde el último tile hasta el punto dado; deja los tiles en strokeCells. */
    private fun lineTo(px: Float, py: Float, w: com.momentadesunt.cretaceouspark.core.World) {
        strokeCells.clear()
        val (tx0, ty0) = pickTile(px, py)
        val tx = tx0.coerceIn(0, w.n - 1); val ty = ty0.coerceIn(0, w.n - 1)
        val idx = w.s.idx(tx, ty)
        if (idx == lastPaintTile) return
        if (lastPaintTile >= 0) {
            var cx = lastPaintTile % w.n; var cy = lastPaintTile / w.n
            var guard = 0
            while ((cx != tx || cy != ty) && guard++ < 400) {
                if (abs(tx - cx) >= abs(ty - cy)) cx += if (tx > cx) 1 else -1 else cy += if (ty > cy) 1 else -1
                strokeCells.add(w.s.idx(cx, cy))
            }
        } else strokeCells.add(idx)
        lastPaintTile = idx
    }

    /** Aplica el pincel (radio según vm.brushSize) sobre cada tile de strokeCells, una sola llamada al mundo. */
    private fun stampBrush(w: com.momentadesunt.cretaceouspark.core.World, apply: (IntArray) -> Int): Int {
        val r = vm.brushSize - 1
        val r2 = r * r + 0.5f
        val n = w.n
        if (lastPaintTile >= 0) vm.brushAt = floatArrayOf(lastPaintTile % n + 0.5f, lastPaintTile / n + 0.5f, r + 0.5f)
        if (strokeCells.isEmpty()) return 0
        brushCells.clear()
        for (c in strokeCells) {
            val cx = c % n; val cy = c / n
            for (dy in -r..r) for (dx in -r..r) {
                if (dx * dx + dy * dy > r2) continue
                val x = cx + dx; val y = cy + dy
                if (x in 0 until n && y in 0 until n) brushCells.add(y * n + x)
            }
        }
        return apply(brushCells.toIntArray())
    }

    /** Borde más cercano al punto de mundo (wx, wy). */
    private fun nearestEdge(wx: Float, wy: Float): Pair<EdgeRef, Float> {
        val tx = floor(wx).toInt(); val ty = floor(wy).toInt()
        val fx = wx - tx; val fy = wy - ty
        val cands = listOf(
            EdgeRef(false, tx, ty) to fx,          // izquierda
            EdgeRef(false, tx + 1, ty) to 1f - fx, // derecha
            EdgeRef(true, tx, ty) to fy,           // arriba
            EdgeRef(true, tx, ty + 1) to 1f - fy   // abajo
        )
        return cands.minByOrNull { it.second }!!
    }

    private fun tap(px: Float, py: Float) {
        val w = vm.world ?: return
        val (wx, wy) = pickWorld(px, py)
        val tx = floor(wx).toInt(); val ty = floor(wy).toInt()
        when (val t = vm.tool) {
            is Tool.None -> select(px, py, wx, wy)
            is Tool.FenceTool -> {
                val (e, _) = nearestEdge(wx, wy)
                if (w.edgeValid(e)) vm.planAdd(e.key(), toggle = true)
            }
            is Tool.Gate -> {
                val (e, _) = nearestEdge(wx, wy)
                val r = w.toggleGate(e); if (!r.ok) vm.message(r.reason)
            }
            is Tool.PathTool -> if (w.s.inBounds(tx, ty)) vm.planAdd(w.s.idx(tx, ty), toggle = true)
            is Tool.Terraform -> {
                if (!w.s.inBounds(tx, ty)) return
                strokeCells.clear(); strokeCells.add(w.s.idx(tx, ty)); lastPaintTile = -1
                val changed = stampBrush(w) { w.terraformMany(t.t, it) }
                vm.brushAt = null
                if (changed == 0) vm.message(w.canTerraform(t.t, tx, ty).reason)
            }
            is Tool.Demolish -> {
                val b = w.grid.buildingAt(tx, ty)
                if (b != null) { vm.select(Selection.BuildingSel(b.id)); vm.message("Pulsa Demoler en el panel del edificio"); return }
                if (!w.s.inBounds(tx, ty)) return
                strokeCells.clear(); strokeCells.add(w.s.idx(tx, ty)); lastPaintTile = -1
                val removed = stampBrush(w) { w.demolishBrush(it) }
                vm.brushAt = null
                if (removed == 0) vm.message("Nada que demoler")
            }
            is Tool.Build -> {
                val def = GameData.building(t.defId)
                val g = vm.ghost
                if (g != null && tx >= g.first && tx < g.first + def.w && ty >= g.second && ty < g.second + def.h) return // toque sobre el plano: se queda
                vm.setGhost(tx - def.w / 2, ty - def.h / 2)
            }
        }
    }

    private fun select(px: Float, py: Float, wx: Float, wy: Float) {
        val w = vm.world ?: return
        // dinos por distancia en pantalla
        var best: Int = -1; var bd = Float.MAX_VALUE
        for (d in w.s.dinos) {
            val dx = floor(d.x).toInt(); val dy = floor(d.y).toInt()
            val gz = if (w.s.inBounds(dx, dy)) w.s.groundZ(dx, dy) else 0f
            val sx = cam.worldSx(d.x, d.y); val sy = cam.worldSy(d.x, d.y, gz + 0.5f)
            val dist = hypot(sx - px, sy - py)
            val radius = cam.tileW * (0.35f + 0.25f * d.def.size.footprint)
            if (dist < radius && dist < bd) { bd = dist; best = d.id }
        }
        if (best >= 0) { vm.select(Selection.DinoSel(best)); return }
        val tx = floor(wx).toInt(); val ty = floor(wy).toInt()
        val b = w.grid.buildingAt(tx, ty)
        if (b != null) { vm.select(Selection.BuildingSel(b.id)); return }
        val (e, d) = nearestEdge(wx, wy)
        if (d < 0.25f && w.edgeValid(e) && w.grid.fenceType(e) != 0) { vm.select(Selection.EdgeSel(e)); return }
        vm.select(null)
    }
}
