package com.fuse.feedback

/**
 * FEL-5 test double: a [Sound] that records every call in order (and the pitch passed to
 * each [mergeTone]), so a test can assert exactly which audio fired, at what pitch, and that
 * NONE fired when sound is muted. Mirrors [FakeHaptics].
 */
class FakeSound : Sound {
    /** Each call appended in order: "mergeTone", "milestoneSting", or "winSting". */
    val calls: MutableList<String> = mutableListOf()

    /** The pitch passed to each [mergeTone], in call order (one entry per mergeTone). */
    val tonePitches: MutableList<Float> = mutableListOf()

    override fun mergeTone(pitch: Float) {
        calls += "mergeTone"
        tonePitches += pitch
    }

    override fun milestoneSting() { calls += "milestoneSting" }
    override fun winSting() { calls += "winSting" }
}
