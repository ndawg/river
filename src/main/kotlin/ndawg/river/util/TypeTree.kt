package ndawg.river.util

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * A tree structure used to speed up mapping hierarchies. Each type can be associated
 * with a set of values (for example, mapping functions).
 */
class TypeTree<V> {
	
	var root: Node<KClass<*>, MutableSet<V>>? = null
	
	/**
	 * Adds the given value for the type into the tree, potentially creating a new node.
	 */
	fun add(type: KClass<*>, value: V) {
		if (root == null) {
			root = Node(type, mutableSetOf(value))
			return
		}
		
		val nodes = mutableListOf(root!!)
		var found: Node<KClass<*>, MutableSet<V>>? = null
		
		while (nodes.isNotEmpty()) {
			val node = nodes.removeAt(0)
			if (node.key == type) { // Direct match
				node.value.add(value)
				return
			} else if (type.isSubclassOf(node.key)) {
				// Continue descent
				found = node
				nodes.addAll(found.children)
			}
		}
		
		if (found != null) {
			// Check other children to see if they should be grouped under the added value
			val children = mutableListOf<Node<KClass<*>, MutableSet<V>>>()
			found.children.removeIf { child ->
				if (child.key.isSubclassOf(type)) {
					children.add(child)
					true
				} else false
			}
			
			val node = Node(type, mutableSetOf(value), children)
			found.children.add(node)
		}
	}
	
	/**
	 * Finds all values where the class of the node is the given class or is a subclass
	 * of the given class and combines them into a single set.
	 */
	fun below(type: KClass<*>): Set<V> {
		if (root == null) {
			return emptySet()
		}
		
		val values = mutableSetOf<V>()
		
		// Find the highest level node that is a subclass (or the same)
		val parent = root!!.bfs { node ->
			node.key.isSubclassOf(type)
		} ?: return emptySet()
		
		// Compile the values
		parent.dfs {
			values.addAll(it.value)
			false // don't stop
		}
		
		return values
	}
	
	/**
	 * Finds all values where the class of the node is the given class or is a superclass
	 * of the given class and combines them into a single set.
	 */
//	fun above(type: KClass<*>): Set<V> {
//		if (root == null) {
//			return emptySet()
//		}
//
//		// Keep track of nodes encountered
//		val stack = mutableListOf<Node<KClass<*>, MutableSet<V>>>()
//		stack.add(root!!)
//	}
	
	/**
	 * Combines all values in the given node and all below it into a single set.
	 * This uses a breadth first search.
	 */
	fun values(node: Node<KClass<*>, MutableSet<V>>): MutableSet<V> {
		val vals = mutableSetOf<V>()
		node.bfs {
			vals.addAll(it.value)
			false
		}
		return vals
	}
	
	/**
	 * Performs a depth first search until the given `test` function returns true for
	 * a Node. If it never returns true, then null is returned. Starts at and includes the root.
	 */
	fun dfs(test: (Node<KClass<*>, MutableSet<V>>) -> Boolean): Node<KClass<*>, MutableSet<V>>? {
		return root?.dfs(test)
	}
	
	/**
	 * Performs a breadth first search until the given `test` function returns true for
	 * a Node. If it never returns true, then null is returned. Starts at and includes the root.
	 */
	fun bfs(test: (Node<KClass<*>, MutableSet<V>>) -> Boolean): Node<KClass<*>, MutableSet<V>>? {
		return root?.bfs(test)
	}
	
	override fun toString(): String = buildString {
		if (root == null) {
			return@toString "[no root]"
		}
		
		var i = 0
		
		fun str(node: Node<*, *>) {
			appendln((' ' * i++) + "└─ ${node.key} = ${node.value}")
			node.children.forEach {
				str(it)
			}
			if (i != 0) i--
		}
		
		str(root!!)
	}
	
	private operator fun Char.times(i: Int): String = this.toString().repeat(i)
	
}

/**
 * Represents a single Node that has a key, value, and children. Nodes are used as
 * starting points for searching.
 */
data class Node<K, V>(
	val key: K, val value: V, val children: MutableList<Node<K, V>> = mutableListOf()
) {
	
	/**
	 * Performs a depth first search until the given `test` function returns true for
	 * a Node. If it never returns true, then null is returned. Includes the current node.
	 */
	fun dfs(test: (Node<K, V>) -> Boolean): Node<K, V>? {
		val nodes = mutableListOf(this)
		
		while (nodes.isNotEmpty()) {
			val node = nodes.removeAt(0)
			if (test(node)) return node else nodes.addAll(0, node.children)
		}
		
		return null
	}
	
	/**
	 * Performs a breadth first search until the given `test` function returns true for
	 * a Node. If it never returns true, then null is returned. Includes the current node.
	 */
	fun bfs(test: (Node<K, V>) -> Boolean): Node<K, V>? {
		val nodes = mutableListOf(this)
		
		while (nodes.isNotEmpty()) {
			val node = nodes.removeAt(0)
			if (test(node)) return node else nodes.addAll(node.children)
		}
		
		return null
	}
	
}
