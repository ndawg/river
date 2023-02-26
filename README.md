River
=====

River is a simple and general purpose event system, built mostly for fun, and written entirely in Kotlin. It uses coroutines to handle event processing within a single thread. There is no producer 
paradigm - events are submitted and listeners decide if action needs to be taken through a set of relational mapping.

## Principles

1. Any object can be submitted as an event
2. Listeners can be registered from anywhere
3. Every submitted event generates an *invocation* object that is passed to listeners

## Basics

Consider a chat system with two simple types to describe a message and an author.
```kotlin
data class User(val name: String)
data class Message(val author: User, val message: String)

val river = River()
```

And let's say somewhere we have a simulation of our chat room:
```kotlin
repeat(100) {
    river.post(Message(User("ndawg"), "Hello world! $it"))
}
```

Listening for every chat message submitted to our event loop is trivial:

```kotlin
river.listen<Message> {
    println("Got the message: ${it.message}")
}
```

This `listen` method is completely context agnostic: it can be called from anywhere. However, be careful with this power. Listeners should be considered live objects that will
 continue to exist until you tell them to stop listening. The concept of ownership is meant to aid in that. More on that later.

### Mapping

Events can be mapped to expose related objects that were involved within the event. In the above example, say you wanted to listen to all messages sent by one author. Setting up a mapping is very simple:

```kotlin
river.map<Message> { produce(it.author) }
```

Mapping is recursive: if a mapper yields an object which can be mapped, it is included. This repeats until there are no new objects left to map. This mapping mechanism will be invoked when a 
corresponding event type (or any subtype) is dispatched. 

The returned properties can be specifically listened to. For example, to listen to messages that are only from
 a specific author:

```kotlin
river.listen<Message>(to=setOf(Author("ndawg"))) {
	println("Got the message from ndawg: ${it.message}")
}
```


### Identity

Identity is tangential to mapping, except it is applied to events being submitted instead of objects involved in an event. Again, take the chat example. Let's say our user definition was a little
 more realistic:
 ```kotlin
data class User(val id: UUID, var name: String)
```
Now we have an ID for our user which will never change. With our old definition, a User changing their name would trip up our listener. 

We can use the concept of identity to fix this:
```
river.id<User> { it.uuid }
```
This does two things:
1. Tells River that any listener waiting for a User to be involved is actually waiting for a UUID instance, and
2. Tells River to automatically map a User involved in an event to a UUID


### Ownership

All listeners have an owner object (by default, the river instance itself). This owner allows groups of listeners to be unregistered all at once. For example:
```kotlin
val owner = Any()

river.listen<Any>(from=owner) { println("Hi!") }
river.listen<Any> { println("Hello!") }

river.unregister(owner)
river.submit(Any()) // only "Hello!" is printed
```

This is especially helpful when relying on a lifecycle principle, where objects are only temporary and will eventually be shutdown (think fragments on Android, for example). 

### Priority

Listeners can have a priority that make them receive events before other listeners. The higher the priority, the sooner the event will be received.

```kotlin
river.listen<Any>(priority=50) { println("Hello") }
river.listen<Any>(priority=10) { println("world!") }

river.submit(Any()) // prints: Hello then world!
```
The default priority is zero. If two listeners both have the same priority, the order between the two is unspecified.

### Errors

In the event that a handler produces an error when it receives an event, the dispatching of the entire event is halted, and the error is carried back up the chain to the `submit` call.

```kotlin
var reached = false

river.listen<Any>(priority=50) { throw RuntimeException() }
river.listen<Any>(priority=10) { reached = true }

// This call will throw a RuntimeException, and `reached` will still be false
river.submit(Any())
```

### Discarding

Sometimes it's useful for individual listeners to decide that events should not continue to be propagated. For this, River offers the `discard()` method:

```kotlin
river.listen<Any> {
    if (it is String)
        discard()
}
```

Any listeners that have yet to be given the event (ie any with lower priority) will not receive the event. Optionally, you can include a reason for discarding.

```kotlin
river.listen<Any> {
    if (it is String)
        discard("No strings allowed!")
}
```

