package com.fuse.engine

import kotlinx.serialization.Serializable

/**
 * A minimal deterministic pseudo-random number generator used by the engine.
 *
 * The engine never touches a platform/global random source: every source of
 * chance (tile spawning in ENG-6) is driven through an injected [Rng] so a game
 * is fully reproducible from its seed. Implementations MUST be deterministic and
 * — critically — **identical across Kotlin targets** (JVM and iOS/Native produce
 * the same sequence for the same seed). See [SeededRng] for why that holds.
 *
 * The surface is intentionally tiny and shaped for what spawning needs:
 *  - [nextInt] picks a random empty-cell index: `rng.nextInt(emptyCells.size)`.
 *  - [nextDouble] rolls the 2-vs-4 weighting: `if (rng.nextDouble() < 0.9) 2 else 4`.
 *  - [nextLong] is the raw 64-bit primitive the other two are derived from, and
 *    is occasionally handy directly (e.g. minting a deterministic value).
 */
interface Rng {
    /** A uniformly distributed 64-bit value across the full [Long] range. */
    fun nextLong(): Long

    /**
     * A uniformly distributed `Int` in `[0, bound)`.
     * @throws IllegalArgumentException if [bound] is not positive.
     */
    fun nextInt(bound: Int): Int

    /** A uniformly distributed `Double` in `[0.0, 1.0)`. */
    fun nextDouble(): Double
}

/**
 * A [SplitMix64](https://prng.di.unimi.it/splitmix64.c) generator.
 *
 * Why SplitMix64? It is a fully specified, single-`Long`-state algorithm built
 * entirely from operations whose semantics are pinned by the Kotlin language:
 * `Long` addition/multiplication wrap mod 2^64, `ushr` is an unsigned (logical)
 * shift, and `xor` is bitwise. None of these depend on the platform, so the same
 * [seed] yields a byte-for-byte identical sequence on the JVM and on
 * Kotlin/Native (iOS). That is the cross-platform-determinism guarantee the
 * story requires, and it is pinned by an exact-sequence test ([RngTest]).
 *
 * We deliberately do NOT use `kotlin.random.Random(seed)`: its algorithm is not
 * contractually identical across Kotlin targets, so it cannot be trusted for a
 * replay/sync that must match between Android and iOS.
 *
 * ## Replay / persistence (ENG-9)
 * The entire generator state is the single [state] field. The class is
 * [Serializable], and [state] is exposed so a game snapshot can persist the RNG
 * mid-stream and later resume the *exact* continuation via [fromState]. The
 * companion [SEED_MIX] / [GAMMA] constants are part of the algorithm, not state.
 *
 * Not thread-safe; the engine is single-threaded per game.
 *
 * @param seed the initial seed; any [Long] is valid (including 0 and negatives).
 */
@Serializable
class SeededRng private constructor(
    /**
     * The live 64-bit generator state. Advanced by [GAMMA] on every draw. Exposed
     * (and restorable via [fromState]) so a game can be serialized and resumed at
     * the precise point in the sequence it was paused.
     */
    var state: Long,
    // Unused discriminator so the @Serializable primary ctor differs from the
    // public seed ctor; see the `seed` factory below. Kept private.
    @Suppress("unused") private val marker: Boolean,
) : Rng {

    /** Constructs a generator seeded with [seed]. */
    constructor(seed: Long) : this(state = seed, marker = true)

    override fun nextLong(): Long {
        // Advance state, then run the SplitMix64 finalizer ("mix") on it.
        state += GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    override fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        // Rejection sampling on the low 31 bits removes modulo bias, so the
        // result is uniform over [0, bound) regardless of how bound divides 2^31.
        // Deterministic: the same draws are consumed for the same state, and the
        // arithmetic is platform-independent. The reject condition discards the
        // small tail of [0, 2^31) that does not divide evenly by `bound`.
        while (true) {
            val bits = nextBits31()
            val value = bits % bound
            if (bits - value + (bound - 1) >= 0) return value
        }
    }

    override fun nextDouble(): Double {
        // Use the top 53 bits (the mantissa width of a Double) so every result
        // is an exact multiple of 2^-53 in [0.0, 1.0); standard construction.
        return (nextLong() ushr 11).toDouble() * DOUBLE_UNIT
    }

    /** A non-negative 31-bit value, the raw material for [nextInt]. */
    private fun nextBits31(): Int = (nextLong() ushr 33).toInt()

    companion object {
        // SplitMix64 constants (the "golden gamma" increment and finalizer
        // multipliers), expressed as Long bit-patterns. These ARE the algorithm.
        private const val GAMMA: Long = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        private const val MIX_1: Long = -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        private const val MIX_2: Long = -0x6b2fb644ecceee15L // 0x94D049BB133111EB

        // 2^-53, the spacing of doubles produced by nextDouble(). Written as a
        // literal (not `1.0 / (1L shl 53)`) because Kotlin/Native rejects a
        // `const val` whose initializer isn't a literal constant expression.
        private const val DOUBLE_UNIT: Double = 1.1102230246251565E-16

        /** Restores a generator whose [state] was previously persisted (ENG-9). */
        fun fromState(state: Long): SeededRng = SeededRng(state = state, marker = true)
    }
}
