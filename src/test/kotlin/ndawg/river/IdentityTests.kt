package ndawg.river

import io.kotlintest.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class IdentityTests {
	
	open class SampleUser(val id: String, val name: String)
	data class SampleMessage(val author: SampleUser, val message: String)
	
	@Test
	fun `no identity returns null`() {
		val river = River()
		val sample = SampleUser("", "")
		river.mappers.identity(sample) shouldBe null
	}
	
	@Test
	fun `identity correctly mapped in builder`() {
		val river = River()
		river.map<SampleMessage> { setOf(it.author) }
		river.id<SampleUser> { it.id }
		
		val a = SampleUser("test", "1")
		val listener = river.listen<SampleUser>(to=setOf(a)) {}
		
		listener.listening shouldBe setOf("test")
	}
	
	@Test
	fun `identity overwrites original object`() {
		val river = River()
		river.map<SampleMessage> { setOf(it.author) }
		river.id<SampleUser> { it.id }
		
		val a = SampleMessage(SampleUser("000", "name_a"), "hello")
		river.getInvolved(a) shouldBe setOf("000")
	}
	
	@Test
	fun `receives based on identity`() {
		val river = River()
		river.map<SampleMessage> { setOf(it.author) }
		river.id<SampleUser> { it.id }
		
		// These author objects should be considered equal by River
		val a = SampleMessage(SampleUser("000", "name_a"), "hello")
		val b = SampleMessage(SampleUser("000", "name_a"), "world")
		
		var received = 0
		river.listen<SampleMessage>(to=setOf(SampleUser("000", "name_b"))) {
			received++
		}
		
		runBlocking {
			river.submit(a)
			received shouldBe 1
			
			river.submit(b)
			received shouldBe 2
		}
	}
	
	@Test
	fun `immediate supertypes are checked`() {
		class SampleExtendedUser(id: String, name: String, val number: Int) : SampleUser(id, name)
		
		val river = River()
		river.map<SampleMessage> { setOf(it.author) }
		river.id<SampleExtendedUser> { it.id }
		
		val a = SampleMessage(SampleExtendedUser("000", "name_a", 123), "hello")
		river.getInvolved(a) shouldBe setOf("000")
	}
	
}