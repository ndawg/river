package ndawg.river

import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class RiverTests : StringSpec({
	class SampleEvent(val string: String)
	
	"listenable creation via builder" {
		val river = River()
		val dummy = Any()
		
		val listener = RiverListenerBuilder(river).from(this).to(dummy).on<Any> {}
		listener.owner shouldBe this
		listener.listening should { dummy in it }
		listener.type shouldBe Any::class.java
		listener.priority shouldBe RiverPriority.NORMAL.priority
	}
	
	"listenable creation via method" {
		val river = River()
		val dummy = Any()
		
		val listener = river.listen<Any>(from = this, to = listOf(dummy)) {}
		listener.owner shouldBe this
		listener.listening should { dummy in it }
		listener.type shouldBe Any::class.java
		listener.priority shouldBe RiverPriority.NORMAL.priority
	}
	
	"wants with correct type" {
		val river = River()
		river.listen<Any> {}.wants(RiverInvocation(river, Any(), emptySet())) shouldBe true
	}
	
	"wants with incorrect type" {
		val river = River()
		river.listen<SampleEvent> {}.wants(RiverInvocation(river, Any(), emptySet())) shouldBe false
		river.listen<Any> {}.wants(RiverInvocation(river, Any(), emptySet())) shouldBe true
	}
	
	"wants with involved objects" {
		val river = River()
		val o1 = Any()
		
		river.listen<Any>(to=listOf(o1)){}.wants(RiverInvocation(river, Any(), emptySet())) shouldBe false
		river.listen<Any>(to=listOf(o1)){}.wants(RiverInvocation(river, Any(), setOf(o1))) shouldBe true
	}
	
	"single dispatch" {
		val river = River()
		var received = false
		
		river.listen<Any> {
			received = true
		}
		
		runBlocking {
			println("submitting")
			river.submit(Any()).await()
			received shouldBe true
		}
	}
	
	"priority dispatching" {
		val river = River()
		val received = mutableListOf<String>()
		
		river.listen<Any>(priority = RiverPriority.FIRST.priority) { received += "a" }
		river.listen<Any>(priority = RiverPriority.LAST.priority) { received += "e" }
		river.listen<Any>(priority = RiverPriority.NORMAL.priority) { received += "c" }
		river.listen<Any>(priority = RiverPriority.LOW.priority) { received += "d" }
		river.listen<Any>(priority = RiverPriority.HIGH.priority) { received += "b" }
		
		runBlocking {
			river.submit(Any()).await()
			delay(100)
			received shouldBe listOf("a", "b", "c", "d", "e")
		}
	}
	
	"event mapping" {
		val river = River()
		river.map<SampleEvent> { setOf(it.string) }
		
		river.getInvolved(SampleEvent("hello")) shouldBe setOf("hello")
		river.getInvolved(Any()) shouldBe emptySet<Any>()
	}
	
	"involving objects" {
		val river = River()
		river.map<SampleEvent> { setOf(it.string) }
		
		var received = false
		river.listen<Any>(to = listOf("hi")) {
			received = true
		}
		
		runBlocking {
			river.submit(SampleEvent("hello")).await()
			delay(100)
			received shouldBe false
			
			river.submit(SampleEvent("hi")).await()
			delay(100)
			received shouldBe true
		}
	}
})