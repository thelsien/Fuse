package com.fuse.presentation

import com.fuse.domain.GetGreetingUseCase

/**
 * Presentation layer — MVI state holders / reducers / intents (GameStore, etc.).
 *
 * For FND-3 this sample presenter pulls a value through the injected use case so
 * the UI has something resolved-via-DI to render, proving the chain
 * data -> domain -> presentation -> ui. Bound in
 * [com.fuse.di.presentationModule].
 */
class SamplePresenter(private val getGreeting: GetGreetingUseCase) {
    fun greetingText(): String = getGreeting()
}
