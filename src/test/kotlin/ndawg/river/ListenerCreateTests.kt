package ndawg.river

import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.shouldBe
import org.junit.jupiter.api.Test

class ListenerCreateTests {
	
	/**
	 * Verifies that the [RiverListenerBuilder] has correct initial values.
	 */
	@Test
	fun `defaults of RiverListenerBuilder`() {
		val river = River()
		val dummy = Any()
		
		val listener = RiverListenerBuilder(river, Any::class).from(this).to(dummy).on { }
		listener.owner shouldBe this
		listener.listening shouldBe setOf(dummy)
		listener.type shouldBe Any::class
		listener.priority shouldBe RiverPriority.NORMAL
	}
	
	/**
	 * Verifies that the [listener] method has correct initial values.
	 */
	@Test
	fun `defaults of listener method`() {
		val river = River()
		val dummy = Any()
		
		val listener = river.listener<Any>(from = this, to = listOf(dummy)).build { }
		listener.owner shouldBe this
		listener.listening shouldContain dummy
		listener.type shouldBe Any::class
		listener.priority shouldBe RiverPriority.NORMAL
	}
	
}