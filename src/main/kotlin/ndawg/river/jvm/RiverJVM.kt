package ndawg.river.jvm

import ndawg.river.River
import ndawg.river.RiverListenerBuilder
import ndawg.river.RiverTypeMapper
import ndawg.river.RiverTypeMappers

/**
 * Establishes a mapping of an event object. The given function will be called when an event
 * of the given type (or subtype) is dispatched. The function should supply objects that were
 * directly involved in the event.
 *
 * @param mapper The function that yields objects involved in the event.
 */
@JvmName("mapMulti")
fun <T : Any> River.map(type: Class<T>, mapper: (T) -> Set<Any?>) = this.map(type.kotlin, mapper)

/**
 * Begins construction of a listener builder. [RiverListenerBuilder.on] must be
 * called for the builder to be registered.
 */
fun <T : Any> River.listener(type: Class<T>) = RiverListenerBuilder(this, type.kotlin)

/**
 * Register a new type mapper, which will receive any subtypes of the given type.
 */
fun <T : Any> RiverTypeMappers.add(type: Class<T>, mapper: RiverTypeMapper) = this.addMapper(type.kotlin, mapper)
