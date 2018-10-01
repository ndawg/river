package ndawg.river

import kotlinx.coroutines.CoroutineScope

/**
 * A single event invocation instance, which exists for every event submitted
 * to the event manager. This object encapsulates the data relating to the event
 * so that it is thread safe, cached, and accessible by the event receiver.
 */
data class RiverInvocation<out T : Any>(val manager: River,
                                        val scope: CoroutineScope,
                                        val event: T,
                                        val involved: Set<Any>) : CoroutineScope by scope