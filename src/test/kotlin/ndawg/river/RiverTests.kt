package ndawg.river

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test

class GeneralTests {
	
	/**
	 * Verifies the most basic behavior of River: a listener with no filters
	 * receiving an event.
	 */
	@Test
	fun `receives event`() {
		val river = River()
		var received = false
		
		river.listen<Any> {
			received = true
		}
		
		runBlocking {
			river.submit(Any())
			received shouldBe true
		}
	}
	
	// TODO receives subtype?
	
	/**
	 * Verifies that listeners are correctly unregistered by owner.
	 */
	@Test
	fun `unregister by owner`() {
		val river = River()
		val dummy = Any()
		river.listen<Any>(from = dummy) {}
		river.unregister(owner = dummy) shouldBe true
	}
	
	/**
	 * Verifies that priority behaves properly in terms of the order that events
	 * are received.
	 */
	@Test
	fun `priority ensures dispatch order`() {
		val river = River()
		val received = mutableListOf<String>()
		
		river.listen<Any>(priority = RiverPriority.FIRST) { received += "a" }
		river.listen<Any>(priority = RiverPriority.LAST) { received += "e" }
		river.listen<Any>(priority = RiverPriority.NORMAL) { received += "c" }
		river.listen<Any>(priority = RiverPriority.LOW) { received += "d" }
		river.listen<Any>(priority = RiverPriority.HIGH) { received += "b" }
		
		runBlocking {
			river.submit(Any())
			received shouldBe listOf("a", "b", "c", "d", "e")
		}
	}
	
	/**
	 * Verifies events that specify an involvement only receive what they should.
	 */
	@Test
	fun `involving properly filters events`() {
		class SampleEvent(val string: String)
		
		val river = River()
		river.map<SampleEvent> { setOf(it.string) }
		
		var received = false
		river.listen<Any>(to = listOf("hi")) {
			received = true
		}
		
		runBlocking {
			river.submit(SampleEvent("hello"))
			received shouldBe false
			
			river.submit(SampleEvent("hi"))
			received shouldBe true
		}
	}
	
	/**
	 * Verifies that any tasks launched from within a listener are properly waited for
	 * in the completion of the event submission.
	 */
	@Test
	fun `tasks launched from listener are awaited`() {
		val river = River()
		var received = false
		
		river.listen<Any> {
			// This launch statement uses the CoroutineScope provided by the dispatcher.
			launch {
				delay(500) // Prevent a false-positive due to race conditions
				received = true
			}
		}
		
		runBlocking {
			river.submit(Any())
			
			// When the task is rejoined, received should have already been assigned. If not, the tasks
			// are not being properly waited on.
			received shouldBe true
		}
	}
	
	class DummyError : Throwable()
	
	/**
	 * Verifies that errors thrown within a listener are properly reported back
	 * to the original `submit` call.
	 */
	@Test
	fun `errors from listener are rethrown`() {
		val river = River()
		river.listen<Any> { throw DummyError() }
		
		runBlocking {
			shouldThrow<DummyError> {
				river.submit(Any())
			}
		}
	}
	
	/**
	 * Verifies that an error within a listener will prevent further propogation.
	 */
	@Test
	fun `errors interrupt dispatching`() {
		val river = River()
		
		class DummyError : Throwable()
		
		var reached = false
		
		river.listen<Any>(priority = 50) { throw DummyError() }
		river.listen<Any>(priority = 10) { reached = true }
		
		runBlocking {
			shouldThrow<DummyError> { river.submit(Any()) }
			reached shouldBe false
		}
	}
	
	/**
	 * Verifies that an error within a listener doesn't end up breaking the entire
	 * scope of the River context, which is potentially normal behavior in a CoroutineScope
	 * (as opposed to a SupervisorScope).
	 */
	@Test
	fun `error in listener doesnt break scope`() {
		val river = River()
		
		river.listen<String> {
			// Listeners should not call cancel, but in case they do...
			cancel()
		}
		
		runBlocking {
			shouldThrow<CancellationException> {
				river.submit("")
			}
			
			// The River executor should recover from the cancellation and continue to support events
			river.submit(Any())
		}
	}
	
	/**
	 * Verifies that an error in a mapper, which is an usual situation, also does not
	 * break the scope of the River.
	 */
	@Test
	fun `error in mapper doesnt break scope`() {
		val river = River()
		
		river.map<String> { throw IllegalStateException() }
		
		runBlocking {
			shouldThrow<IllegalStateException> {
				river.submit("test")
			}
			
			river.submit(Any())
		}
	}
	
	/**
	 * Verifies that a listener configured to receive a single event is properly unregistered
	 * and does not fire more than once.
	 */
	@Test
	fun `once results in unregistering`() {
		val river = River()
		var run = 0
		
		val listener = river.listen<Any>(once = true) { run++ }
		
		runBlocking {
			river.submit(Any())
			run shouldBe 1
			
			river.submit(Any())
			run shouldBe 1
			
			river.unregister(listener) shouldBe false
		}
	}
	
	/**
	 * Verifies that, by default, listeners can receive more than one event no problem.
	 */
	@Test
	fun `receives multiple events by default`() {
		val river = River()
		var run = 0
		
		val listener = river.listen<Any>(once = false) { run++ }
		
		runBlocking {
			river.submit(Any())
			run shouldBe 1
			
			river.submit(Any())
			run shouldBe 2
			
			river.unregister(listener) shouldBe true
		}
	}
	
	/**
	 * Verifies that a listener which unregisters itself is treated properly, not receiving
	 * additional events.
	 */
	// TODO investigate if this is necessary
	@Test
	fun `unregister during dispatch`() {
		val river = River()
		
		repeat(10_000) {
			val owner = Any()
			var triggered = 0
			
			// This listener should only be triggered once, as it's unregistered
			// But it's a possible race condition
			river.listen<Any>(from = owner) {
				triggered++
				river.post(Any())
				river.unregister(owner)
			}
			
			runBlocking {
				river.submit(Any())
			}
			
			triggered shouldBe 1
		}
	}
	
	/**
	 * Verifies the data in a [RiverResult].
	 */
	@Test
	fun `dispatch result data`() {
		val river = River()
		var invocation: RiverInvocation<*>? = null
		val listener = river.listen<Any> {
			invocation = this
		}
		val event = Any()
		
		runBlocking {
			val result = river.submit(event)
			result.event shouldBe event
			result.invocation shouldBe invocation
			result.discarded shouldBe false
			result.received shouldBe listOf(listener)
		}
	}
	
}