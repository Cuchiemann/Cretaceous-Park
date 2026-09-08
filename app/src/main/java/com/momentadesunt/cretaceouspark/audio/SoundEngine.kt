package com.momentadesunt.cretaceouspark.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Audio sintetizado en tiempo real: no hay archivos de sonido en el proyecto.
 *  - Efectos cortos (PCM 16 bits, 22 050 Hz) generados una vez al arrancar.
 *  - Dos temas generativos (calma / alerta) mezclados con fundido en un hilo propio.
 *  - Vibración corta al colocar y en alertas rojas.
 */
class SoundEngine(private val ctx: Context) {
    companion object { const val SR = 22050 }

    /** La app está en primer plano: si no, ni efectos ni música. */
    @Volatile var appActive = true
        set(v) { field = v; if (!v) stopMusic() else if (musicOn) startMusic() }
    var sfxOn = true
    var musicOn = true
        set(v) { field = v; if (!v) stopMusic() else startMusic() }
    var vibrateOn = true

    private val clips = HashMap<String, ShortArray>()
    private val handler = Handler(Looper.getMainLooper())
    private var activeTracks = 0
    private val rnd = Random(7)

    init { synthClips() }

    // ------------------------------------------------------------------ efectos
    private fun synthClips() {
        clips["pop"] = render(0.09f) { t, p -> sin(2 * PI * (520.0 - 260.0 * p) * t) * env(p, 0.02, 0.6) }
        clips["demolish"] = render(0.18f) { t, p -> (noise() * 0.7 + sin(2 * PI * 120.0 * t) * 0.4) * env(p, 0.01, 0.35) }
        clips["coin"] = render(0.14f) { t, p -> (sin(2 * PI * 880.0 * t) * 0.5 + sin(2 * PI * 1320.0 * t) * 0.4) * env(p, 0.01, 0.5) }
        clips["chime"] = render(0.5f) { t, p -> (sin(2 * PI * 660.0 * t) * 0.4 + sin(2 * PI * 990.0 * t) * 0.3 * (if (p > 0.3) 1.0 else 0.0) + sin(2 * PI * 1320.0 * t) * 0.25 * (if (p > 0.55) 1.0 else 0.0)) * env(p, 0.02, 0.45) }
        clips["error"] = render(0.16f) { t, p -> sq(220.0 * t) * 0.35 * env(p, 0.005, 0.5) }
        clips["alert"] = render(0.42f) { t, p -> (if ((p * 4).toInt() % 2 == 0) saw(660.0 * t) else saw(495.0 * t)) * 0.35 * env(p, 0.01, 0.9) }
        clips["hit"] = render(0.12f) { t, p -> (noise() * 0.8 + sin(2 * PI * 90.0 * t) * 0.6) * env(p, 0.002, 0.25) }
        clips["dart"] = render(0.22f) { t, p -> sin(2 * PI * (1400.0 - 900.0 * p) * t) * 0.4 * env(p, 0.01, 0.5) }
        clips["storm"] = render(1.6f) { _, p -> lowNoise() * 0.5 * env(p, 0.3, 0.9) }
        clips["thunder"] = render(1.2f) { t, p -> (lowNoise() * 0.9 + sin(2 * PI * 45.0 * t) * 0.5) * env(p, 0.01, 0.6) }
        clips["roar:S"] = render(0.35f) { t, p -> (saw((320.0 - 80.0 * p) * t) * 0.5 + noise() * 0.2) * env(p, 0.03, 0.5) }
        clips["roar:M"] = render(0.7f) { t, p -> (saw((150.0 - 40.0 * p) * t) * 0.55 + sin(2 * PI * 75.0 * t) * 0.3 + lowNoise() * 0.25) * env(p, 0.05, 0.6) }
        clips["roar:L"] = render(1.1f) { t, p -> (saw((85.0 - 25.0 * p) * t) * 0.6 + sin(2 * PI * 42.0 * t) * 0.4 + lowNoise() * 0.3) * env(p, 0.08, 0.65) }
    }

    private var lp = 0.0
    private fun noise(): Double = rnd.nextDouble() * 2 - 1
    private fun lowNoise(): Double { lp += (noise() - lp) * 0.08; return lp * 4.0 }
    private fun sq(x: Double) = if ((x - kotlin.math.floor(x)) < 0.5) 1.0 else -1.0
    private fun saw(x: Double) = 2.0 * (x - kotlin.math.floor(x + 0.5))
    /** Envolvente ataque/decay normalizada: p en 0..1, a = ataque relativo, d = punto donde la caída llega al 10 %. */
    private fun env(p: Double, a: Double, d: Double): Double = if (p < a) p / a else exp(-(p - a) / max(0.01, d) * 2.3)

