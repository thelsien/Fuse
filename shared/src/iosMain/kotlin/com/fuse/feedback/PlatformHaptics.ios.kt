package com.fuse.feedback

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * FEL-4 (iOS) — binds [Haptics] backed by UIKit's Taptic-Engine feedback generators.
 *
 * iOS has no Application/Context, and the generators need no extra input, so the iOS Koin
 * start (`MainViewController` → `initKoin()`) needs no changes beyond including this module
 * (mirroring `platformSettingsModule`). On a Simulator the generators exist but produce no
 * physical feedback — the calls are harmless no-ops, so this builds and runs without crashing.
 */
actual val platformHapticsModule: Module = module {
    single<Haptics> { IosHaptics() }
}

/**
 * UIFeedbackGenerator-backed [Haptics]:
 *  - [tick] → light [UIImpactFeedbackGenerator] impact (per-merge tap).
 *  - [thunk] → heavy [UIImpactFeedbackGenerator] impact (milestone).
 *  - [buzz] → [UINotificationFeedbackGenerator] *error* notification (distinct blocked feel).
 *
 * Generators are held and `prepare()`d ahead of firing so the Taptic Engine is warm and the
 * impact lands with minimal latency, per Apple's guidance.
 */
private class IosHaptics : Haptics {
    private val lightImpact =
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val heavyImpact =
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notification = UINotificationFeedbackGenerator()

    override fun tick() {
        lightImpact.prepare()
        lightImpact.impactOccurred()
    }

    override fun thunk() {
        heavyImpact.prepare()
        heavyImpact.impactOccurred()
    }

    override fun buzz() {
        notification.prepare()
        notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
    }
}
