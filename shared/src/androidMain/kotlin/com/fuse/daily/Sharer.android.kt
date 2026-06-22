package com.fuse.daily

import android.content.Context
import android.content.Intent
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DLY-7 (Android) — binds [Sharer] backed by an `ACTION_SEND` chooser.
 *
 * The `Context` is resolved from the Koin graph (contributed by
 * `androidContext(this@FuseApplication)`), mirroring `platformSettingsModule` /
 * `platformHapticsModule`. Bound as a `single` — stateless, one is enough.
 */
actual val platformSharerModule: Module = module {
    single<Sharer> { AndroidSharer(context = get()) }
}

/**
 * `ACTION_SEND` (`text/plain`) [Sharer], wrapped in [Intent.createChooser] so the user picks
 * the target app, and launched with `FLAG_ACTIVITY_NEW_TASK` so it works from the *application*
 * context (no Activity reference needed — the Daily screen resolves this via Koin).
 *
 * Fully defensive: the whole launch is wrapped in `runCatching` so a device with nothing able
 * to handle the share (or any other failure) never crashes the game — sharing is best-effort.
 */
private class AndroidSharer(private val context: Context) : Sharer {
    override fun share(text: String) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(send, null).apply {
                // Required because we launch from the application Context, not an Activity.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
