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
inline fun <reified T : Any> River.listener(
	from: Any = this,
	to: Collection<Any> = emptySet(),
	priority: Int = 0,
	once: Boolean = false
): RiverListenerBuilder<T> {
	return RiverListenerBuilder(this, T::class).from(from).to(to).priority(priority).let {
		if (once) it.once() else it
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
inline fun <reified T : Any> River.listen(
	from: Any = this,
	to: Collection<Any> = emptySet(),
	priority: Int = 0,
	once: Boolean = false,
	noinline handler: RiverReceiver<T>
): RiverListener<T> {
	return listener<T>(from=from, to=to, priority=priority, once=once).on(handler)
}

/**
 * An implementation of a [RiverFilter] for the first-class support of [RiverListenerBuilder.on], ie
 * provides support for only triggering a listener when every entry in [objects] is involved in the
 * [RiverInvocation] (via [RiverInvocation.involved]).
 *
 * See also: [RiverListener.listening], which is possible thanks to this implementation.
 */
data class InvolvementFilter<T : Any>(val objects: Set<Any>) : RiverFilter<T> {
	override fun invoke(inv: RiverInvocation<T>) = inv.involved.containsAll(objects.map {
		inv.manager.mappers.identity(it) ?: it
	})
}

/**
 * A convenience property that identifies the objects a RiverListener is waiting on.
 * If no objects are being waited on, an empty set is returned.
 */
val <T : Any> RiverListener<T>.listening: Set<Any>
get() = this.filters.filterIsInstance<InvolvementFilter<T>>().flatMapTo(mutableSetOf() ) { it.objects }