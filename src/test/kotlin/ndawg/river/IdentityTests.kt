package ndawg.river

import io.kotlintest.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID

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
		
		listener.listening shouldBe setOf(a)
	}
	
	@Test
	fun `identity overwrites original object`() {
		val river = River()
		river.map<SampleMessage> { produce(it.author) }
		river.id<SampleUser> { it.id }
		
		val a = SampleMessage(SampleUser("000", "name_a"), "hello")
		river.getInvolved(a) shouldBe setOf("000")
	}
	
	@Test
	fun `receives based on identity`() {
		val river = River()
		river.map<SampleMessage> { produce(it.author) }
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
		river.map<SampleMessage> { produce(it.author) }
		river.id<SampleExtendedUser> { it.id }
		
		val a = SampleMessage(SampleExtendedUser("000", "name_a", 123), "hello")
		river.getInvolved(a) shouldBe setOf("000")
	}
	
	@Test
	fun `identity doesn't prevent recursive mapping`() {
		class Server(val uuid: UUID = UUID.randomUUID())
		class Channel(val server: Server, val uuid: UUID = UUID.randomUUID())
		class Message(val channel: Channel, val uuid: UUID = UUID.randomUUID())
		
		val river = River()
		river.map<Channel> { produce(it.server) }
		river.map<Message> { produce(it.channel) }
		river.id<Server> { it.uuid }
		river.id<Channel> { it.uuid }
		river.id<Message> { it.uuid }
		
		val server = Server()
		val channel = Channel(server)
		val message = Message(channel)
		
		// Listen to the server
		var receivedFromServer = false
		river.listen<Message>(to=setOf(server)) {
			receivedFromServer = true
		}
		
		// Listen to the channel
		var receivedFromChannel = false
		river.listen<Message>(to=setOf(channel)) {
			receivedFromChannel = true
		}
		
		runBlocking {
			river.submit(message)
		}
		
		receivedFromServer shouldBe true
		receivedFromChannel shouldBe true
	}
	
}