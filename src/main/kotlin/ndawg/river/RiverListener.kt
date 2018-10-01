package ndawg.river

import ndawg.log.log

data class RiverListener<T : Any>(val type: Class<T>,
                                  val owner: Any,
                                  val listening: Set<Any>,
                                  val priority: Int = 0,
                                  private val once: Boolean = false,
                                  private val filters: List<(RiverInvocation<T>) -> Boolean>,
                                  private val consumer: suspend (RiverInvocation<T>) -> Unit) {
	
	/**
	 * @param event The event to check.
	 * @return Whether or not this Listener should receive the given event.
	 */
	fun wants(event: RiverInvocation<*>): Boolean {
		return this.type.isAssignableFrom(event.event::class.java) && !filters.any { !it.invoke(event as RiverInvocation<T>) }
	}
	
	/**
	 * Calls the handler with the given invocation. This function is suspending, as is the
	 * consumer of the event.
	 */
	suspend fun invoke(invocation: RiverInvocation<T>) {
		if (once) invocation.manager.unregister(this)
		this.consumer.invoke(invocation)
	}
	
}

class RiverListenerBuilder(val manager: River) {
	
	var owner: Any? = null
	val involving = mutableSetOf<Any>()
	var once = false
	var priority: Int = 0
	
	/**
	 * Specifies the owner of the listener being built. This does not impact
	 * the receiving of the event, but allows shutdown by owner.
	 *
	 * @param owner The owner of the EventListener.
	 * @return This builder instance.
	 */
	fun from(owner: Any): RiverListenerBuilder {
		this.owner = owner
		return this
	}
	
	/**
	 * Specifies the objects that should be involved in any event received by
	 * this listener.
	 *
	 * @param objects The objects that should be involved.
	 * @return This builder instance.
	 */
	fun to(vararg objects: Any): RiverListenerBuilder {
		objects.forEach { involving += it }
		return this
	}
	
	/**
	 * Specifies the objects that should be involved in any event received by
	 * this listener.
	 *
	 * @param objects The objects that should be involved.
	 * @return This builder instance.
	 */
	fun to(objects: Collection<Any>): RiverListenerBuilder {
		objects.forEach { involving += it }
		return this
	}
	
	/**
	 * Assigns the priority of this listener, which determines when it will receive the event
	 * with respect to other listeners. High values are executed before low values. The default
	 * priority is zero.
	 */
	fun priority(priority: Int) {
		this.priority = priority
	}
	
	/**
	 * Assigns the priority of this listener, which determines when it will receive the event
	 * with respect to other listeners. High values are executed before low values. The default
	 * priority is zero.
	 */
	fun priority(priority: RiverPriority) {
		this.priority = priority.value
	}
	
	/**
	 * Configures the listener to be unregistered after receiving a
	 * single event.
	 *
	 * @return This builder instance.
	 */
	fun once(): RiverListenerBuilder {
		this.once = true
		return this
	}
	
	/**
	 * The final step in the builder process, which registers this listener.
	 *
	 * @param handler The handler which will receive the event. This object will be delegating
	 * from the event invocation, and receive the event as the parameter for convenience.
	 * @return The registered listener instance.
	 */
	inline fun <reified T : Any> on(noinline handler: suspend RiverInvocation<T>.(T) -> Unit): RiverListener<T> {
		val filters = mutableListOf<(RiverInvocation<T>) -> Boolean>()
		
		// Hook in the filter for making sure the event involves all objects specified
		if (involving.isNotEmpty())
			filters.add(generateInvolvementFilter(involving))
		
		log().info { "Registering listener {type=${T::class.java}, owner=${this}}" }
		
		val listener = RiverListener(T::class.java, owner ?: this, involving, priority, once, filters, transformHandler(handler))
		manager.register(listener)
		return listener
	}
	
}

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
                                            once: Boolean = false,
                                            noinline handler: suspend RiverInvocation<T>.(T) -> Unit): RiverListener<T> {
	return RiverListener(T::class.java, from, to.toSet(), priority, once, listOf(generateInvolvementFilter(to)), transformHandler(handler))
}

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
                                          noinline handler: suspend RiverInvocation<T>.(T) -> Unit): RiverListener<T> {
	return listener(from, to, priority, once, handler).also { this.register(it) }
}

/**
 * Begins construction of a listener builder. [RiverListenerBuilder.on] must be
 * called for the builder to be registered.
 */
fun River.listen(): RiverListenerBuilder = RiverListenerBuilder(this)

/**
 * Creates a filter to make sure the given objects are involved in the event.
 */
fun <T : Any> generateInvolvementFilter(objects: Collection<Any>): (RiverInvocation<T>) -> Boolean = {
	if (objects.isEmpty()) true else it.involved.containsAll(objects)
}

/**
 * Transforms a given handler to receive an event invocation.
 */
fun <T : Any> transformHandler(handler: suspend RiverInvocation<T>.(T) -> Unit): suspend (RiverInvocation<T>) -> Unit {
	return { ev ->
		handler.invoke(ev, ev.event)
	}
}

// TODO in the future, this could probably be an inline class
enum class RiverPriority(val value: Int = 0) {
	LAST(-100),
	LOW(-10),
	NORMAL,
	HIGH(10),
	FIRST(100)
}