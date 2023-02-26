package ndawg.river

import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses

/**
 * The definition for a non-generic mapper (note that the implementation in
 * [River.map] provides proper type hints).
 */
@JvmInline // prevent heap allocation
value class RiverTypeMapper<T : Any>(val func: RiverMappingReceiver.(T) -> Unit)

@JvmInline // prevent heap allocation
value class RiverMappingReceiver(private val receiver: (Any?) -> Unit) {
	
	/**
	 * Produce a single object from the event.
	 */
	fun produce(obj: Any?) = this.receiver.invoke(obj)
	
	/**
	 * Produce a list of objects from the event.
	 */
	fun produce(vararg objs: Any?) = objs.forEach(receiver)
	
	/**
	 * Produce a list of objects from the event.
	 */
	fun produce(objs: Collection<Any?>) = objs.forEach(receiver)
	
}

/**
 * Manages mappings for types.
 */
class RiverTypeMappers {
	
	private val mappers = mutableMapOf<KClass<*>, MutableSet<RiverTypeMapper<out Any>>>()
	private val identities = mutableMapOf<KClass<*>, (Any) -> Any>()
	
	/**
	 * Register a new type mapper, which will receive any subtypes of the given type.
	 *
	 * @param type The type of event to receive. The mapper will also receive any
	 * subtypes of the event.
	 * @param mapper The mapper to invoke when an event is received.
	 */
	fun <T : Any> addMapper(type: KClass<T>, mapper: RiverTypeMapper<T>) {
		mappers.computeIfAbsent(type) { mutableSetOf() } += mapper
	}
	
	/**
	 * Registers the identity function for the given type. If another identity function already exists,
	 * an [IllegalArgumentException] will be thrown.
	 *
	 * @param type The type of object to add an identity function for. This does not apply to subtypes.
	 * @param func The function that will receive the type.
	 */
	fun <T : Any> addIdentity(type: KClass<T>, func: (T) -> Any) {
		if (type in identities)
			throw IllegalArgumentException("An identity function for type $type already exists")
		@Suppress("UNCHECKED_CAST")
		identities[type] = func as ((Any) -> Any)
	}
	
	/**
	 * Produces input from all registered mappers, with all null results filtered out.
	 */
	fun map(input: Any): Set<Any> {
		return produce(input)
	}
	
	/**
	 * Produces the identity from the input, returning null if there is no alternative identity
	 * to be provided.
	 */
	fun identity(input: Any): Any? {
		// Check exact type.
		val exact = identities[input::class]
		if (exact != null) return exact(input)
		
		// Check supertypes.
		input::class.allSuperclasses.firstOrNull {
			identities[it] != null
		}?.let {
			return identities[it]!!(input)
		}
		
		return null
	}
	
	/**
	 * Returns mappers that can handle the given type.
	 */
	private fun getPossibleMappers(type: KClass<*>): Set<RiverTypeMapper<*>> {
		val maps = mutableSetOf<RiverTypeMapper<*>>()
		
		// Add the immediate type
		mappers[type]?.let { maps.addAll(it) }
		
		// Add all superclasses
		type.allSuperclasses.forEach { t ->
			mappers[t]?.let { maps.addAll(it) }
		}
		
		return maps
	}
	
	private fun produce(input: Any): Set<Any> {
		val result = mutableSetOf<Any?>()
		
		// A set of elements that have already been mapped, kept track of to avoid attempting multiple/recursive mappings.
		// Ops: contains
		val mapped = mutableSetOf<Any?>()
		// A list of elements that need to be mapped.
		// Ops: remove first (N), add (N)
		val toMap = mutableSetOf<Any?>(input)
		
		// The object that will receive mapped values from producers
		// This is done to prevent creating collections all over the place
		val receiver = RiverMappingReceiver {
			if (it != null) {
				// Store the result, mapping to an identity if necessary
				// O(1) add on set
				result.add(identity(it) ?: it)
				
				// Recursively mapped what was produced, WITHOUT mapping to identity
				// This allows the recursive mapping process to continue, but identity
				// will still be stored in the result.
				// O(1) add on set
				toMap.add(it)
			}
		}
		
		while (toMap.isNotEmpty()) {
			val element = toMap.pop()
			
			// Skip nulls and any elements already mapped.
			// O(1) contains on set
			if (element == null || mapped.contains(element)) {
				continue
			}
			
			// Register it as being mapped already.
			// O(1) add on set
			mapped.add(element)
			
			// Find mappers that can handle the type.
			val eligible = getPossibleMappers(element::class)
			
			eligible.forEach { mapper ->
				// Invoke the function, which will utilize the receiver defined above
				// to funnel items into collections
				@Suppress("UNCHECKED_CAST")
				(mapper as RiverTypeMapper<Any>).func.invoke(receiver, element)
			}
		}
		
		// Ditch null. Only do this once to avoid intermediate filtering.
		result.remove(null)
		@Suppress("UNCHECKED_CAST")
		return result as Set<Any>
	}
	
}

/**
 * Implements a pop operation for a MutableSet in constant time.
 */
private fun <T> MutableSet<T>.pop(): T {
	val it = this.iterator()
	val v = it.next()
	it.remove()
	return v
}