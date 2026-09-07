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
import com.momentadesunt.cretaceouspark.core.Fence
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
            if (isAttachedToWindow) choreo.postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); lastNanos = 0L; choreo.postFrameCallback(frameCb) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); choreo.removeFrameCallback(frameCb) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); cam.setViewport(w, h) }

    private fun step(dt: Float) {
        val world = vm.world ?: return
        if (camMap != world.s.size) { camMap = world.s.size; cam.mapSize = world.s.size; centerOnEntrance() }
        animTime += dt
        val speed = if (vm.overlay != null || world.s.gameOver) 0 else world.s.speed
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
            px[i] = if (world.grid.region[i] > 0) blend(base, 0xFFFFFFFF.toInt(), 0.25f) else base
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
        renderer.rectPreview = rectStart?.let { st -> rectEnd?.let { en -> intArrayOf(st.first, st.second, en.first, en.second) } }
    }

    // ------------------------------------------------------------------ gestos
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f; private var downY = 0f; private var downTime = 0L
    private var lastX = 0f; private var lastY = 0f
    private var dragging = false
    private var painting = false
    private var multi = false
    private var rectStart: Pair<Int, Int>? = null
    private var rectEnd: Pair<Int, Int>? = null
    private var lastPaintTile = -1
    private var scaleAcc = 1f
    private var miniDrag = false

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
                dragging = false; painting = false; multi = false; scaleAcc = 1f
                rectStart = null; rectEnd = null; lastPaintTile = -1
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> { multi = true; rectStart = null; rectEnd = null }
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
                if (moved && !dragging && !painting) {
                    when (val t = vm.tool) {
                        is Tool.FenceTool -> { painting = true; rectStart = cam.screenToTile(downX, downY) }
                        is Tool.PathTool, is Tool.Terraform -> { painting = true; paintAt(downX, downY, t) }
                        else -> dragging = true
                    }
                }
                if (dragging) { cam.panBy(e.x - lastX, e.y - lastY); vm.world?.cameraMoved = true }
                else if (painting) {
                    val t = vm.tool
                    if (t is Tool.FenceTool) rectEnd = cam.screenToTile(e.x, e.y) else paintAt(e.x, e.y, t)
                }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                if (miniDrag) { miniDrag = false; return true }
                if (multi) { multi = false; return true }
                if (painting) {
                    val t = vm.tool
                    if (t is Tool.FenceTool && rectStart != null && rectEnd != null) {
                        val w = vm.world
                        val st = rectStart!!; val en = rectEnd!!
                        if (w != null && (st != en)) {
                            val placed = w.fenceRect(st.first, st.second, en.first, en.second, t.type)
                            val fail = w.lastFenceFail
                            vm.message(when {
                                placed > 0 && fail.isNotEmpty() -> "$placed tramos colocados · $fail"
                                placed > 0 -> "$placed tramos de ${Fence.names[t.type]}"
                                fail.isNotEmpty() -> "No se pudo vallar: $fail"
                                else -> "No se pudo vallar ahí"
                            })
                        } else if (w != null) tap(downX, downY)
                    }
                    rectStart = null; rectEnd = null
                } else if (!dragging && System.currentTimeMillis() - downTime < 400) {
                    tap(e.x, e.y)
                }
                dragging = false; painting = false
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; painting = false; multi = false; rectStart = null; rectEnd = null }
        }
        return true
    }

    private fun paintAt(px: Float, py: Float, t: Tool) {
        val w = vm.world ?: return
        val (tx, ty) = cam.screenToTile(px, py)
        if (!w.s.inBounds(tx, ty)) return
        val idx = w.s.idx(tx, ty)
        if (idx == lastPaintTile) return
        // línea 4-conexa desde el último tile pintado para que los caminos queden unidos por lados
        val cells = ArrayList<Pair<Int, Int>>()
        if (lastPaintTile >= 0) {
            var cx = lastPaintTile % w.n; var cy = lastPaintTile / w.n
            var guard = 0
            while ((cx != tx || cy != ty) && guard++ < 200) {
                if (kotlin.math.abs(tx - cx) >= kotlin.math.abs(ty - cy)) cx += if (tx > cx) 1 else -1 else cy += if (ty > cy) 1 else -1
                cells.add(Pair(cx, cy))
            }
        } else cells.add(Pair(tx, ty))
        lastPaintTile = idx
        for ((px2, py2) in cells) {
            when (t) {
                is Tool.PathTool -> w.placePath(px2, py2)
                is Tool.Terraform -> w.terraform(t.t, px2, py2)
                else -> {}
            }
        }
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
        val (wx, wy) = cam.screenToWorld(px, py)
        val tx = floor(wx).toInt(); val ty = floor(wy).toInt()
        when (val t = vm.tool) {
            is Tool.None -> select(px, py, wx, wy)
            is Tool.FenceTool -> {
                val (e, _) = nearestEdge(wx, wy)
                val r = w.placeFence(e, t.type)
                if (!r.ok) vm.message(r.reason)
            }
            is Tool.Gate -> {
                val (e, _) = nearestEdge(wx, wy)
                val r = w.toggleGate(e); if (!r.ok) vm.message(r.reason)
            }
            is Tool.PathTool -> { val r = w.placePath(tx, ty); if (!r.ok) vm.message(r.reason) }
            is Tool.Terraform -> { val r = w.terraform(t.t, tx, ty); if (!r.ok) vm.message(r.reason) }
            is Tool.Demolish -> {
                val (e, d) = nearestEdge(wx, wy)
                val r = if (d < 0.22f && w.grid.fenceType(e) != 0) w.removeFence(e) else w.demolishAt(tx, ty)
                if (!r.ok) vm.message(r.reason)
            }
            is Tool.Build -> {
                val def = GameData.building(t.defId)
                val gx = tx - def.w / 2; val gy = ty - def.h / 2
                val g = vm.ghost
                if (g != null && g.first == gx && g.second == gy) {
                    val r = w.placeBuilding(t.defId, gx, gy)
                    if (r.ok) { vm.message("${def.name} construido"); vm.ghost = null } else vm.message(r.reason)
                } else vm.setGhost(gx, gy)
            }
        }
    }

    private fun select(px: Float, py: Float, wx: Float, wy: Float) {
        val w = vm.world ?: return
        // dinos por distancia en pantalla
        var best: Int = -1; var bd = Float.MAX_VALUE
        for (d in w.s.dinos) {
            val sx = cam.worldSx(d.x, d.y); val sy = cam.worldSy(d.x, d.y, 0.5f)
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
        // cualquier borde con valla alrededor del tile si el toque cae cerca
        if (w.s.inBounds(tx, ty) && Terrain.isWalkablePath(w.s.terrainAt(tx, ty))) { vm.select(null); return }
        vm.select(null)
    }
}
