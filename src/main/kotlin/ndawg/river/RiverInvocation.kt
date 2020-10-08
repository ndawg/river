package ndawg.river

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * A single event invocation instance, which exists for every event submitted
 * to the event manager. This object encapsulates the data relating to the event
 * so that it is thread safe, cached, and accessible by the event receiver.
 * This class is also used as the body of listeners.
 */
data class RiverInvocation<out T : Any>(
	val manager: River,
	val scope: CoroutineScope,
	val event: T,
	val involved: Set<Any>,
	val data: RiverData = RiverData()
) : CoroutineScope by scope {
	
	/**
	 * Discards this event, preventing further propagation to additional listeners, without failing the dispatching.
	 */
	fun discard(): Nothing = throw DiscardException()
	
	/**
	 * Discards this event, preventing further propagation to additional listeners, without failing the dispatching.
	 */
	fun discard(reason: String): Nothing = throw DiscardException(reason)
	
}

/**
 * The exception thrown when an invocation calls [RiverInvocation.discard].
 */
class DiscardException(reason: String? = null) : CancellationException(reason)

/**
 * Data attached to a single dispatch instance that can be mutated by individual listeners.
 * This data is attached to a single invocation, and returned in [RiverResult], which allows an event
 * dispatch to also act like a data pipeline by monitoring what data is produced.
 *
 * Data is organized by two keys: the type of data, and the name (which is optional). If no name is provided
 * then the name is null. No data objects can have the same pair of keys.
 */
class RiverData(
	private val entries: MutableMap<DataKey<*>, Any> = mutableMapOf()
) {
	
	private fun add(key: DataKey<*>, value: Any) {
		require(key !in entries) { "A data entry with the key $key already exists" }
		entries[key] = value
	}
	
	/**
	 * Stores the given data object.
	 */
	fun put(data: Any) = add(DataKey(data::class), data)
	
	/**
	 * Stores the given data object and associates it with the given name. It can only be
	 * retrieved with the correct type and name.
	 */
	fun put(name: String, data: Any) = add(DataKey(data::class, name), data)
	
	@Suppress("UNCHECKED_CAST")
	private fun <T : Any> retrieve(type: KClass<T>, name: String?): T? = entries.entries.find {
		it.key.type == type && it.key.name == name
	}?.value?.let { it as T }
	
	/**
	 * Retrieves a stored data object using the given type and a `null` name. This will only match stored
	 * items that have a `null` name. This method will throw [NoSuchElementException] if no element is stored.
	 */
	fun <T : Any> get(type: KClass<T>): T = retrieve(type, null) ?: throw NoSuchElementException()
	
	/**
	 * Retrieves a stored data object using the given type and the given name.
	 * This method will throw [NoSuchElementException] if no element is stored.
	 */
	fun <T : Any> get(name: String, type: KClass<T>): T = retrieve(type, name) ?: throw NoSuchElementException()
	
	/**
	 * Retrieves a stored data object using the given type and a `null` name. This will only match stored
	 * items that have a `null` name. This method will return null if no element is stored.
	 */
	fun <T : Any> find(type: KClass<T>): T? = retrieve(type, null)
	
	/**
	 * Retrieves a stored data object using the given type and the given name.
	 * This method will return null if no element is stored.
	 */
	fun <T : Any> find(name: String, type: KClass<T>): T? = retrieve(type, name)
	
	/**
	 * Retrieves a stored data object using the given type and a `null` name. This will only match stored
	 * items that have a `null` name. This method will throw [NoSuchElementException] if no element is stored.
	 */
	inline fun <reified T : Any> get() = get(T::class)
	
	/**
	 * Retrieves a stored data object using the given type and the given name.
	 * This method will throw [NoSuchElementException] if no element is stored.
	 */
	inline fun <reified T : Any> get(name: String) = get(name, T::class)
	
	/**
	 * Retrieves a stored data object using the given type and a `null` name. This will only match stored
	 * items that have a `null` name. This method will return null if no element is stored.
	 */
	inline fun <reified T : Any> find() = find(T::class)
	
	/**
	 * Retrieves a stored data object using the given type and the given name.
	 * This method will return null if no element is stored.
	 */
	inline fun <reified T : Any> find(name: String) = find(name, T::class)
	
	/**
	 * The key used to represent an item being stored.
	 */
	data class DataKey<T : Any>(val type: KClass<out T>, val name: String? = null)
	
	override fun toString(): String {
		return entries.toString()
	}
}