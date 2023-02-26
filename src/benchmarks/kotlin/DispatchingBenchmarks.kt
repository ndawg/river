package ndawg.river.benchmarks

import kotlinx.coroutines.runBlocking
import ndawg.river.River
import ndawg.river.listen
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
@Suppress("unused")
open class DispatchingBenchmarks {
	
	private val river = River()
	
	@Setup
	fun setup() = repeat(100) {
		river.listen<Any> {  }
	}
	
	@Benchmark
	open fun dispatching() {
		runBlocking {
			river.submit(Any())
		}
	}
	
}