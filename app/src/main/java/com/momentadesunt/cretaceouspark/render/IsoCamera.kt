package com.momentadesunt.cretaceouspark.render

import kotlin.math.floor

/** Cámara ortográfica isométrica 2:1 con 4 giros y 3 niveles de zoom. */
class IsoCamera(var mapSize: Int) {
    var rot = 0                 // 0..3
    var zoom = 1                // 0 cerca, 1 medio, 2 lejos
    var fx = mapSize / 2f       // punto del mundo en el centro de pantalla
    var fy = mapSize / 2f
    var screenW = 1f
    var screenH = 1f

    private val tilesAcross = floatArrayOf(11f, 18f, 30f, 52f)

    val tileW: Float get() = screenW / tilesAcross[zoom]
    val tileH: Float get() = tileW / 2f
    val zUnit: Float get() = tileH * 1.15f

    fun setViewport(w: Int, h: Int) { screenW = w.toFloat(); screenH = h.toFloat() }

    /** Coordenadas de mundo → coordenadas de vista (rotadas alrededor del centro del mapa). */
    fun toViewX(x: Float, y: Float): Float {
        val c = mapSize / 2f; val dx = x - c; val dy = y - c
        return when (rot) { 0 -> dx; 1 -> -dy; 2 -> -dx; else -> dy } + c
    }
    fun toViewY(x: Float, y: Float): Float {
        val c = mapSize / 2f; val dx = x - c; val dy = y - c
        return when (rot) { 0 -> dy; 1 -> dx; 2 -> -dy; else -> -dx } + c
    }
    fun viewToWorldX(vx: Float, vy: Float): Float {
        val c = mapSize / 2f; val dx = vx - c; val dy = vy - c
        return when (rot) { 0 -> dx; 1 -> dy; 2 -> -dx; else -> -dy } + c
    }
    fun viewToWorldY(vx: Float, vy: Float): Float {
        val c = mapSize / 2f; val dx = vx - c; val dy = vy - c
        return when (rot) { 0 -> dy; 1 -> -dx; 2 -> -dy; else -> dx } + c
    }

    // Proyeccion del foco y metricas cacheadas por fotograma (llamar a prepare() antes de dibujar)
    private var fvx = 0f; private var fvy = 0f
    private var halfW = 0f; private var halfH = 0f; private var tH = 1f; private var tH2 = 0.5f; private var zU = 1f
    fun prepare() {
        fvx = toViewX(fx, fy); fvy = toViewY(fx, fy)
        halfW = screenW / 2f; halfH = screenH / 2f; tH = tileH; tH2 = tileH / 2f; zU = zUnit
    }

    /** Vista → pantalla. */
    fun sx(vx: Float, vy: Float): Float = halfW + ((vx - fvx) - (vy - fvy)) * tH
    fun sy(vx: Float, vy: Float, z: Float): Float = halfH + ((vx - fvx) + (vy - fvy)) * tH2 - z * zU

    /** ¿El punto de mundo cae dentro de la pantalla con un margen en tiles? */
    fun visible(x: Float, y: Float, marginTiles: Float): Boolean {
        val px = worldSx(x, y); val py = worldSy(x, y, 0f)
        val m = marginTiles * tileW
        return px > -m && px < screenW + m && py > -m && py < screenH + m
    }

    /** Mundo → pantalla directo. */
    fun worldSx(x: Float, y: Float) = sx(toViewX(x, y), toViewY(x, y))
    fun worldSy(x: Float, y: Float, z: Float) = sy(toViewX(x, y), toViewY(x, y), z)

    /** Pantalla (suelo z=0) → mundo (coordenadas continuas). */
    fun screenToWorld(px: Float, py: Float): Pair<Float, Float> {
        val fvx = toViewX(fx, fy); val fvy = toViewY(fx, fy)
        prepare()
        val a = (px - screenW / 2f) / tileH           // vx - vy
        val b = (py - screenH / 2f) / (tileH / 2f)    // vx + vy
        val vx = (a + b) / 2f + fvx
        val vy = (b - a) / 2f + fvy
        return Pair(viewToWorldX(vx, vy), viewToWorldY(vx, vy))
    }

    fun screenToTile(px: Float, py: Float): Pair<Int, Int> {
        val (wx, wy) = screenToWorld(px, py)
        return Pair(floor(wx).toInt(), floor(wy).toInt())
    }

    /** Desplazar la cámara según un delta de pantalla en píxeles. */
    fun panBy(dpx: Float, dpy: Float) {
        val a = dpx / tileH; val b = dpy / (tileH / 2f)
        val dvx = (a + b) / 2f; val dvy = (b - a) / 2f
        // mover foco en sentido contrario, en coordenadas de mundo
        val c = mapSize / 2f
        val fvx = toViewX(fx, fy) - dvx; val fvy = toViewY(fx, fy) - dvy
        fx = viewToWorldX(fvx, fvy); fy = viewToWorldY(fvx, fvy)
        val m = 3f
        fx = fx.coerceIn(-m, mapSize + m); fy = fy.coerceIn(-m, mapSize + m)
        if (c < 0) fx = c
    }

    fun rotate() { rot = (rot + 1) % 4 }
    fun zoomIn() { if (zoom > 0) zoom-- }
    fun zoomOut() { if (zoom < 3) zoom++ }
    fun focus(x: Float, y: Float) { fx = x; fy = y }
}
