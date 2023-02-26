package ndawg.river.util

import io.kotlintest.shouldBe
import org.junit.jupiter.api.Test

class TypeTreeTests {

	@Test
	fun `simple hierarchy`() {
		val tree = TypeTree<String>()
		tree.add(Any::class, "Root")
		tree.add(Int::class, "Integer")
		val root = tree.root!!
		
		root.key shouldBe Any::class
		root.value shouldBe setOf("Root")
		root.children.size shouldBe 1
		
		root.children.first().let { c ->
			c.key shouldBe Int::class
			c.value shouldBe setOf("Integer")
			c.children.isEmpty() shouldBe true
		}
	}
	
	@Test
	fun `find single value in tree`() {
		val tree = TypeTree<String>()
		tree.add(Any::class, "Root")
		tree.add(Int::class, "Integer")
		
		tree.below(Int::class) shouldBe setOf("Integer")
	}
	
	@Test
	fun `insert value into hierarchy`() {
		val tree = TypeTree<String>()
		tree.add(Any::class, "Root")
		tree.add(Int::class, "Integer")
		tree.add(Int::class, "Test")
		val root = tree.root!!
		
		root.key shouldBe Any::class
		root.value shouldBe setOf("Root")
		root.children.size shouldBe 1
		
		root.children.first().let { c ->
			c.key shouldBe Int::class
			c.value shouldBe setOf("Integer", "Test")
			c.children.isEmpty() shouldBe true
		}
	}
	
	@Test
	fun `bfs order`() {
		val tree = TypeTree<String>()
		tree.add(Any::class, "A")
		tree.add(Number::class, "B")
		tree.add(String::class, "C")
		tree.add(Int::class, "D")
		
		val received = mutableListOf<String>()
		tree.bfs {
			received.addAll(it.value)
			false
		}
		received shouldBe listOf("A", "B", "C", "D")
	}
	
	@Test
	fun `dfs order`() {
		val tree = TypeTree<String>()
		tree.add(Any::class, "A")
		tree.add(Number::class, "B")
		tree.add(String::class, "C")
		tree.add(Int::class, "D")
		
		val received = mutableListOf<String>()
		tree.dfs {
			received.addAll(it.value)
			false
		}
		received shouldBe listOf("A", "B", "D", "C")
	}
	
	@Test
	fun `bfs stop`() {
		val tree = TypeTree<String>()
		tree.add(Any::class, "A")
		tree.add(Number::class, "B")
		tree.add(String::class, "C")
		tree.add(Int::class, "D")
		
		tree.bfs {
			"B" in it.value
		}!!.key shouldBe Number::class
	}
	
	@Test
	fun `dfs stop`() {
		val tree = TypeTree<String>()
		tree.add(Any::class, "A")
		tree.add(Number::class, "B")
		tree.add(String::class, "C")
		tree.add(Int::class, "D")
		
		tree.dfs {
			"B" in it.value
		}!!.key shouldBe Number::class
	}
	
	@Test
	fun `find with type`() {
		val tree = TypeTree<String>()
		tree.add(Any::class, "A")
		tree.add(Number::class, "B")
		tree.add(String::class, "C")
		tree.add(Int::class, "D")
		
		tree.below(Any::class) shouldBe setOf("A", "B", "C", "D")
		tree.below(Number::class) shouldBe setOf("B", "D")
		tree.below(String::class) shouldBe setOf("C")
	}
	
	@Test
	fun `add to empty tree`() {
		val tree = TypeTree<String>()
		tree.add(Number::class, "Test")
		
		tree.below(String::class) shouldBe emptySet()
		tree.below(Int::class) shouldBe setOf()
		tree.below(Number::class) shouldBe setOf("Test")
	}
	
	@Test
	fun `any of nothing`() {
		val tree = TypeTree<String>()
		tree.add(Number::class, "Test")
		
//		tree.below(Any::class) shouldBe emptySet()
	}
	
}