Like `sequence {}`, but asynchronous. Like `Flow`, but with inverted control.

```kt
fun main() = runBlocking {
    asyncSequence {
        for (i in 1..5) {
            delay(100)
            yield(i)
        }
    }.forEach {
        println(it)
    }
}
```

This project is a proof of concept/thought experiment kind of thing and should not be taken seriously.

To run tests, clone the repository and run:

```sh
./gradlew allTests
```
