package ndawg.river

import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldExist
import io.kotlintest.shouldBe
import org.junit.jupiter.api.Test

@Suppress("BlockingMethodInNonBlockingContext")
class MappingTests {
	
	open class SampleEvent(val string: String)
	
	/**
	 * Verifies the behavior of a single mapper.
	 */
	@Test
	fun `simple mapping`() {
		val river = River()
		river.map<SampleEvent> { setOf(it.string) }
		
		river.getInvolved(SampleEvent("hello")) shouldBe setOf("hello")
		river.getInvolved(Any()) shouldBe emptySet()
	}
	
	/**
	 * Verifies the behavior of multiple mappers, where one mapper yields a piece
	 * of data that should be mapped by another.
	 */
	@Test
	fun `mapping data yielded by another mapper`() {
		val river = River()
		river.map<SampleEvent> { setOf(it.string) }
		river.map<String> { setOf(it.toUpperCase()) }
		
		river.getInvolved(SampleEvent("hi")) shouldBe setOf("hi", "HI")
	}
	
	/**
	 * Verifies that multiple mappers of the same type will be processed correctly,
	 * each yielding their own mapping.
	 */
	@Test
	fun `multiple mappers of the same type`() {
		val river = River()
		river.map<SampleEvent> { setOf(it.string) }
		river.map<SampleEvent> { setOf(it.string.toUpperCase()) }
		
		river.getInvolved(SampleEvent("hi")) shouldBe setOf("hi", "HI")
	}
	
	/**
	 * Verifies that the mapping of the type SampleEvent is also called when a
	 * subtype is dispatched, in this case SubSampleEvent.
	 */
	@Test
	fun `mapping of a subtype`() {
		val river = River()
		
		class SubSampleEvent(val number: Int, string: String) : SampleEvent(string)
		river.map<SampleEvent> { setOf(it.string) }
		river.map<SubSampleEvent> { setOf(it.number) }
		
		river.getInvolved(SubSampleEvent(5, "hi")) shouldBe setOf(5, "hi")
	}
	
	/**
	 * This test ensures that a mapping which yields itself does not cause an infinite loop
	 * of mapping attempts.
	 *
	 * +-----+
	 * | Any |
	 * +-----+
	 *    |
	 *    x stops because the event has already been mapped.
	 *    v
	 * +-----+
	 * | Any |
	 * +-----+
	 */
	@Test
	fun `avoid recursive mapping when yielding self`() {
		val river = River()
		river.map<Any> { setOf(it) }
		
		val ev = Any()
		river.getInvolved(ev) shouldBe setOf(ev)
		
		// TODO ensure that `map` only gets called once? would require River() spy
	}
	
	/**
	 * This test ensures that a circular mapping does not cause an infinite loop of
	 * mapping attempts.
	 *
	 * +--------+
	 * | EventA |
	 * +--------+
	 *     ||
	 *     vv
	 * +--------+
	 * | EventB |
	 * +--------+
	 *     ||
	 *     xx stops here because EventA has already been mapped.
	 *     vv
  	 * +--------+
	 * | EventA |
	 * +--------+
	 */
	@Test
	fun `avoid recursive mapping when double branching`() {
		class EventA
		class EventB(val a: EventA)
		val river = River()
		
		river.map<EventA> { setOf(EventB(it)) }
		river.map<EventB> { setOf(it.a) }
		
		val ev = EventA()
		val involved = river.getInvolved(ev)
		
		involved shouldExist { it is EventB && it.a == ev }
		involved shouldContain ev
		involved.size shouldBe 2
	}
	
	/**
	 * Similar to the above test except there are three instead of two branches.
	 */
	@Test
	fun `avoid recursive mapping when triple branching`() {
		class EventA
		class EventB(val a: EventA)
		class EventC(val b: EventB)
		val river = River()
		
		val evA = EventA()
		val evB = EventB(evA)
		
		river.map<EventA> { setOf(evB) }
		river.map<EventB> { setOf(it, it.a, EventC(it)) }
		river.map<EventC> { setOf(it, it.b, it.b.a) }
		
		val involved = river.getInvolved(evA)
		
		involved shouldExist { it is EventB && it.a == evA }
		involved shouldExist { it is EventC && it.b == evB && it.b.a == evA }
		involved shouldContain evA
		involved shouldContain evB
		involved.size shouldBe 3
	}
	
}
