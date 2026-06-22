package com.fuse.ads

/**
 * ADS-1 — a configurable, fully deterministic [AdProvider] for tests/previews.
 *
 * Lives in `commonMain` (not `commonTest`) so the feature stories (ADS-2/3/4) can inject it from
 * their own test source sets and from previews without depending on `:shared` test fixtures. It
 * holds NO SDK reference, so it runs on JVM and Kotlin/Native alike.
 *
 * ## What it does
 *  - **Scripted results.** [scriptShow] queues per-format outcomes that [show] returns in order;
 *    once the queue for a format is empty, [defaultResult] (per format) is returned. This lets a
 *    feature test simulate `Rewarded` vs `NoFill` vs `Dismissed` vs `Failed` deterministically.
 *  - **Load control.** [load] returns [loadSucceeds] (default `true`) and, on success, marks that
 *    format ready so [isReady] is `true` until a [show] consumes it — mirroring the real cache.
 *  - **Call recording.** Every call is recorded so a test can assert "an ad of format X was
 *    requested / shown": see [initializeCount], [loadCalls], [showCalls], [calls].
 *
 * All mutation is single-threaded test usage; not synchronised.
 *
 * ### Example (ADS-2 revive)
 * ```
 * val ads = FakeAdProvider().apply { scriptShow(AdFormat.REWARDED, AdResult.Rewarded) }
 * // …drive the game-over revive flow…
 * assertTrue(AdFormat.REWARDED in ads.loadCalls)   // an ad was requested
 * assertEquals(1, lives)                            // reward granted only on Rewarded
 * ```
 */
class FakeAdProvider(
    /** What [load] returns (and whether it marks the format ready). Default: loads succeed. */
    var loadSucceeds: Boolean = true,
) : AdProvider {

    /** Per-format result returned by [show] when its scripted queue is empty. */
    private val defaults: MutableMap<AdFormat, AdResult> = mutableMapOf(
        AdFormat.REWARDED to AdResult.Rewarded,
        AdFormat.INTERSTITIAL to AdResult.Completed,
    )

    private val scripted: MutableMap<AdFormat, ArrayDeque<AdResult>> = mutableMapOf()
    private val ready: MutableSet<AdFormat> = mutableSetOf()

    /** A recorded call against the fake. */
    sealed interface Call {
        data object Initialize : Call
        data class Load(val format: AdFormat) : Call
        data class Show(val format: AdFormat) : Call
    }

    /** Every call in order — for fine-grained assertions on the load→show sequence. */
    val calls: MutableList<Call> = mutableListOf()

    /** How many times [initialize] was called. */
    var initializeCount: Int = 0
        private set

    /** Formats passed to [load], in order (duplicates kept). */
    val loadCalls: MutableList<AdFormat> = mutableListOf()

    /** Formats passed to [show], in order (duplicates kept). */
    val showCalls: MutableList<AdFormat> = mutableListOf()

    /** Queues [results] for [format]; [show] returns them in order before falling back to the default. */
    fun scriptShow(format: AdFormat, vararg results: AdResult) {
        scripted.getOrPut(format) { ArrayDeque() }.addAll(results)
    }

    /** Sets the fallback [result] returned by [show] for [format] once its scripted queue drains. */
    fun setDefaultResult(format: AdFormat, result: AdResult) {
        defaults[format] = result
    }

    override fun initialize() {
        initializeCount++
        calls += Call.Initialize
    }

    override suspend fun load(format: AdFormat): Boolean {
        loadCalls += format
        calls += Call.Load(format)
        if (loadSucceeds) ready += format
        return loadSucceeds
    }

    override fun isReady(format: AdFormat): Boolean = format in ready

    override suspend fun show(format: AdFormat): AdResult {
        showCalls += format
        calls += Call.Show(format)
        if (format !in ready) return AdResult.NotReady
        ready -= format // a show consumes the loaded ad, like the real providers
        val queue = scripted[format]
        return if (queue != null && queue.isNotEmpty()) {
            queue.removeFirst()
        } else {
            defaults.getValue(format)
        }
    }
}
