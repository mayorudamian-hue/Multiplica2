package com.example

import com.example.game.SoundEffect
import com.example.game.SoundSynthesizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundSynthesizerTest {

    @Test
    fun `synthesizes valid non-empty PCM WAV header for every game sound effect`() {
        for (effect in SoundEffect.entries) {
            val wav = SoundSynthesizer.synthesizeWav(effect)
            assertTrue("WAV should be longer than 44-byte header for $effect", wav.size > 44)

            val riff = String(wav, 0, 4, Charsets.US_ASCII)
            val wave = String(wav, 8, 4, Charsets.US_ASCII)
            val fmt = String(wav, 12, 4, Charsets.US_ASCII)
            val data = String(wav, 36, 4, Charsets.US_ASCII)

            assertEquals("RIFF", riff)
            assertEquals("WAVE", wave)
            assertEquals("fmt ", fmt)
            assertEquals("data", data)

            // Ensure non-silent audio payload
            val hasSignal = (44 until wav.size step 2).any { idx ->
                val lo = wav[idx].toInt() and 0xFF
                val hi = wav[idx + 1].toInt()
                val sample = (hi shl 8) or lo
                sample != 0
            }
            assertTrue("WAV payload for $effect should contain non-zero PCM samples", hasSignal)
        }
    }
}
