package ndawg.river.benchmarks

import kotlinx.coroutines.runBlocking
import ndawg.river.River
import ndawg.river.listen
import org.openjdk.jmh.annotations.*

@Suppress("unused")
open class MappingBenchmarks {
	
	@State(Scope.Benchmark)
	open class ComputeInvolvementData {
		
		val river = River()
		
		@Setup(Level.Invocation)
		fun setup() {
			repeat(100) {
				river.map<Any> { setOf(it) }
			}
		}
	}
	
	@Benchmark
	open fun computeInvolvement(data: ComputeInvolvementData) {
		val river = data.river
		river.getInvolved(Any())
	}
	
	@State(Scope.Benchmark)
	open class DispatchingData {
		
		val river = River()
		
		@Setup(Level.Invocation)
		fun setup() = repeat(100) {
			river.listen<Any> {  }
		}
	}
	
	@Benchmark
	open fun dispatching(data: DispatchingData) {
		val river = data.river
		runBlocking {
			river.submit(Any())
		}
	}
	
}