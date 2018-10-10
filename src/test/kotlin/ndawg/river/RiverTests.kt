package ndawg.river

import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nom.codes.NLog
import java.time.Duration
import java.time.Instant

class RiverTests : StringSpec({
	
	NLog.init()
	open class SampleEvent(val string: String)
	
	"listenable creation via builder" {
		val river = River()
		val dummy = Any()
		
		val listener = RiverListenerBuilder(river).from(this).to(dummy).on<Any> {}
		listener.owner.get() shouldBe this
		listener.listening should { dummy in it }
		listener.type shouldBe Any::class.java
		listener.priority shouldBe RiverPriority.NORMAL.value
	}
	
	"listenable creation via method" {
		val river = River()
		val dummy = Any()
		
		val listener = river.listener<Any>(from = this, to = listOf(dummy)) {}
		listener.owner.get() shouldBe this
		listener.listening should { dummy in it }
		listener.type shouldBe Any::class.java
		listener.priority shouldBe RiverPriority.NORMAL.value
	}
	
	"unregister by owner" {
		val river = River()
		val dummy = Any()
		river.listen<Any>(from = dummy) {}
		river.unregister(owner = dummy) shouldBe true
	}
	
	"wants with correct type" {
		val river = River()
		river.listen<Any> {}.wants(RiverInvocation(river,river, Any(), emptySet())) shouldBe true
	}
	
	"wants with incorrect type" {
		val river = River()
		river.listen<SampleEvent> {}.wants(RiverInvocation(river,river, Any(), emptySet())) shouldBe false
		river.listen<Any> {}.wants(RiverInvocation(river,river, Any(), emptySet())) shouldBe true
	}
	
	"wants with involved objects" {
		val river = River()
		val o1 = Any()
		
		river.listen<Any>(to=listOf(o1)){}.wants(RiverInvocation(river, river,Any(), emptySet())) shouldBe false
		river.listen<Any>(to=listOf(o1)){}.wants(RiverInvocation(river,river, Any(), setOf(o1))) shouldBe true
	}
	
	"single dispatch" {
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
	
	"priority dispatching" {
		val river = River()
		val received = mutableListOf<String>()
		
		river.listen<Any>(priority = RiverPriority.FIRST.value) { received += "a" }
		river.listen<Any>(priority = RiverPriority.LAST.value) { received += "e" }
		river.listen<Any>(priority = RiverPriority.NORMAL.value) { received += "c" }
		river.listen<Any>(priority = RiverPriority.LOW.value) { received += "d" }
		river.listen<Any>(priority = RiverPriority.HIGH.value) { received += "b" }
		
		runBlocking {
			river.submit(Any())
			received shouldBe listOf("a", "b", "c", "d", "e")
		}
	}
	
	"event mapping" {
		val river = River()
		river.map<SampleEvent> { setOf(it.string) }
		
		river.getInvolved(SampleEvent("hello")) shouldBe setOf("hello")
		river.getInvolved(Any()) shouldBe emptySet<Any>()
	}
	
	"event mapping of a subtype" {
		val river = River()
		class SubSampleEvent(val number: Int, string: String): SampleEvent(string)
		river.map<SampleEvent> { setOf(it.string) }
		river.map<SubSampleEvent> { setOf(it.number) }
		
		river.getInvolved(SubSampleEvent(5, "hi")) shouldBe setOf(5, "hi")
	}
	
	"submapping" {
		val river = River()
		river.map<SampleEvent> { setOf(it.string) }
		river.submap<String> { setOf(it.toUpperCase()) }
		
		river.getInvolved(SampleEvent("hi")) shouldBe setOf("hi", "HI")
	}
	
	"involving objects" {
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
	
	"contextual threading" {
		val river = River()
		var received = false
		
		river.listen<Any> { received = true }
		runBlocking {
			river.submit("")
			received shouldBe true
		}
	}
	
	"child suspension and awaiting" {
		val river = River()
		var received = false
		
		river.listen<Any> {
			// This launch statement uses the CoroutineScope provided by the dispatcher.
			launch {
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
	
	"multiple children with delay" {
		val river = River()
		val start = Instant.now()
		
		river.listen<Any> {
			repeat(10) {
				launch {
					delay(1_000)
				}
			}
		}
		runBlocking {
			river.submit(Any())
			Duration.between(start, Instant.now()) should { it < Duration.ofSeconds(2) }
		}
	}
	
	"error reporting" {
		val river = River()
		class DummyError : Throwable()
		
		river.listen<Any> { throw DummyError() }
		runBlocking {
			shouldThrow<DummyError> { river.submit(Any()) }
		}
	}
	
	"error interruption" {
		// This test verifies that an error in any handler will disrupt the dispatching
		// of the entire event chain.
		val river = River()
		class DummyError : Throwable()
		var reached = false
		
		river.listen<Any>(priority=50) { throw DummyError() }
		river.listen<Any>(priority=10) { reached = true }
		runBlocking {
			shouldThrow<DummyError> { river.submit(Any()) }
			reached shouldBe false
		}
	}
	
	"once" {
		val river = River()
		var run = 0
		
		river.listen<Any>(once=true) { run++ }
		
		runBlocking {
			river.submit(Any())
			run shouldBe 1
			
			river.submit(Any())
			run shouldBe 1
		}
	}
	
	"twice" {
		val river = River()
		var run = 0
		
		river.listen<Any>(once=false) { run++ }
		
		runBlocking {
			river.submit(Any())
			run shouldBe 1
			
			river.submit(Any())
			run shouldBe 2
		}
	}
})