package com.momentadesunt.cretaceouspark.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Guardado en JSON (kotlinx.serialization), una ranura por isla. Escritura atómica vía archivo temporal. */
object Save {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun file(dir: File, island: String) = File(dir, "save_$island.json")

    fun write(dir: File, s: GameState, async: Boolean = false) {
        val f = file(dir, s.island)
        val tmp = File(dir, f.name + ".tmp")
        val text = json.encodeToString(s)   // instantanea coherente: se codifica en el hilo de la simulacion
        val io = Runnable {
            synchronized(this) {
                tmp.writeText(text)
                if (f.exists()) f.delete()
                tmp.renameTo(f)
            }
        }
        if (async) Thread(io, "save-io").start() else io.run()
    }

    fun read(dir: File, island: String): GameState? {
        val f = file(dir, island)
        if (!f.exists()) return null
        return try { json.decodeFromString<GameState>(f.readText()) } catch (e: Exception) { null }
    }

    fun exists(dir: File, island: String) = file(dir, island).exists()
    fun delete(dir: File, island: String) { file(dir, island).delete() }
}
