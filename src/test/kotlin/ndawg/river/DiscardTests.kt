package ndawg.river

import io.kotlintest.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class DiscardTests {
	
	/**
	 * Verifies that a discard() call prevents propagation to lower priority listeners.
	 * This is the core functionality of the discard operation.
	 */
	@Test
	fun `discarding interrupts propagation`() {
		val river = River()
		val event = Any()
		
		var receivedFirst = false
		var receivedSecond = false
		
		val first = river.listen<Any>(priority = RiverPriority.FIRST) {
			receivedFirst = true
			discard()
		}
		
		river.listen<Any>(priority = RiverPriority.LAST) {
			receivedSecond = true
		}
		
		runBlocking {
			val result = river.submit(event)
			
			receivedFirst shouldBe true
			receivedSecond shouldBe false
			
			result.received shouldBe listOf(first)
		}
	}
	
	/**
	 * Verifies that discarding is preserved in the result object, and that no
	 * exception is thrown even though discard() throws an exception.
	 */
	@Test
	fun `preserve discard state in result`() {
		val river = River()
		
		river.listen<Any> {
			discard()
		}
		
		runBlocking {
			val result = river.submit(Any())
			result.discarded shouldBe true
		}
	}
	
	/**
	 * Verifies that discarding is preserved, along with the reason, in the result object,
	 * and that no exception is thrown even though discard() throws an exception.
	 */
	@Test
	fun `preserve discard state with reason`() {
		val river = River()
		
		river.listen<Any> {
			discard("Sample message")
		}
		
		runBlocking {
			val result = river.submit(Any())
			result.discarded shouldBe true
			result.discard!!.message shouldBe "Sample message"
		}
	}
	
	/**
	 * Verifies that `ifDiscarded` behaves properly.
	 */
	@Test
	fun `ifDiscarded operation`() {
		val river = River()
		val event = Any()
		
		river.listen<Any> {
			discard("Sample message")
		}
		
		runBlocking {
			val result = river.submit(event)
			var discarded = false
			result.ifDiscarded {
				it.message shouldBe "Sample message"
				discarded = true
			}
			
			discarded shouldBe true
		}
	}
	
}
