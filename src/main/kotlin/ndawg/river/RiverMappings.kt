package ndawg.river

import ndawg.log.log

typealias RiverTypeMapper = (Any) -> Set<Any>

class RiverTypeMappers {
	
	private val mappers = mutableMapOf<Class<*>, MutableSet<RiverTypeMapper>>()
	
	/**
	 * Register a new type mapper, which will receive any subtypes of the given type.
	 */
	fun <T> add(type: Class<T>, mapper: RiverTypeMapper) {
		mappers.computeIfAbsent(type) { mutableSetOf() } += mapper
	}
	
	/**
	 * Produces input from all registered mappers.
	 */
	fun map(input: Any): Set<Any> {
		val objs = mutableSetOf<Any>()
		mappers.filter { it.key.isAssignableFrom(input::class.java) }.forEach {
			it.value.forEach {
				try {
					objs.addAll(it.invoke(input))
				} catch (e: Throwable) {
					log().error(e) { "Type mapper $it failed when given $input" }
				}
			}
		}
		return objs
	}
	
}