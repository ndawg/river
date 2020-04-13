package ndawg.river

import io.kotlintest.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class SuspendTests {
	
	/**
	 * This test verifies that when a listener suspends, the event loop is freed up to continue
	 * processing event submissions.
	 */
	@Test
	fun `suspends in-between invocations`() {
		val river = River()
		val done = mutableListOf<String>()
		
		// Arbitrary suspending listeners
		river.listen<Int> { delay(5_000) }
		river.listen<Float> { delay(500) }
		
		runBlocking {
			// Launch a task in another context that will submit after the 123 has been submitted
			launch {
				delay(1_000)
				
				// This submission should return before the next submission of 123 because it does not suspend for as long
				river.submit(1.0F)
				done.add("a")
			}
			
			river.submit(123)
			done.add("b")
		}
		
		done shouldBe listOf("a", "b")
	}
	
	/**
	 * This test verifies that if a listener uses `runBlocking`, the event loop does not free itself.
	 */
	@Test
	fun `blocking prevents suspension`() {
		val river = River()
		val done = mutableListOf<String>()
		
		// Arbitrary suspending listeners
		river.listen<Int> { runBlocking { delay(5_000) } }
		river.listen<Float> { runBlocking { delay(500) } }
		
		runBlocking {
			// Launch a task in another context that will submit after the 123 has been submitted
			launch {
				delay(1_000)
				
				// This submission should not return before the next submission of 123 because it does not suspend for as long
				river.submit(1.0F)
				done.add("a")
			}
			
			river.submit(123)
			done.add("b")
		}
		
		done shouldBe listOf("b", "a")
	}
	
}