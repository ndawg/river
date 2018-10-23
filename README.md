River
=====

River is a simple and general purpose event system, built mostly for fun, and written entirely in kotlin. It uses coroutines to handle event processing within a single thread. There is no producer 
paradigm - events are submitted and listeners decide if action needs to be taken.

**Basics**

Consider a chat system. To listen for a chat message, all you would need is a simple setup like this:
```kotlin
data class Author(val id: Int)
data class Message(val author: Author, val message: String)

val river = River()

river.listen<Message> {
	println("Got the message: ${it.message}")
}

// prints "Got the message"
river.submit(ChatMessage(Author("ndawg"), "Hello, world!"))
```

**Mapping**

Events can be mapped to expose related objects that were involved within the event. In the above example, say you wanted to listen to all messages sent by one author. Setting up a mapping is very simple:

```kotlin
river.map<Message> { setOf(it.author) }
```

This mapping mechanism will be invoked when a corresponding event type is dispatched. The returned properties can be specifically listened to:

```kotlin
river.listen<Message>(to=setOf(Author("ndawg"))) {
	println("Got the message from ndawg: ${it.message}")
}
```

In addition, another layer of mapping is available, called submapping. This layer receives the objects from the mappers and breaks them down into event smaller pieces. For example, user properties 
could be exposed.

**Ownership**

All listeners have an owner object (by default, the river instance itself). This owner allows groups of listeners to be unregistered all at once. For example:
```kotlin
val owner = Any()

river.listen<Any>(from=owner) { println("Hi!") }
river.listen<Any> { println("Hello!") }

river.unregister(owner)
river.submit(Any()) // only "Hello!" is printed
```
Owner objects are kept as weak references.

**Priority**

Listeners can have a priority that make them receive events before other listeners. The higher the priority, the sooner the event will be received.

```kotlin
river.listen<Any>(priority=50) { println("Hello") }
river.listen<Any>(priority=10) { println("world!") }

river.submit(Any()) // prints: Hello then world!
```
The default priority is zero. If two listeners both have the same priority, the order between the two is unspecified.

**Errors**

In the event that a handler produces an error when it receives an event, the dispatching of the entire event is halted, and the error is carried back up the chain to the `submit` call.

```kotlin
var reached = false

river.listen<Any>(priority=50) { throw RuntimeException() }
river.listen<Any>(priority=10) { reached = true }

// This call will throw a RuntimeException, and `reached` will still be false
river.submit(Any())
```