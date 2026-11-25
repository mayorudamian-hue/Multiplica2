package com.example.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

enum class SoundEffect(val fileName: String) {
    MOVE("sfx_move.wav"),
    ROTATE("sfx_rotate.wav"),
    SOFT_DROP("sfx_soft_drop.wav"),
    DROP_LAND("sfx_drop_land.wav"),
    HARD_DROP("sfx_hard_drop.wav"),
    ROW_CLEAR_SINGLE("sfx_row_single.wav"),
    ROW_CLEAR_MULTI("sfx_row_multi.wav"),
    GAME_OVER("sfx_game_over.wav")
}

interface GameSoundPlayer {
    fun playMove()
    fun playRotate()
    fun playSoftDrop()
    fun playDrop(isHardDrop: Boolean)
    fun playRowClear(rowCount: Int)
    fun playGameOver()
    fun release()
}

/**
 * Sintetizador PCM en memoria (16-bit mono WAV a 22050 Hz) para efectos auditivos ligeros
 * sin requerir archivos binarios externos.
 */
object SoundSynthesizer {

    const val SAMPLE_RATE = 22050

    fun synthesizeWav(effect: SoundEffect): ByteArray {
        val samples = when (effect) {
            SoundEffect.MOVE -> synthesizeGlide(
                durationMs = 38,
                startFreq = 520.0,
                endFreq = 660.0,
                amplitude = 0.30,
                harmonicMix = 0.18,
                decayRate = 9.0
            )
            SoundEffect.ROTATE -> synthesizeSteps(
                notes = listOf(523.25 to 34, 783.99 to 44),
                amplitude = 0.38,
                harmonicMix = 0.22,
                decayRate = 6.5
            )
            SoundEffect.SOFT_DROP -> synthesizeGlide(
                durationMs = 30,
                startFreq = 350.0,
                endFreq = 260.0,
                amplitude = 0.22,
                harmonicMix = 0.10,
                decayRate = 10.0
            )
            SoundEffect.DROP_LAND -> synthesizeGlide(
                durationMs = 95,
                startFreq = 240.0,
                endFreq = 95.0,
                amplitude = 0.52,
                harmonicMix = 0.28,
                decayRate = 7.5
            )
            SoundEffect.HARD_DROP -> synthesizeGlide(
                durationMs = 120,
                startFreq = 420.0,
                endFreq = 85.0,
                amplitude = 0.60,
                harmonicMix = 0.30,
                decayRate = 6.2
            )
            SoundEffect.ROW_CLEAR_SINGLE -> synthesizeSteps(
                notes = listOf(523.25 to 75, 659.25 to 75, 783.99 to 115),
                amplitude = 0.54,
                harmonicMix = 0.25,
                decayRate = 4.2
            )
            SoundEffect.ROW_CLEAR_MULTI -> synthesizeSteps(
                notes = listOf(523.25 to 68, 659.25 to 68, 783.99 to 75, 1046.50 to 150),
                amplitude = 0.58,
                harmonicMix = 0.28,
                decayRate = 3.6
            )
            SoundEffect.GAME_OVER -> synthesizeSteps(
                notes = listOf(392.00 to 105, 329.63 to 105, 261.63 to 150),
                amplitude = 0.44,
                harmonicMix = 0.15,
                decayRate = 4.0
            )
        }
        return encodeWav(samples)
    }

    private fun synthesizeGlide(
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        amplitude: Double,
        harmonicMix: Double,
        decayRate: Double
    ): ShortArray {
        val count = ((SAMPLE_RATE * durationMs) / 1000.0).roundToInt().coerceAtLeast(1)
        val out = ShortArray(count)
        var phase = 0.0
        val attackSamples = (SAMPLE_RATE * 0.004).coerceAtLeast(1.0)

        for (i in 0 until count) {
            val tNorm = i.toDouble() / count
            val freq = startFreq + (endFreq - startFreq) * tNorm
            phase += 2.0 * PI * freq / SAMPLE_RATE

            val attackEnv = (i / attackSamples).coerceAtMost(1.0)
            val decayEnv = exp(-decayRate * tNorm)
            val tailFade = if (tNorm > 0.85) (1.0 - tNorm) / 0.15 else 1.0
            val env = attackEnv * decayEnv * tailFade

            val wave = (sin(phase) + harmonicMix * sin(phase * 2.0)) / (1.0 + harmonicMix)
            val sample = (wave * amplitude * env * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = sample.toShort()
        }
        return out
    }

    private fun synthesizeSteps(
        notes: List<Pair<Double, Int>>,
        amplitude: Double,
        harmonicMix: Double,
        decayRate: Double
    ): ShortArray {
        val stepArrays = notes.map { (freq, durMs) ->
            synthesizeGlide(
                durationMs = durMs,
                startFreq = freq,
                endFreq = freq * 1.005,
                amplitude = amplitude,
                harmonicMix = harmonicMix,
                decayRate = decayRate
            )
        }
        val total = stepArrays.sumOf { it.size }
        val merged = ShortArray(total)
        var offset = 0
        for (arr in stepArrays) {
            arr.copyInto(merged, destinationOffset = offset)
            offset += arr.size
        }
        return merged
    }

    private fun encodeWav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)

