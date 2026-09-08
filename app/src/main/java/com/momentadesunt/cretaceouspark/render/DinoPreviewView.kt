package com.momentadesunt.cretaceouspark.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Choreographer
import android.view.View
import com.momentadesunt.cretaceouspark.core.Dino
import com.momentadesunt.cretaceouspark.core.GameData
import com.momentadesunt.cretaceouspark.core.Size

/** Vista previa animada de un modelo de dinosaurio (para la ficha de ADN). Gira despacio sobre un tile de hierba. */
@SuppressLint("ViewConstructor")
class DinoPreviewView(context: Context, species: String, skin: Int = 0) : View(context) {
    private val cam = IsoCamera(6)
    private val renderer = IsoRenderer(cam)
    private var dino = Dino(0, species, 3f, 3f, 3f, 3f, skin = skin, facing = 1)
    private var time = 0f
    private var last = 0L

    var species: String = species
        set(v) { if (field != v) { field = v; dino = Dino(0, v, 3f, 3f, 3f, 3f, skin = skin, facing = 1); setup() } }

    /** Piel alternativa (colores invertidos): se refleja al instante en el modelo. */
    var skin: Int = skin
        set(v) { if (field != v) { field = v; dino.skin = v } }

    private val cb = object : Choreographer.FrameCallback {
        override fun doFrame(nanos: Long) {
            if (last != 0L) { val dt = ((nanos - last) / 1e9f).coerceAtMost(0.1f); time += dt }
            last = nanos
            invalidate()
            if (isAttachedToWindow) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun setup() {
        val def = GameData.species(dino.species)
        cam.customTilesAcross = when (def.size) { Size.S -> 2.0f; Size.M -> 3.0f; Size.L -> 4.6f }
        cam.focus(3f, 3f)
    }

    init { setup() }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); last = 0L; Choreographer.getInstance().postFrameCallback(cb) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); Choreographer.getInstance().removeFrameCallback(cb) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); cam.setViewport(w, h); cam.focus(3f, 3f - 0.15f * cam.customTilesAcross) }

    override fun onDraw(canvas: Canvas) {
        // paseo lento para mostrar la animación de paso
        dino.tx = dino.x + 1f
        dino.hop = (dino.hop + 0.12f) % 6.2832f
        renderer.drawPreview(canvas, dino, time)
    }
}
