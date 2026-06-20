package com.fuse.domain

import com.fuse.data.Greeting

/**
 * Domain layer — entities + use cases. Depends on the data layer's abstractions,
 * never on concrete implementations (clean-architecture dependency rule).
 *
 * For FND-3 this is the sample use case that consumes the injected [Greeting],
 * demonstrating cross-layer wiring through Koin. Bound in
 * [com.fuse.di.domainModule].
 */
class GetGreetingUseCase(private val greeting: Greeting) {
    operator fun invoke(): String = greeting.greet()
}
