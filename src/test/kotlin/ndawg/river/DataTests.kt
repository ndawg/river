package ndawg.river

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

@Suppress("BlockingMethodInNonBlockingContext")
class DataTests {
	
	/**
	 * Verifies that data is stored and retrieved properly without being given a name, both
	 * from listener to listener and to the final result object.
	 */
	@Test
	fun `data storage without a name`() {
		val river = River()
		
		river.listen<Any>(priority = RiverPriority.FIRST) {
			data.put(123)
		}
		
		river.listen<Any>(priority = RiverPriority.LAST) {
			data.get<Int>() shouldBe 123
			data.find<Int>() shouldBe 123
		}
		
		runBlocking {
			val res = river.submit(Any())
			res.data.get<Int>() shouldBe 123
			res.data.find<Int>() shouldBe 123
			// Non-existent key
			shouldThrow<NoSuchElementException> {
				res.data.get<Int>("name")
			}
			res.data.find<Int>("name") shouldBe null
		}
	}
	
	/**
	 * Verifies that data is stored and retrieved properly when being given a name, both
	 * from listener to listener and to the final result object.
	 */
	@Test
	fun `data storage with name`() {
		val river = River()
		
		river.listen<Any>(priority = RiverPriority.FIRST) {
			data.put("sample_name", 123)
		}
		
		river.listen<Any>(priority = RiverPriority.LAST) {
			data.get<Int>("sample_name") shouldBe 123
			data.find<Int>("sample_name") shouldBe 123
		}
		
		runBlocking {
			val res = river.submit(Any())
			res.data.get<Int>("sample_name") shouldBe 123
			res.data.find<Int>("sample_name") shouldBe 123
			
			// Non-existent key
			shouldThrow<NoSuchElementException> {
				res.data.get<Int>()
			}
			res.data.find<Int>() shouldBe null
		}
	}
	
	/**
	 * Verifies that attempting to store a duplicate key throws an exception.
	 */
	@Test
	fun `data storage of duplicate key`() {
		val river = River()
		
		river.listen<Any>(priority = RiverPriority.FIRST) {
			data.put("sample_name", 123)
			data.put("sample")
		}
		
		river.listen<Any>(priority = RiverPriority.LAST) {
			// Int with name sample_name already exists
			shouldThrow<IllegalArgumentException> {
				data.put("sample_name", 456)
			}
			// Non-named String already exists
			shouldThrow<IllegalArgumentException> { data.put("other") }
			
			// Non-named data should be able to be stored, as it doesn't clash
			data.put(456)
			// String with a different name should be able to be stored
			data.put("dummy_name", "other")
		}
		
		runBlocking {
			river.submit(Any())
		}
	}
	
	/**
	 * Verifies that `get` throws an exception when retrieving a non-existent key.
	 */
	@Test
	fun `data retrieval of nonexistent key`() {
		val river = River()
		
		river.listen<Any> {
			shouldThrow<NoSuchElementException> {
				data.get<Int>()
			}
		}
		
		runBlocking {
			river.submit(Any())
		}
	}
	
}