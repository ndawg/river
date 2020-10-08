package ndawg.river

import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.Closeable
import java.lang.Runnable
import kotlin.reflect.KClass

/**
 * River is a simple and general purpose event system. Any object can be an event, meaning it can
 * be both dispatched and listened for. Dispatching is done using coroutines, and, likewise, event handlers
 * run in a suspension context. Events can be submitted using [submit], and listened to using [listen].
 *
 * When a listener runs a suspending operation, River's coroutine context is freed up to begin processing more
 * events again. This requires some care on the listener's part. It is possible that an assumption made about the
 * application's state at the start of a listener might not remain true throughout. If state consistency is important,
 * then `runBlocking` should be used to block the entire event loop.
 */
@Suppress("EXPERIMENTAL_API_USAGE") // for the executor
class River(private val executor: CoroutineDispatcher? = newSingleThreadContext("river")) {
	
	private val logger = KotlinLogging.logger {}
	internal val mappers = RiverTypeMappers()
	internal val listeners = mutableListOf<RiverListener<out Any>>()
	val scope = executor?.let { CoroutineScope(executor) } ?: CoroutineScope(SupervisorJob())
	
	/**
	 * Dispatches the event to all registered listeners that want it. This method dispatches
	 * immediately, and suspends on each registered handler as necessary. Errors from handlers are
	 * re-thrown directly. Listeners can prevent propagation of the event by using the
	 * [RiverInvocation.discard] method.
	 *
	 * @param event The event to dispatch.
	 */
	suspend fun <T : Any> submit(event: T): RiverResult<T> {
		return if (executor != null) {
			withContext(executor) {
				submit0(event)
			}
		} else {
			submit0(event)
		}
	}
	
	/**
	 * Dispatches the event to all registered listeners that want it. This method dispatches
	 * asynchronously and returns immediately. Errors from handlers are caught in the Deferred
	 * object. If any handler throws an error, dispatching is halted (no more listeners
	 * will receive the event).
	 *
	 * @param event The event to dispatch.
	 */
	@Suppress("DeferredIsResult")
	fun <T : Any> post(event: T): Deferred<RiverResult<T>> {
		return if (executor != null) {
			scope.async(executor) {
				submit0(event)
			}
		} else {
			scope.async {
				submit0(event)
			}
		}
	}
	
	/**
	 * Does the actual dispatching of an event. This method assumes the context has
	 * already been set correctly depending on the value of the [executor].
	 */
	private suspend fun <T : Any> submit0(event: T): RiverResult<T> = coroutineScope {
		val inv = RiverInvocation(this@River, this, event, getInvolved(event))
		val wants = getInterested(inv)
		val received = mutableListOf<RiverListener<T>>()
		
		wants.forEach {
			try {
				logger.debug { "Dispatching $event to $it" }
				received += it
				it.invoke(inv)
				// TODO submit a meta-event for post firing?
			} catch (e: Throwable) {
				// TODO: it could be allowed for other listeners to continue here despite discarding
				if (e is DiscardException)
					return@coroutineScope RiverResult(event, inv, received, e, inv.data)
				throw e
			}
		}
		
		return@coroutineScope RiverResult(event, inv, received, null, inv.data)
	}
	
	/**
	 * Filters through all listeners to find which are interested in the event for the given invocation.
	 * Listeners are considered interested if [RiverListener.wants] returns true. If this method throws an
	 * error, it is suppressed and the logger emits a warning to prevent disruption to dispatching.
	 */
	@Suppress("UNCHECKED_CAST")
	private fun <T : Any> getInterested(inv: RiverInvocation<T>): MutableList<RiverListener<T>> {
		val event = inv.event
		val wants = mutableListOf<RiverListener<T>>()
		
		// TODO synchronize
		listeners.forEach {
			try {
				if (it.wants(inv)) {
					wants.add(it as RiverListener<T>)
					logger.debug { "Listener $it wants $event" }
				}
			} catch (e: Throwable) {
				// Choose not to interrupt if a listener produces an error.
				logger.warn(e) { "Listener $e produced error while checking $event" }
			}
		}
		
		return wants
	}
	
	/**
	 * Objects are considered to be involved in an event if they are relevant at all.
	 * Consider a generic chat message: there is a channel where the message was sent to, an author
	 * who sent the message, etc. All of these details are said to be involved, and listeners can pick
	 * and choose certain properties to listen to. This allows for emulation of a subscriber-publisher
	 * model, but through a system of mapping instead.
	 *
	 * @param event The event to get a set of objects for.
	 * @return All the objects involved in the event.
	 */
	fun getInvolved(event: Any): Set<Any> {
		val found = mappers.map(event)
		
		if (found.isNotEmpty())
			logger.debug { "Event $event yielded $found" }
		
		return found
	}
	
