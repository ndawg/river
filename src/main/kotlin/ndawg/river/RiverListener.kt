package ndawg.river

import java.lang.ref.WeakReference
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

/**
 * A type alias representing the receiver of an event. The handler executes within the context of the
 * invocation (ie `this` refers to the [RiverInvocation] in the lambda), and it passes the event as a parameter
 * (ie `it` refers to the event itself).
 */
typealias RiverReceiver<T> = suspend RiverInvocation<T>.(T) -> Unit

/**
 * A data class which contains all of the necessary elements for a single registered listener.
 */
data class RiverListener<T : Any>(
	internal val type: KClass<T>,
	private val _owner: WeakReference<Any>,
	// TODO remove, it's within a filter
	internal val listening: Set<Any>,
	internal val priority: Int = 0,
	private val once: Boolean = false,
	private val filters: List<(RiverInvocation<T>) -> Boolean>,
	private val consumer: RiverReceiver<T>
) {
	
	val owner get() = _owner.get()
	
	/**
	 * @param invocation The event invocation to check. This should almost certainly be of the type `RiverInvocation<T>`.
	 * @return Whether or not this listener should receive the given event.
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

// TODO immutable instead probably
/**
 * A builder for listeners. This is the main entry-point for creating listeners and should never be
 * worked around. All [listen] methods use this class.
 */
class RiverListenerBuilder<T : Any>(private val manager: River, val type: KClass<T>) {
	
	private var owner: Any = manager
	private val involving = mutableSetOf<Any>()
	private var once = false
	private var priority: Int = 0
	val filters = mutableSetOf<(RiverInvocation<T>) -> Boolean>()
	
	/**
	 * Specifies the owner of the listener being built. This does not impact
	 * the receiving of the event, but allows shutdown by owner.
	 *
	 * @param owner The owner of the listener.
	 * @return This builder instance.
	 */
	fun from(owner: Any): RiverListenerBuilder<T> {
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
	fun to(vararg objects: Any): RiverListenerBuilder<T> {
		return to(objects.toList())
	}
	
	/**
	 * Specifies the objects that should be involved in any event received by
	 * this listener.
	 *
	 * @param objects The objects that should be involved.
	 * @return This builder instance.
	 */
	fun to(objects: Collection<Any>): RiverListenerBuilder<T> {
		involving.addAll(objects.map {
			manager.mappers.identity(it) ?: it
		})
		return this
	}
	
	fun involving(builder: InvolvementBuilder.() -> InvolvementFilter): RiverListenerBuilder<T> {
		builder.invoke(InvolvementBuilder { objs ->
			objs.mapTo(mutableSetOf()) {
				manager.mappers.identity(it) ?: it
			}
		})
		return this
	}
	
	/**
	 * Adds a filter to this listener. The listener will only receive an event if
	 * the given condition returns true.
	 *
	 * @param filter The predicate that should return true if the listener wants the event,
	 * and false otherwise.
	 * @return This builder instance.
	 */
	fun where(filter: (RiverInvocation<T>) -> Boolean): RiverListenerBuilder<T> {
		filters.add(filter)
		return this
	}
	
	/**
	 * Assigns the priority of this listener, which determines when it will receive the event
	 * with respect to other listeners. High values are executed before low values. The default
	 * priority is zero.
	 */
	fun priority(priority: Int): RiverListenerBuilder<T> {
		this.priority = priority
		return this
	}
	
	/**
	 * Configures the listener to be unregistered after receiving a
	 * single event.
	 *
	 * @return This builder instance.
	 */
	fun once(): RiverListenerBuilder<T> {
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
	fun on(handler: RiverReceiver<T>): RiverListener<T> {
		// Hook in the filter for making sure the event involves all objects specified
		if (involving.isNotEmpty())
			filters.add(generateInvolvementFilter(involving))
		
		val listener = build(handler)
		check(manager.register(listener)) {
			"Listener was not registered"
		}
		return listener
	}
	
	fun build(handler: RiverReceiver<T>): RiverListener<T> {
		return RiverListener(type, WeakReference(owner), involving, priority, once, filters.toList(), handler)
	}
	
}

/**
 * A filter to represent anything that can prevent a RiverListener from
 * receiving an event instance.
 */
interface RiverFilter<T : Any> {
	fun shouldReceive(invocation: RiverInvocation<T>): Boolean
}

/**
 * A simple abstraction on a RiverFilter that filters based on objects that are
 * involved in the RiverInvocation.
 */
interface InvolvementFilter: RiverFilter<Any> {
	
	fun containsRequired(involved: Set<Any>): Boolean
	
	override fun shouldReceive(invocation: RiverInvocation<Any>) =
		containsRequired(invocation.involved)
	
}

/**
 * Combines multiple InvolvementFilter instances into one.
 */
class InvolvementFilters(val filters: List<InvolvementFilter>): InvolvementFilter {
	override fun containsRequired(involved: Set<Any>): Boolean {
		return filters.all { it.containsRequired(involved) }
	}
}

class InvolvementBuilder(val identityMapper: (Set<Any>) -> Set<Any>) {
	
	private val filters = mutableListOf<InvolvementFilter>()
	
	fun all(objs: Collection<Any>): InvolvementFilter = AllInvolved(identityMapper(objs.toSet()))
	fun any(objs: Collection<Any>): InvolvementFilter = AnyInvolved(identityMapper(objs.toSet()))
	
	fun all(vararg objs: Any): InvolvementFilter = all(objs.toSet())
	fun any(vararg objs: Any): InvolvementFilter = any(objs.toSet())
	
	fun build() = InvolvementFilters(filters)
	
	fun InvolvementFilter.plus(other: InvolvementFilter): InvolvementFilter {
		return this
	}
	
}

class AnyInvolved(val objs: Set<Any>): InvolvementFilter {
	override fun containsRequired(involved: Set<Any>) =
		objs.any { it in involved }
}

class AllInvolved(val objs: Set<Any>): InvolvementFilter {
	override fun containsRequired(involved: Set<Any>) =
		involved.containsAll(objs)
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