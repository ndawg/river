package ndawg.river

import mu.KLogger
import mu.KotlinLogging
import kotlin.reflect.KClass

private val loggers = mutableMapOf<Class<*>, KLogger>()

/**
 * Obtains a logger instance for the current class
 */
internal fun Any.log(): KLogger {
	return loggers.computeIfAbsent(this::class.java) {
		val name = this.javaClass.name
		val slicedName = when {
			name.contains("Kt$") -> name.substringBefore("Kt$")
			name.contains("$") -> name.substringBefore("$")
			else -> name
		}
		KotlinLogging.logger(slicedName)
	}
}

/**
 * Obtains a logger instance for the given class
 */
internal fun log(clazz: Class<*>): KLogger {
	return loggers.computeIfAbsent(clazz) {
		val name = clazz.name
		val slicedName = when {
			name.contains("Kt$") -> name.substringBefore("Kt$")
			name.contains("$") -> name.substringBefore("$")
			else -> name
		}
		KotlinLogging.logger(slicedName)
	}
}

/**
 * Obtains a logger instance for the given class
 */
internal fun log(kClass: KClass<*>): KLogger {
	return log(kClass.java)
}