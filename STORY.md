I made an asynchronous sequence in Kotlin! It's like `sequence {}`, but asynchronous! Here's how it looks: [1]

```kotlin
fun main() = runBlocking {
    asyncSequence {
        for (i in 1..5) {
            yield(i)
        }
    }.forEach {
        println(it)
    }
}
```

You might be thinking, this is pretty much what `flow {}` does, and you would be right, except for one tiny detail... But let me tell you a story first.

***

So I like Kotlin's `sequence {}`. It's awesome! If you are not familiar with it, it allows writing this function: [2]

```kotlin
fun squaresOfOddNumbers(upToIncluding: Int): Collection<Int> {
    return (0..upToIncluding)
            .filter { it % 2 == 1 }
            .map { it * it }
}

fun main() {
    squaresOfOddNumbers(upToIncluding = 7).forEach { println(it) }
}
```

like this: [3]

```kotlin
fun squaresOfOddNumbers(upToIncluding: Int) = sequence {
    for (i in 0..upToIncluding) {
        if (i % 2 == 1) yield(i * i)
    }
}
```

Not only this allows writing logic in a more conventional way with `if`/`else` (if that's what you prefer), but it also evaluates results lazily. For example, the following code will make the loop inside `getSquaresOfOddNumbers` run only 6 times, not 999: [4]

```kotlin
for (i in squaresOfOddNumbers(999)) {
    println(i)
    if (i == 25) break
}
```

This can be handy if the computation of the yielded values is expensive. And, you can have infinite sequences as well.

***

While this is all neat, sequence builder will block the thread it's running on. In a concurrent application, you want a version of this that can give way to other coroutines. An obvious choice here is `flow {}`: [5]

```kotlin
fun squaresOfOddNumbers(upToIncluding: Int) = flow {
    for (i in 0..upToIncluding) {
        if (i % 2 == 1) emit(i * i)
    }
}

fun main() = runBlocking {
    squaresOfOddNumbers(upToIncluding = 7).collect { println(it) } 
}
```

Note how this code is almost identical to the `sequence` code above [3]. We just changed a few words! Neat? Very neat! However, you better pay a bit of attention to one peculiarity of `Flow`. As I was reading the [documentation](https://kotlinlang.org/docs/reference/coroutines/flow.html#exception-transparency), I stumbled upon the following:

> Flows must be transparent to exceptions and it is a violation of the exception transparency to emit values in the `flow { ... }` builder from inside of a `try/catch` block. This guarantees that a collector throwing an exception can always catch it using `try/catch` as in the previous example.

This was a bit hard to understand, but when I actually did try to use `try/catch` in emitter, I could see the issue. This code: [6]

```kotlin
class FunnyException : Exception()

fun main() = runBlocking<Unit> {
    try {
        flow {
            println("emitting: 0")
            emit(0)
            try {
                println("emitting: 1")
                emit(1)
            } catch (e: FunnyException) {
                println("caught in emitter: $e")
                println("emitting: 2")
                emit(2)
            }
        }.onEach {
            if (it == 1) throw FunnyException()
        }.collect {
            println("collected: $it")
        }
    } catch (e: Throwable) {
        println("caught in runBlocking: $e")
    } 
}
```
produces:

     emitting: 0
     collected: 0
     emitting: 1
     caught in emitter: FunnyException
     emitting: 2
     caught in runBlocking: java.lang.IllegalStateException: Flow exception transparency is violated:
         Previous 'emit' call has thrown exception FunnyException, but then emission attempt of value '2' has been detected.
         Emissions from 'catch' blocks are prohibited in order to avoid unspecified behaviour, 'Flow.catch' operator can be used instead.
         For a more detailed explanation, please refer to Flow documentation.

Evidently, `onEach` throws an exception, and while you might expect it to propagate downwards, it doesn't, as it's being getting caught in the emitter. The fact that `FunnyException` appears in the *emitter* confused me. What's it doing there? I wasn't expecting any exceptions there, save maybe `CancellationException`.

To get to the bottom of this, let's first try to answer, why does this problem not affect `sequence {}`? And how does it work in the first place?

***

What is a `sequence {}` in Kotlin is commonly called [a generator](https://en.wikipedia.org/wiki/Generator_(computer_programming)). A generator is an iterable that uses a function to lazily produce values. When this functions produces the next value, it is paused until the next value is needed. If the next value is never requested, the function might remain paused till the death of the application.

> Note: to be precise, a generator can can only be iterated over once. That is, every generator is an iterator. Kotlin's `Sequence`, on the other hand, can be iterated many times over. I'm not sure if there's a term for that. “Regenerator”? To have a regular generator, you can use the `iterator {}` function that has the same syntax as `sequence {}`. It returns an `Iterator`. 
> 
> Also note that a `Sequence` in Kotlin refers specifically to an iterable-ish thing with lazily evaluated values. A `List` is *not* a `Sequence`. I'm saying iterable-*ish* as `Sequence` does not implement `Iterable`. (Why?)

But how do you “pause” a function? You have a bunch of local variables that you have to remember, and a call stack... Sounds familiar? If you are thinking that this is awfully similar to a coroutine, you would be right! Generators are often made using coroutines/continuations, and sometimes, as it used to be the case with Python, coroutines are made using generators. Hence the name of the function `yield`: it comes from [yielding execution to other threads](https://en.wikipedia.org/wiki/Yield_(multithreading)).

Kotlin's sequences are also implemented using coroutines. These coroutines are not tied to a coroutine context such as the one used by `runBlocking {}` (`EmptyCoroutineContext` is used instead). After yielding, these coroutines are just lying there until you need a new value. This is why `yield()` will not throw any exceptions from downstream code.

***

What about flows, though? A `FlowCollector` doesn't have a `yield()` function, instead, it has `emit()`. Indeed, it cannot be called yield, as it doesn't yield execution. If you look at the source code of `collect {}`, you'll see that emit is directly calling its block:

```kotlin
public suspend inline fun <T> Flow<T>.collect(crossinline action: suspend (value: T) -> Unit): Unit =
    collect(object : FlowCollector<T> {
        override suspend fun emit(value: T) = action(value)
    })
```

If the collect block throws an exception, it is not propagated to the for loop, as there isn't a for loop! Instead, it's propagated to the flow emitter. Maybe this will make more sense:

* In sequences, the execution is “controlled” by the for loop:

                      coroutine
                       ↑     ↓  
       for loop → ⮎ get next value → process it ⮌
                                      ↓     ↑
                                     loop body

* In flow, the execution is “controlled” by the emitter:

       collect() → flow block flow block flow block flow block
                     ↓         ↑      ↓        ↑      ...
                    collect block    collect block        ...

...And now you have the exception transparency issue.

***

So I wondered, can I make something that doesn't have this issue? After all, a for loop can work with asynchronous iterators; instead of `operator fun next()` you can simply use `suspend operator fun next()`. So I poked around and made [`AsyncIterator`](https://github.com/oakkitten/AsyncIterable)! (Note that this project is a proof of concept/thought experiment kind of thing and absolutely should not be used anywhere near production.)

This approach has a few advantages, namely:

* You can have more familiar methods such as `yield() ` or `forEach {}`

* You need not worry about exception transparency

* As the for loop is in control, you can break out of it. This also means that you can return from `forEach {}`. In flow, the `collect {}` block is used directly by the emitter, and hence must be marked `crossinline`. This is probably not very important, but if you were to rewrite [4] with flow, you would have to use something like: [7]
  ```kotlin
  squaresOfOddNumbers(999).asFlow()
        .onEach { println(it) }
        .takeWhile { it != 25 }
        .collect()
  `````

It also has major disadvantages:

* If you have a `try/finally` in the `asyncSequence {}` block, the finally clause may not run when you expect it to run. Unlike it is with `sequence {}`, the context that `asyncSequence {}` is running on is real, and the coroutine will get eventually cancelled— but this will not happen until `runBlocking {}` it's in ends. Perhaps one could try employing a finalizer here?

* Each `asyncSequnce {}` call (and so every `filter {}` and `map {}`) creates a new coroutine. A coroutine, compared to a suspend function, is a very expensive object. This probably makes things considerably slower, although I didn't benchmark it.

* Coroutines can be hard to debug, and having more coroutines doesn't help there! 

I tried making AsyncSequence to see if I could do it. I guess I learned a few things from doing that, and I hope you found this interesting as well. Thanks for reading!