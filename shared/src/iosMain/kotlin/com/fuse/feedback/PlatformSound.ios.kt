@file:OptIn(ExperimentalForeignApi::class)

package com.fuse.feedback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * FEL-5 (iOS) — binds [Sound] backed by an [AVAudioEngine] + [AVAudioPlayerNode] that plays
 * short, synthesised sine bursts, mirroring the Android synth approach (no bundled asset).
 *
 * ## Audio session
 * The session is configured `AVAudioSessionCategoryAmbient` so the game's SFX mix politely
 * with other audio (e.g. the user's music keeps playing) and respect the hardware mute
 * switch — correct behaviour for casual game SFX.
 *
 * ## Defensive
 * The Simulator frequently has no working audio; a failed session activation or engine start
 * must not crash. Everything is guarded, and if the engine can't be built/started we bind
 * [NoOpSound].
 */
actual val platformSoundModule: Module = module {
    single<Sound> {
        runCatching { IosSound() }.getOrNull()?.takeIf { it.isReady } ?: NoOpSound
    }
}

/**
 * [AVAudioEngine]-backed [Sound]. We build a small PCM buffer per cue on the fly and schedule
 * it on a single [AVAudioPlayerNode]:
 *  - [mergeTone] — a single decaying sine note whose frequency rises with [pitch].
 *  - [milestoneSting] — a two-note rising arpeggio.
 *  - [winSting] — a three-note rising fanfare.
 *
 * The engine is started once and kept running; scheduling a buffer is cheap and overlapping
 * cues mix naturally on the node.
 */
private class IosSound : Sound {

    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private val format = AVAudioFormat(
        commonFormat = AVAudioPCMFormatFloat32,
        sampleRate = SAMPLE_RATE,
        channels = 1u,
        interleaved = false,
    )

    /** `true` iff the session + engine came up and we can actually play. */
    val isReady: Boolean = runCatching {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryAmbient, null)
        session.setActive(true, null)
        engine.attachNode(player)
        engine.connect(player, engine.mainMixerNode, format)
        engine.startAndReturnError(null)
        player.play()
        true
    }.getOrDefault(false)

    override fun mergeTone(pitch: Float) {
        val freq = MIN_HZ + (MAX_HZ - MIN_HZ) * pitch.coerceIn(0f, 1f)
        schedule(listOf(freq.toDouble()))
    }

    override fun milestoneSting() {
        schedule(listOf(STING_BASE_HZ, STING_BASE_HZ * 1.5))
    }

    override fun winSting() {
        schedule(listOf(WIN_BASE_HZ, WIN_BASE_HZ * 1.25, WIN_BASE_HZ * 1.5))
    }

    /** Build a PCM buffer of the given ascending [freqs] (each a decaying note) and play it. */
    private fun schedule(freqs: List<Double>) {
        if (!isReady) return
        runCatching {
            val perNote = (SAMPLE_RATE * NOTE_SECONDS).toInt()
            val frames = (perNote * freqs.size).toUInt()
            val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames)
                ?: return@runCatching
            buffer.setFrameLength(frames)
            // floatChannelData is `float**`; [0] selects the single (mono) channel buffer.
            val channel = buffer.floatChannelData?.get(0) ?: return@runCatching

            var i = 0
            for (freq in freqs) {
                val twoPiF = 2.0 * PI * freq
                for (n in 0 until perNote) {
                    val t = n.toDouble() / SAMPLE_RATE
                    val env = exp(-t * DECAY_PER_SEC)
                    channel[i] = (sin(twoPiF * t) * env * AMPLITUDE).toFloat()
                    i++
                }
            }

            player.scheduleBuffer(buffer, completionHandler = null)
            if (!player.isPlaying()) player.play()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100.0

        const val MIN_HZ = 330.0
        const val MAX_HZ = 990.0

        const val STING_BASE_HZ = 660.0
        const val WIN_BASE_HZ = 523.0

        const val NOTE_SECONDS = 0.09
        const val AMPLITUDE = 0.35
        const val DECAY_PER_SEC = 14.0
    }
}
