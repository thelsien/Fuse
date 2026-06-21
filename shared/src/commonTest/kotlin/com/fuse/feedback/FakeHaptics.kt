package com.fuse.feedback

/**
 * FEL-4 test double: a [Haptics] that records every call in order, so a test can assert
 * exactly which feedback fired (and that NONE fired when haptics are disabled).
 */
class FakeHaptics : Haptics {
    /** Each call appended in order: "tick", "thunk", or "buzz". */
    val calls: MutableList<String> = mutableListOf()

    override fun tick() { calls += "tick" }
    override fun thunk() { calls += "thunk" }
    override fun buzz() { calls += "buzz" }
}
