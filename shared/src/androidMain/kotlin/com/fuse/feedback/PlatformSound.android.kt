package com.fuse.feedback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * FEL-5 (Android) — binds [Sound] backed by synthesised sine bursts via [AudioTrack].
 *
 * ## Why synth, not an asset
 * The "rises in pitch with the tile value" rule wants a continuous range of pitches. Rather
 * than bundle a sample and re-pitch it (a `SoundPool` asset that has to be packaged for both
 * platforms and can fail to load), we synthesise a short sine burst at the exact frequency
 * the pure [SoundCoordinator.mergePitch] dictates. Nothing to package, nothing to fail to
 * load, and the pitch is precise. Each cue is a tiny PCM buffer written to a one-shot
 * [AudioTrack] in `MODE_STATIC`.
 *
 * ## Defensive
 * Any audio failure (no output device, track init failure on an emulator, busy audio) is
 * swallowed via `runCatching`: a sound is best-effort and must never crash the game. If we
 * cannot even build the renderer we bind [NoOpSound].
 */
actual val platformSoundModule: Module = module {
    single<Sound> {
        runCatching { AndroidSound() }.getOrDefault(NoOpSound)
    }
}

/**
 * [AudioTrack]-backed [Sound] synthesising short tones:
 *  - [mergeTone] — a single sine burst whose frequency rises with [pitch] (the climbing tone).
 *  - [milestoneSting] — a quick two-note rising arpeggio (a richer, distinct cue).
 *  - [winSting] — a three-note rising fanfare (the biggest cue).
 *
 * Buffers are 16-bit mono PCM at [SAMPLE_RATE] with a short exponential decay envelope so the
 * note doesn't click or sustain. Every playback is wrapped in `runCatching` — a failed write
 * or a busy track is silently ignored.
 */
private class AndroidSound : Sound {

    override fun mergeTone(pitch: Float) {
        val freq = lerpFreq(pitch)
        play(tone(freq, TONE_MILLIS))
    }

    override fun milestoneSting() {
        // Two ascending notes near the top of the range — distinct from a single merge tone.
        play(arpeggio(listOf(STING_BASE_HZ, STING_BASE_HZ * 1.5f), NOTE_MILLIS))
    }

    override fun winSting() {
        // Three ascending notes — a small fanfare, the biggest cue.
        play(arpeggio(listOf(WIN_BASE_HZ, WIN_BASE_HZ * 1.25f, WIN_BASE_HZ * 1.5f), NOTE_MILLIS))
    }

    /** Map normalised pitch [0,1] to an audible frequency span (low → high). */
    private fun lerpFreq(pitch: Float): Float {
        val p = pitch.coerceIn(0f, 1f)
        return MIN_HZ + (MAX_HZ - MIN_HZ) * p
    }

    /** Synthesise one decaying sine note of [freq] Hz lasting [millis] ms into a PCM buffer. */
    private fun tone(freq: Float, millis: Int): ShortArray {
        val count = (SAMPLE_RATE * millis / 1000.0).toInt().coerceAtLeast(1)
        val out = ShortArray(count)
        val twoPiF = 2.0 * PI * freq
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            // Exponential decay so the note rings then fades (no click on stop).
            val env = exp(-t * DECAY_PER_SEC)
            val sample = sin(twoPiF * t) * env * AMPLITUDE
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Concatenate several [tone] notes into one buffer (a quick rising arpeggio). */
    private fun arpeggio(freqs: List<Float>, noteMillis: Int): ShortArray {
        val notes = freqs.map { tone(it, noteMillis) }
        val total = notes.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (note in notes) {
            note.copyInto(out, offset)
            offset += note.size
        }
        return out
    }

    /** Write [pcm] to a fresh one-shot static [AudioTrack] and play it. Best-effort. */
    private fun play(pcm: ShortArray) {
        runCatching {
            val sizeBytes = pcm.size * 2
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                sizeBytes,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return@runCatching
            }
            track.write(pcm, 0, pcm.size)
            // Release the one-shot track once it finishes so we don't leak; the buffer is tiny.
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) {
                        runCatching { t?.stop() }
                        runCatching { t?.release() }
                    }
                    override fun onPeriodicNotification(t: AudioTrack?) {}
                },
            )
            track.play()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100

        /** Merge-tone frequency span (Hz). Low merge → ~330 Hz, top tile → ~990 Hz. */
        const val MIN_HZ = 330f
        const val MAX_HZ = 990f

        const val STING_BASE_HZ = 660f
        const val WIN_BASE_HZ = 523f

        const val TONE_MILLIS = 90
        const val NOTE_MILLIS = 80

        const val AMPLITUDE = 0.35
        const val DECAY_PER_SEC = 14.0
    }
}
