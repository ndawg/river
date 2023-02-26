package ndawg.river.benchmarks

import ndawg.river.River
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
@Suppress("unused")
open class MappingBenchmarks {
	
	private val river = River()
	
	@Setup
	fun setup() {
		repeat(100) {
			river.map<Any> { produce(it) }
		}
	}
	
	@Benchmark
	open fun computeInvolvement() {
		river.getInvolved(Any())
	}
	
}