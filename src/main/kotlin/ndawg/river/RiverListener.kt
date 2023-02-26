package ndawg.river

import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

/**
 * A type alias representing the receiver of an event. The handler executes within the context of the
 * invocation (ie `this` refers to the [RiverInvocation] in the lambda), and it passes the event as a parameter
 * (ie `it` refers to the event itself).
 */
typealias RiverReceiver<T> = suspend RiverInvocation<T>.(T) -> Unit

/**
 * An interface representing a filter that decides if an event should be received by
 * a listener.
 */
typealias RiverFilter<T> = (RiverInvocation<T>) -> Boolean

/**
 * A data class which contains all the necessary elements for a single registered listener.
 */
data class RiverListener<T : Any>(
	internal val type: KClass<T>,
	internal val owner: Any,
	internal val priority: Int = 0,
	internal val once: Boolean = false,
	internal val filters: List<RiverFilter<T>>,
	internal val consumer: RiverReceiver<T>
) {
	
	/**
	 * @param invocation The event invocation to check. This should almost certainly be of the type `RiverInvocation<T>`.
	 * @return Whether this listener should receive the given event.
	 */
	fun wants(invocation: RiverInvocation<*>): Boolean {
		@Suppress("UNCHECKED_CAST")
		return this.type.isSuperclassOf(invocation.event::class) && filters.all { it.invoke(invocation as RiverInvocation<T>) }
	}
	
	/**
	 * Calls the handler with the given invocation. This function is suspending, as is the
	 * consumer of the event.
	 */
	suspend fun invoke(invocation: RiverInvocation<T>) {
		if (once) invocation.manager.unregister(this)
		this.consumer.invoke(invocation, invocation.event)
	}
	
}

/**
 * An immutable builder for listeners. This is the main entry-point for creating
 * listeners and should never be bypassed. All [listen] methods use this class.
 */
data class RiverListenerBuilder<T : Any>(
	// "Immutable" properties
	private val manager: River,
	private val type: KClass<T>,
	// Builder properties
	private val owner: Any = manager,
	private val once: Boolean = false,
	private val priority: Int = 0,
	private val filters: Set<RiverFilter<T>> = setOf()
) {
	
	/**
	 * Specifies the owner of the listener being built. This does not impact
	 * the receiving of the event, but allows shutdown by owner.
	 *
	 * @param owner The owner of the listener.
	 * @return A new builder instance with the property applied.
	 */
	fun from(owner: Any) = this.copy(owner = owner)
	
	/**
	 * Specifies the objects that should be involved in any event received by
	 * this listener.
	 *
	 * @param objects The objects that should be involved.
	 * @return A new builder instance with the property applied.
	 */
	fun to(objects: Collection<Any>) = this.where(InvolvementFilter(objects.toSet()))
	
	/**
	 * Specifies the objects that should be involved in any event received by
	 * this listener.
	 *
	 * @param objects The objects that should be involved.
	 * @return This builder instance.
	 */
	fun to(vararg objects: Any) = to(objects.toSet())
	
	/**
	 * Adds a filter to this listener. The listener will only receive an event if
	 * the given condition returns true.
	 *
	 * @param filter The predicate that should return true if the listener wants the event,
	 * and false otherwise.
	 * @return A new builder instance with the property applied.
	 */
	fun where(filter: RiverFilter<T>) = this.copy(filters = filters.plus(filter))
	
	/**
	 * Assigns the priority of this listener, which determines when it will receive the event
	 * with respect to other listeners. High values are executed before low values. The default
	 * priority is zero.
	 *
	 * @return A new builder instance with the property applied.
	 */
	fun priority(priority: Int) = this.copy(priority = priority)
	
	/**
	 * Configures the listener to be unregistered after receiving a
	 * single event.
	 *
	 * @return A new builder instance with the property applied.
	 */
	fun once() = this.copy(once = true)
	
	/**
	 * The final step in the builder process, which registers this listener.
	 *
	 * @param handler The handler which will receive the event. This object will be delegating
	 * from the event invocation, and receive the event as the parameter for convenience.
	 * @return The registered listener instance.
	 */
	fun on(handler: RiverReceiver<T>): RiverListener<T> {
		val listener = build(handler)
		check(manager.register(listener)) {
			"Listener was not registered"
		}
		return listener
	}
	
	internal fun build(handler: RiverReceiver<T>): RiverListener<T> {
		return RiverListener(
			type = type,
			owner = owner,
			priority = priority,
			once = once,
			filters = filters.toList(),
			consumer = handler
		)
	}
	
}

/**
 * A convenience object that provides some built-in priorities. Higher numbers means a higher priority.
 */
object RiverPriority {
	const val LAST = -100
	const val LOW = -10
	const val NORMAL = 0
	const val HIGH = 10
	const val FIRST = 100
}