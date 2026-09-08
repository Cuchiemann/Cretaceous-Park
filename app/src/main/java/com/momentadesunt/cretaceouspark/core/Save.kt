package com.momentadesunt.cretaceouspark.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Guardado en JSON (kotlinx.serialization), una ranura por isla. Escritura atómica vía archivo temporal. */
object Save {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun file(dir: File, island: String) = File(dir, "save_$island.json")

    private val lock = Any()

    fun write(dir: File, s: GameState, async: Boolean = false) {
        val f = file(dir, s.island)
        val tmp = File(dir, f.name + ".tmp")
        val bak = File(dir, f.name + ".bak")
        val text = json.encodeToString(s)   // instantánea coherente: se codifica en el hilo de la simulación
        val io = Runnable {
            synchronized(lock) {
                try {
                    tmp.writeText(text)
                    if (f.exists()) { bak.delete(); f.renameTo(bak) }
                    tmp.renameTo(f)
                } catch (_: Exception) {}
            }
        }
        if (async) Thread(io, "save-io").start() else io.run()
    }

    private fun decode(f: File): GameState? {
        if (!f.exists() || f.length() < 32) return null
        return try { json.decodeFromString<GameState>(f.readText()) } catch (e: Exception) { null }
    }

    /** Lee el guardado; si está dañado o vacío, intenta la copia de seguridad. */
    fun read(dir: File, island: String): GameState? = synchronized(lock) {
        val f = file(dir, island)
        decode(f) ?: decode(File(dir, f.name + ".bak"))
    }

    fun exists(dir: File, island: String) = file(dir, island).exists()
    fun delete(dir: File, island: String) { synchronized(lock) { val f = file(dir, island); f.delete(); File(dir, f.name + ".bak").delete(); File(dir, f.name + ".tmp").delete() } }
}
