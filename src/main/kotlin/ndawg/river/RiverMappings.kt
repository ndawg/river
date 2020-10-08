package ndawg.river

import mu.KotlinLogging
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.superclasses

data class RiverTypeMapper(val func: (Any) -> Set<Any?>)

/**
 * Manages mappings for types.
 */
class RiverTypeMappers {
	
	private val logger = KotlinLogging.logger {}
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
	// TODO why just immedaite supertypes?
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
	private fun getPossibleMappers(type: KClass<*>): Set<RiverTypeMapper> {
		val maps = mutableSetOf<RiverTypeMapper>()
		// This is an attempt at efficiency. The other option here is to check every single
		// mapper to see if it's a super type. Instead, we can retrieve every superclass and
		// check it that way, utilizing the O(1) efficiency of the map#contains check.
		mappers[type]?.let { maps.addAll(it) }
		
		type.superclasses.forEach { t ->
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
			
//			eligible.forEach { entry ->
				eligible.forEach { mapper ->
					// Don't complain about nulls here - filter out later.
					// This creates a new collection unnecessarily. Could be optimized.
					val produced = mapper.func.invoke(element).map {
						if (it == null) null else identity(it) ?: it
					}
					// O(1) add on set
					result.addAll(produced)
					
					logger.debug {
						"Mapper $mapper received $element and produced: $produced"
					}
					
					// Recursively mapped what was produced (check for already mapped is above).
					// O(1) add on set
					toMap.addAll(produced)
				}
//			}
		}
		
		// Ditch null. Only do this once to avoid intermediate filtering.
		result.remove(null)
		@Suppress("UNCHECKED_CAST")
		return result as Set<Any>
	}
	
}

fun <T> MutableSet<T>.pop(): T {
	val it = this.iterator()
	val v = it.next()
	it.remove()
	return v
}