    private fun render(seconds: Float, f: (t: Double, p: Double) -> Double): ShortArray {
        val n = (seconds * SR).toInt()
        val out = ShortArray(n)
        lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val v = f(t, i.toDouble() / n).coerceIn(-1.0, 1.0)
            out[i] = (v * 32767 * 0.8).toInt().toShort()
        }
        return out
    }

    fun play(name: String, volume: Float = 1f) {
        if (!sfxOn || !appActive) return
        val clip = clips[name] ?: return
        if (activeTracks >= 6) return
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(clip.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(clip, 0, clip.size)
            track.setVolume(volume.coerceIn(0f, 1f))
            track.play()
            activeTracks++
            handler.postDelayed({ try { track.stop(); track.release() } catch (_: Exception) {}; activeTracks-- }, (clip.size * 1000L / SR) + 60)
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ vibración
    fun vibrate(ms: Long) {
        if (!vibrateOn || !appActive) return
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ eventos del juego
    fun event(name: String) {
        when {
            name == "place" -> { play("pop"); vibrate(15) }
            name == "demolish" -> play("demolish")
            name == "coin" -> play("coin", 0.6f)
            name == "chime" -> play("chime")
            name == "error" -> play("error", 0.7f)
            name == "alert" -> { play("alert"); vibrate(80) }
            name == "hit" -> play("hit")
            name == "dart" -> play("dart")
            name == "storm" -> { play("storm"); play("thunder", 0.8f) }
            name == "thunder" -> play("thunder", 0.7f)
            name.startsWith("roar:") -> play(name)
        }
    }

    // ------------------------------------------------------------------ música generativa
    @Volatile var tense = false
    @Volatile private var running = false
    private var thread: Thread? = null

    fun startMusic() {
        if (!musicOn || !appActive || running) return
        running = true
        thread = Thread({ musicLoop() }, "music").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1; start() }
    }

    fun stopMusic() { running = false; thread = null }

    private class Voice(val freq: Double, val amp: Double, val decay: Double, val harm: Double) { var age = 0.0 }

    private fun musicLoop() {
        var track: AudioTrack? = null
        try {
            val frames = 2048
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(frames * 2 * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            val buf = ShortArray(frames)
            val voices = ArrayList<Voice>()
            // patrones (semitonos relativos a A3 = 220 Hz): calma pentatónica, alerta menor con pulso
            val calm = intArrayOf(3, 7, 10, 12, 15, 10, 7, 12, 3, 10, 7, 15, 12, 10, 7, 5)
            val calmBass = intArrayOf(-9, -9, -2, -2)
            val tenseSeq = intArrayOf(0, 3, 7, 8, 7, 3, 0, -1, 0, 3, 7, 10, 8, 7, 3, -1)
            val tenseBass = intArrayOf(-12, -12, -12, -12, -13, -13, -12, -12)
            var gCalm = 1.0; var gTense = 0.0
            var stepCalm = 0; var stepTense = 0
            var tCalm = 0.0; var tTense = 0.0
            val stepCalmLen = 60.0 / 84.0 / 2.0     // corcheas a 84 bpm
            val stepTenseLen = 60.0 / 126.0 / 2.0   // corcheas a 126 bpm
            val dt = frames.toDouble() / SR
            var phaseTrem = 0.0
            while (running) {
                val target = if (tense) 1.0 else 0.0
                gTense += (target - gTense) * 0.08; gCalm += ((1.0 - target) - gCalm) * 0.08
                // disparar notas
                tCalm += dt
                while (tCalm >= stepCalmLen) {
                    tCalm -= stepCalmLen
                    if (gCalm > 0.02) {
                        voices.add(Voice(220.0 * 2.0.pow(calm[stepCalm % calm.size] / 12.0), 0.18 * gCalm, 0.9, 0.35))
                        if (stepCalm % 4 == 0) voices.add(Voice(220.0 * 2.0.pow(calmBass[(stepCalm / 4) % calmBass.size] / 12.0), 0.16 * gCalm, 1.6, 0.15))
                    }
                    stepCalm++
                }
                tTense += dt
                while (tTense >= stepTenseLen) {
                    tTense -= stepTenseLen
                    if (gTense > 0.02) {
                        voices.add(Voice(220.0 * 2.0.pow(tenseSeq[stepTense % tenseSeq.size] / 12.0), 0.16 * gTense, 0.35, 0.6))
                        if (stepTense % 2 == 0) voices.add(Voice(220.0 * 2.0.pow(tenseBass[(stepTense / 2) % tenseBass.size] / 12.0), 0.2 * gTense, 0.3, 0.8))
                    }
                    stepTense++
                }
                // renderizar
                for (i in 0 until frames) {
                    var v = 0.0
                    val tt = i.toDouble() / SR
                    for (vo in voices) {
                        val a = vo.age + tt
                        val e = exp(-a / vo.decay * 3.0)
                        v += vo.amp * e * (sin(2 * PI * vo.freq * a) + vo.harm * sin(2 * PI * vo.freq * 2 * a) * e)
                    }
                    phaseTrem += 1.0 / SR
                    val trem = 1.0 - 0.25 * gTense * (0.5 + 0.5 * sin(2 * PI * 5.5 * phaseTrem))
                    buf[i] = (v * trem * 0.55).coerceIn(-1.0, 1.0).times(32767).toInt().toShort()
                }
                for (vo in voices) vo.age += dt
                voices.removeAll { it.age > it.decay * 2.5 }
                if (voices.size > 24) voices.subList(0, voices.size - 24).clear()
                track.write(buf, 0, frames)
            }
        } catch (_: Exception) {
        } finally {
            try { track?.stop(); track?.release() } catch (_: Exception) {}
            running = false
        }
    }
}
