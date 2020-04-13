package ndawg.river

import io.kotlintest.shouldBe
import org.junit.jupiter.api.Test

class WantTests {
	
	class SampleEvent
	
	/**
	 * Verifies that a listener will automatically assume it wants an invocation based purely
	 * on the type of the event.
	 */
	@Test
	fun `wants correct type`() {
		val river = River()
		river.listen<Any> {}.wants(RiverInvocation(river, river.scope, Any(), emptySet())) shouldBe true
	}
	
	/**
	 * Verifies that a listener will automatically assume it wants an invocation even when given
	 * a subtype of the original type that it listened to.
	 */
	@Test
	fun `wants correct when given subtype`() {
		val river = River()
		river.listen<Any> {}.wants(RiverInvocation(river, river.scope, "other", emptySet())) shouldBe true
	}
	
	/**
	 * Verifies listeners refuse when given an incorrect type.
	 */
	@Test
	fun `refuses when given incorrect type`() {
		val river = River()
		river.listen<SampleEvent> {}.wants(RiverInvocation(river, river.scope, Any(), emptySet())) shouldBe false
		river.listen<Any> {}.wants(RiverInvocation(river, river.scope, Any(), emptySet())) shouldBe true
	}
	
	/**
	 * Verifies that listeners correctly assess wanting an invocation when the only determining factor
	 * is the objects involved in the event.
	 */
	@Test
	fun `wants with involvement`() {
		val river = River()
		val o1 = Any()
		
		river.listen<Any>(to = listOf(o1)) {}.wants(RiverInvocation(river, river.scope, Any(), emptySet())) shouldBe false
		river.listen<Any>(to = listOf(o1)) {}.wants(RiverInvocation(river, river.scope, Any(), setOf(o1))) shouldBe true
	}
	
}