When submitting to River, there is a convenience method to operate on discarded events, which allows you to retrieve the reason.
```kotlin
val res = river.submit("hello")
res.ifDiscarded { discard ->
    println("It got discarded for: ${discard.reason}")
}
```

Because discarding is implemented by throwing an exception, you can also retrieve the stack to see where discarding occurred.

### Data

For each event, a `RiverData` instance is created. This is essentially a mutable map of data with a pair of keys: the type of the data, and the name of the key (the name is optional, and is `null
` unless changed). The benefit of such is a system is that listeners can pass data around to each other as well as back to the event's submission point. Examples:

```kotlin
river.listen<Any>(priority=RiverPriority.FIRST) {
    data.put("started", Instant.now())
}

river.listen<Any>(priority=RiverPriority.LAST) {
    val started = data.get<Instant>("started")
    println("Elapsed: ${Duration.between(started, Instant.now())}")
}
```

Remember, the key of the data is both the class type and name:

| put | resulting key | retrieval |
|---------------|---------------|----------------------|
| `data.put(Instant.now())` | (type=Instant, name=null) | `data.get<Instant>()` |
| `data.put("start", Instant.now())` | (type=Instant, name=start) | `data.get<Instant>("start")` |

If you're unsure about whether or not a key will be present, there are two `find` operations that will retrieve data without throwing on a missing key.

## Suspending Behavior

River's internal executor is based on a single threaded coroutine context. The most notable consequence is that a suspension from a listener will free up the coroutine to receive new events. For
 example:
 
```kotlin
river.listen<Any> {
    // Needs to do some complex IO, offload
    launch(Dispatchers.IO) {
        // This suspends the listener, meaning events will begin being fired by River again
    }
}
```

In other words, there is no guarantee that each submitted event will finish in order. If this behavior is necessary, make sure you use `runBlocking` when performing suspending operations, or avoid
 making suspending calls. If your listener suspends, another event might start being processed, which could introduce race conditions to your application.


## Memory Considerations

As mentioned earlier, listeners should be considered "live" objects until they are unregistered. In particular, they hold
strong references to their owner and to any object that they are listening to.

## Complex Example

Let's stick with a chat application, but a more realistic one. First, let's define our types:
```kotlin
interface Identifiable {
	val id: UUID
}

data class User(val id: UUID, var name: String): Identifiable
data class Server(val id: UUID): Identifiable
data class Channel(val id: UUID, val server: Server, var name: String): Identifiable
data class Message(val id: UUID, val author: User, val channel: Channel, val content: String): Identifiable

val river = River()
```

Okay, now let's configure River to handle these types. You'll notice all of these objects are
`Identifiable` -  in a real application, these UUIDs would persist across sessions. Knowing this,
we should set up the identity:

```kotlin
river.id<Identifiable> { it.id }
```

Notice we only have to do this for the interface and not all classes that implement it. Okay,
now let's map the objects involved in the events:
```kotlin
river.map<Message> { produce(it.user, it.channel) }
river.map<Channel> { produce(it.server) }
```

Now when a `Message` is submitted, the following will be available: the user, channel, and server.
This is because types are mapped recursively. When a `Message` is mapped to a `User` and a `Channel`, a check is performed
to see if either of those objects can also be mapped; and since we have a mapping for `Channel`, we apply it.

Let's set up some listeners:

```kotlin
// Listen to an entire server
val server: Server = TODO()
river.listen<Message>(to=setOf(server)) {
	println("There was a message sent in the server! {it}")
}
```

Since we've also exposed the `User` instance in a mapping, we can listen to all events involving users:
```kotlin
river.listen<User> {
    println("A user did something! {it}")
}
```

Heck, we can listen to any event involving _any_ `Identifiable` since listeners can receive any instance of the specified type:
```kotlin
river.listen<Identifiable> {
    println("The UUID ${it.uuid} was involved in an event")
}
```

Of course, we can combine what we're listening to. Perhaps a message from a specific user in a specific channel:

```kotlin
val channel: Channel = TODO()
val user: User = TODO()
river.listen<Message>(to=setOf(channel, user)) {
	println("The user sent a message in the channel: ${it}")
}
```