        fun writeAscii(text: String) {
            for (ch in text) out.write(ch.code)
        }

        fun writeIntLE(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 24) and 0xFF)
        }

        fun writeShortLE(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
        }

        writeAscii("RIFF")
        writeIntLE(36 + dataSize)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeIntLE(16)
        writeShortLE(1)
        writeShortLE(1)
        writeIntLE(SAMPLE_RATE)
        writeIntLE(SAMPLE_RATE * 2)
        writeShortLE(2)
        writeShortLE(16)
        writeAscii("data")
        writeIntLE(dataSize)

        for (s in samples) {
            writeShortLE(s.toInt())
        }
        return out.toByteArray()
    }
}

class SoundPoolGameSoundPlayer(
    context: Context,
    private val isSoundEnabled: () -> Boolean
) : GameSoundPlayer {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val soundIds = ConcurrentHashMap<SoundEffect, Int>()
    private val loadedIds = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var released = false

    private val soundPool: SoundPool? = runCatching {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attributes)
            .build()
            .apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        loadedIds.add(sampleId)
                    }
                }
            }
    }.getOrNull()

    init {
        scope.launch {
            prepareSounds()
        }
    }

    private fun prepareSounds() {
        val pool = soundPool ?: return
        if (released) return
        runCatching {
            val sfxDir = File(appContext.cacheDir, "sfx").apply { mkdirs() }
            for (effect in SoundEffect.entries) {
                if (released) break
                val target = File(sfxDir, effect.fileName)
                if (!target.exists() || target.length() < 44L) {
                    target.writeBytes(SoundSynthesizer.synthesizeWav(effect))
                }
                val id = pool.load(target.absolutePath, 1)
                if (id > 0) {
                    soundIds[effect] = id
                }
            }
        }
    }

    override fun playMove() {
        play(SoundEffect.MOVE, volume = 0.55f, rate = 1.0f)
    }

    override fun playRotate() {
        play(SoundEffect.ROTATE, volume = 0.72f, rate = 1.0f)
    }

    override fun playSoftDrop() {
        play(SoundEffect.SOFT_DROP, volume = 0.45f, rate = 1.0f)
    }

    override fun playDrop(isHardDrop: Boolean) {
        if (isHardDrop) {
            play(SoundEffect.HARD_DROP, volume = 0.85f, rate = 1.0f)
        } else {
            play(SoundEffect.DROP_LAND, volume = 0.70f, rate = 1.0f)
        }
    }

    override fun playRowClear(rowCount: Int) {
        if (rowCount >= 2) {
            val pitchRate = (1.0f + ((rowCount - 2).coerceAtMost(3) * 0.06f))
            play(SoundEffect.ROW_CLEAR_MULTI, volume = 0.92f, rate = pitchRate)
        } else {
            play(SoundEffect.ROW_CLEAR_SINGLE, volume = 0.85f, rate = 1.0f)
        }
    }

    override fun playGameOver() {
        play(SoundEffect.GAME_OVER, volume = 0.78f, rate = 1.0f)
    }

    private fun play(effect: SoundEffect, volume: Float, rate: Float) {
        if (released || !isSoundEnabled()) return
        val pool = soundPool ?: return
        val id = soundIds[effect] ?: return
        if (id !in loadedIds) return
        runCatching {
            pool.play(id, volume, volume, 1, 0, rate)
        }
    }

    override fun release() {
        released = true
        scope.cancel()
        runCatching {
            soundPool?.release()
        }
        soundIds.clear()
        loadedIds.clear()
    }
}
