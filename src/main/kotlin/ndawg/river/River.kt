package ndawg.river

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.newSingleThreadContext
import ndawg.log.log
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Main event loop class.
 */
class River : CoroutineScope {
	
	private val executor = newSingleThreadContext("river")
	private val mappers = RiverTypeMappers()
	private val submaps = RiverTypeMappers()
	private val listeners: MutableSet<RiverListener<Any>> = Collections.newSetFromMap(ConcurrentHashMap<RiverListener<Any>, Boolean>())
	
	override val coroutineContext: CoroutineContext
		get() = executor
	
	/**
	 * Dispatches the event to all registered listeners that want it. Dispatching is done
	 * on executor thread.
	 *
	 * @param event The event to dispatch.
	 */
	fun submit(event: Any) = async(executor) {
		// TODO evaluate spawning of child coroutines within invocation
		val inv = RiverInvocation(this, event, getInvolved(event))
		val wants = mutableSetOf<RiverListener<Any>>()
		
		// Find listeners that are interested in the event.
		listeners.forEach {
			try {
				if (it.wants(inv))
					wants.add(it)
			} catch (e: Throwable) {
				log().error("Listener $e produced error while checking $event", e)
			}
		}
		
		// Sort by priority then dispatch in order.
		wants.sortedBy { -it.priority }.forEach {
			try {
				log().trace("Dispatching $event to $it")
				it.invoke(inv)
			} catch (e: Throwable) {
				log().error("Listener $it produced error while handling $event", e)
			}
		}
	}
	
	/**
	 * Objects are considered to be involved in an event if they are relevant at all.
	 * Consider a generic chat message: there is a channel where the message was sent to, an author
	 * who sent the message, etc.
	 *
	 * @param event The event to get a set of objects for.
	 * @return All the objects involved in the event.
	 */
	fun getInvolved(event: Any): Set<Any> {
		val found = mutableSetOf<Any>()
		
		found.addAll(mappers.map(event))
		val sub = mutableSetOf<Any>()
		found.forEach { sub.addAll(submaps.map(it)) }
		found.addAll(sub)
		
		if (!found.isEmpty())
			log().debug("Event $event yielded $found")
		
		return found
	}
	
	/**
	 * Registers a new listener instance. This method is not synchronized with event
	 * dispatching.
	 *
	 * @param listener The listener to register.
	 */
	fun register(listener: RiverListener<*>) {
		listeners.add(listener as RiverListener<Any>)
	}
	
	/**
	 * Unregisters a listener instance. This method is not synchronized with event
	 * dispatching.
	 *
	 * @param listener The listener to unregister.
	 */
	fun unregister(listener: RiverListener<*>) {
		listeners.remove(listener)
	}
	
	/**
	 * Unregisters listeners by its owner. This method is not synchronized with event
	 * dispatching.
	 *
	 * @param owner The owner to unregister by.
	 * @return Whether anything was unregistered.
	 */
	fun unregister(owner: Any?): Boolean {
		return listeners.removeIf {
			(owner != null && it.owner == owner)
		}
	}
	
	/**
	 * Establishes a mapping of an event object. The given function will be called when an event
	 * of the given type (or subtype) is dispatched. The function should supply objects that were
	 * directly involved in the event.
	 *
	 * @param mapper The function that yields objects involved in the event.
	 */
	fun <T : Any> map(type: Class<T>, mapper: (T) -> Set<Any>) {
		mappers.add(type) {
			if (!type.isInstance(it)) throw IllegalArgumentException("Invalid event type given to mapper, expected type of $type and received $it")
			@Suppress("UNCHECKED_CAST")
			mapper(it as T)
		}
	}
	
	/**
	 * Establishes a mapping of an event object. The given function will be called when an event
	 * of the given type (or subtype) is dispatched. The function should supply objects that were
	 * directly involved in the event.
	 *
	 * @param mapper The function that yields objects involved in the event.
	 */
	fun <T : Any> submap(type: Class<T>, mapper: (T) -> Set<Any>) {
		mappers.add(type) {
			if (!type.isInstance(it)) throw IllegalArgumentException("Invalid event type given to mapper, expected type of $type and received $it")
			@Suppress("UNCHECKED_CAST")
			mapper(it as T)
		}
	}
	
	/**
	 * Establishes a mapping of an event object. The given function will be called when an event
	 * of the given type (or subtype) is dispatched. The function should supply objects that were involved
	 * in the event.
	 *
	 * @param mapper The function that yields objects involved in the event.
	 */
	inline fun <reified T : Any> map(noinline mapper: (T) -> Set<Any>) = map(T::class.java, mapper)
	
	/**
	 * Establishes a mapping of an event object. The given function will be called when an event
	 * of the given type (or subtype) is dispatched. The function should supply objects that were involved
	 * in the event.
	 *
	 * @param mapper The function that yields objects involved in the event.
	 */
	inline fun <reified T : Any> submap(noinline mapper: (T) -> Set<Any>) = submap(T::class.java, mapper)
	
}