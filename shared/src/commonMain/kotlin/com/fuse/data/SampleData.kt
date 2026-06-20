package com.fuse.data

/**
 * Data layer — repositories and local sources (SQLDelight, multiplatform-settings).
 *
 * For FND-3 this layer only carries the *sample dependency* that proves the Koin
 * graph resolves end to end. [Greeting] is the abstraction; [DefaultGreeting] is
 * the implementation bound in [com.fuse.di.dataModule]. Real repositories arrive
 * in later sprints.
 */
interface Greeting {
    fun greet(): String
}

internal class DefaultGreeting : Greeting {
    override fun greet(): String = "Fuse DI ready"
}
