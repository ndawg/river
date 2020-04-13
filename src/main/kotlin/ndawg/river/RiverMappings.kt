package ndawg.river

import mu.KotlinLogging
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.superclasses

data class RiverTypeMapper(val func: (Any) -> Set<Any?>)

// TODO for efficiency avoid set operations where necessary
class RiverTypeMappers {
	
	private val logger = KotlinLogging.logger {}
	// TODO for efficiency this could probably be a tree of some sort
	private val mappers = mutableMapOf<KClass<*>, MutableSet<RiverTypeMapper>>()
	private val identities = mutableMapOf<KClass<*>, (Any) -> Any>()
	
	/**
	 * Register a new type mapper, which will receive any subtypes of the given type.
	 *
	 * @param type The type of event to receive. The mapper will also receive any
	 * subtypes of the event.
	 * @param mapper The mapper to invoke when an event is received.
	 */
	fun <T : Any> addMapper(type: KClass<T>, mapper: RiverTypeMapper) {
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
		input::class.superclasses.find {
			identities[it] != null
		}?.let {
			return identities[it]!!(input)
		}
		
		return null
	}
	
	/**
	 * Returns mappers that can handle the given type.
	 */
	private fun getPossibleMappers(type: KClass<*>): Map<KClass<*>, MutableSet<RiverTypeMapper>> {
		// TODO work on the efficiency of this function
		return mappers.filter { it.key.isSuperclassOf(type) }
	}
	
	private fun <V> getWithSupertype(type: KClass<*>, map: Map<KClass<*>, V>): Set<V> {
		return map.filter { it.key.isSuperclassOf(type) }.values.toSet()
	}
	
	private fun produce(input: Any): Set<Any> {
		// TODO only filter nulls once
		val result = mutableSetOf<Any>()
		
		// A set of elements that have already been mapped, kept track of to avoid attempting multiple/recursive mappings.
		val mapped = mutableSetOf<Any>()
		// A list of elements that need to be mapped.
		val toMap = mutableListOf(input)
		
		while (toMap.isNotEmpty()) {
			// Map the first element.
			val element = toMap.removeAt(0)
			// Register it as being mapped already.
			mapped.add(element)
			
			// Find mappers that can handle the type.
			val eligible = getPossibleMappers(element::class)
			
			eligible.forEach { entry ->
				entry.value.forEach { mapper ->
					// Filter out all null products for convenience. This allows mappers to be lazy about
					// null checking their values.
					val produced = mapper.func.invoke(element).filterNotNull().map {
						identity(it) ?: it
					}
					result.addAll(produced)
					
					logger.debug {
						"Mapper $mapper received $element and produced: $produced"
					}
					
					// Now we can check each produced and recursively map it. Subtract what has already been mapped.
					val new = produced.minus(mapped)
					toMap.addAll(new)
				}
			}
		}
		
		return result
	}
	
}