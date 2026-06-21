package com.fuse.feedback

/**
 * FEL-5 — a thin, intent-named sound-effects service, mirroring [Haptics].
 *
 * As with haptics, the method names describe the *meaning* of the audio cue, not the
 * waveform, so the decision logic ([SoundCoordinator]) stays platform-agnostic and each
 * platform impl is free to map an intent onto whatever native primitive it wants
 * (Android `AudioTrack` synth / iOS `AVAudioEngine`).
 *
 * It is deliberately an interface with no return values: the platform impls are thin,
 * fire-and-forget side effects (see `platformSoundModule`), and a recording [FakeSound]
 * makes the *decision* (which call, at which pitch, when) trivially testable in
 * `commonTest` without touching any platform audio code.
 *
 *  - [mergeTone] — the per-merge "climbing" tone. [pitch] is a normalised step produced by
 *    the PURE [mergePitch] mapping: it rises monotonically with the merged tile value, so
 *    bigger merges sound higher. The impl turns it into a concrete frequency / playback rate.
 *  - [milestoneSting] — a distinct, richer cue when a notable tile is reached (512/1024/2048).
 *  - [winSting] — the celebratory cue on first reaching the win target (2048 / justWon).
 */
interface Sound {
    /**
     * Play the per-merge tone at the given normalised [pitch] (see [mergePitch]); higher
     * value ⇒ higher pitch. [pitch] is in `[0f, 1f]`, where the platform impl maps `0f`
     * to the lowest tone and `1f` to the highest.
     */
    fun mergeTone(pitch: Float)

    /** A distinct sting when a milestone tile (512/1024/2048) is reached this move. */
    fun milestoneSting()

    /** The celebratory sting on first reaching the win target. */
    fun winSting()
}

/**
 * A [Sound] that does nothing — the default wherever real audio is unavailable or unwanted
 * (tests, previews, any path that constructs without Koin, or a device with no usable audio
 * output). Like [NoOpHaptics] it keeps the seam honest: sound is always *invoked* the same
 * way, and "muted / no audio" is a real implementation rather than scattered null checks.
 */
object NoOpSound : Sound {
    override fun mergeTone(pitch: Float) {}
    override fun milestoneSting() {}
    override fun winSting() {}
}