	/**
	 * Shutdowns this River instance, closing the executor and releasing its resources. This
	 * cannot be undone.
	 */
	fun shutdown() {
		listeners.clear()
		if (executor is Closeable)
			executor.close()
	}
	
	/**
	 * Registers a new listener instance. This method is not synchronized with event
	 * dispatching.
	 *
	 * @param listener The listener to register.
	 * @return Whether or not the listener was registered.
	 */
	fun <T : Any> register(listener: RiverListener<T>): Boolean {
		val add = listeners.add(listener)
		if (add) listeners.sortBy { -it.priority }
		return add
	}
	
	/**
	 * Unregisters a listener instance. This method is not synchronized with event
	 * dispatching.
	 *
	 * @param listener The listener to unregister.
	 */
	fun <T : Any> unregister(listener: RiverListener<T>): Boolean {
		return listeners.remove(listener)
	}
	
	/**
	 * Unregisters listeners by its owner. This method is not synchronized with event
	 * dispatching.
	 *
	 * @param owner The owner to unregister by.
	 * @return Whether anything was unregistered.
	 */
	fun unregister(owner: Any): Boolean {
		require(owner != this) { "The River instance cannot be unregistered" }
		return listeners.removeIf { it.owner == owner }
	}
	
	/**
	 * Establishes a mapping of an event object. The given function will be called when an event
	 * of the given type (or subtype) is dispatched. The function should supply objects that were
	 * directly involved in the event.
	 *
	 * @param mapper The function that yields objects involved in the event.
	 */
	@JvmName("mapMulti")
	fun <T : Any> map(type: KClass<T>, mapper: (T) -> Set<Any?>) {
		mappers.addMapper(type, RiverTypeMapper {
			require(type.isInstance(it)) { "Invalid event type given to mapper, expected type of $type and received $it" }
			@Suppress("UNCHECKED_CAST")
			mapper(it as T)
		})
	}
	
	/**
	 * Establishes a mapping of an event object. The given function will be called when an event
	 * of the given type (or subtype) is dispatched. The function should supply objects that were involved
	 * in the event.
	 *
	 * @param mapper The function that yields objects involved in the event.
	 */
	@JvmName("mapMulti")
	inline fun <reified T : Any> map(noinline mapper: (T) -> Set<Any?>) = map(T::class, mapper)
	
	/**
	 * Establishes the identity of a given type of object. Identity is essentially a form of mapping for involvement
	 * that also takes place when a listener is registered.
	 *
	 * **Examples:**
	 * - A user that has a UUID. The UUID would be setup as the identity, so the user object is irrelevant.
	 * - A session object corresponding to a certain user may be created or destroyed throughout an application's lifecycle,
	 * so listening to the session itself isn't a good solution. The session's identity can be specified to be the user's ID.
	 *
	 * This identity correspondence replaces any other mapping for the given type. That is to say that you should probably
	 * not be mapping items that will be directly emitted into the River, but rather subtypes that appear in event data.
	 *
	 * @param type The type of class to add the identifier for. This might also be used for subclasses, if no more exacting
	 * identifier exists.
	 * @param identifier The function that will produce the identity when given the object.
	 */
	fun <T : Any> id(type: KClass<T>, identifier: (T) -> Any) {
		mappers.addIdentity(type, identifier)
	}
	
	/**
	 * Establishes the identity of a given type of object. Identity is essentially a form of mapping for involvement
	 * that also takes place when a listener is registered.
	 *
	 * **Examples:**
	 * - A user that has a UUID. The UUID would be setup as the identity, so the user object is irrelevant.
	 * - A session object corresponding to a certain user may be created or destroyed throughout an application's lifecycle,
	 * so listening to the session itself isn't a good solution. The session's identity can be specified to be the user's ID.
	 *
	 * This identity correspondence replaces any other mapping for the given type. That is to say that you should probably
	 * not be mapping items that will be directly emitted into the River, but rather subtypes that appear in event data.
	 *
	 * @param T The type of class to add the identifier for. This might also be used for subclasses, if no more exacting
	 * identifier exists.
	 * @param identifier The function that will produce the identity when given the object.
	 */
	inline fun <reified T : Any> id(noinline identifier: (T) -> Any) = id(T::class, identifier)
	
}
