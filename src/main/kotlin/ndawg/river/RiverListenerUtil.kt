package ndawg.river

import kotlin.reflect.KClass

/**
 * Constructs an event listener with the given properties without registering it.
 *
 * @param from Specifies the owner of the listener. This does not impact
 * the receiving of the event, but allows un-registration by owner via [River.unregister]
 * @param to The objects that should be involved in any event received by
 * this listener.
 * @param priority Determines when the listener will receive the event
 * with respect to other listeners. High values are executed before low values. The default
 * priority is zero.
 * @param once Configures the listener to be unregistered after receiving a
 * single event.
 * @param handler The handler which will receive the event. This object will be delegating
 * from the event invocation, and receive the event as the parameter for convenience.
 */
inline fun <reified T : Any> River.listener(from: Any = this,
                                            to: Collection<Any> = emptySet(),
                                            priority: Int = 0,
                                            once: Boolean = false): RiverListenerBuilder<T> {
	return RiverListenerBuilder(this, T::class).from(from).to(to).priority(priority).apply {
		if (once) once()
	}
}

/**
 * Begins construction of a listener builder. [RiverListenerBuilder.on] must be
 * called for the builder to be registered.
 */
fun <T : Any> River.listener(type: KClass<T>) = RiverListenerBuilder(this, type)

/**
 * Begins construction of a listener builder. [RiverListenerBuilder.on] must be
 * called for the builder to be registered.
 */
inline fun <reified T : Any> River.listener() = RiverListenerBuilder(this, T::class)

/**
 * Constructs and registers an event listener with the given properties.
 *
 * @param from Specifies the owner of the listener. This does not impact
 * the receiving of the event, but allows un-registration by owner via [River.unregister]
 * @param to The objects that should be involved in any event received by
 * this listener.
 * @param priority Determines when the listener will receive the event
 * with respect to other listeners. High values are executed before low values. The default
 * priority is zero.
 * @param once Configures the listener to be unregistered after receiving a
 * single event.
 * @param handler The handler which will receive the event. This object will be delegating
 * from the event invocation, and receive the event as the parameter for convenience.
 */
inline fun <reified T : Any> River.listen(from: Any = this,
                                          to: Collection<Any> = emptySet(),
                                          priority: Int = 0,
                                          once: Boolean = false,
                                          noinline handler: RiverReceiver<T>): RiverListener<T> {
	return listener<T>(from, to, priority, once).on(handler)
}

/**
 * Creates a filter to make sure the given objects are involved in the event.
 */
fun <T : Any> generateInvolvementFilter(objects: Collection<Any>): (RiverInvocation<T>) -> Boolean = when {
	objects.isEmpty() -> { _ -> true }
	else -> { inv -> inv.involved.containsAll(objects) }
}
