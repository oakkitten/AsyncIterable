@file:Suppress("ControlFlowWithEmptyBody")

package asynciterable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


fun CoroutineScope.makeIterator(delayDuration: Long = 0) = asyncIterator {
    for (x in 1..5) {
        delay(delayDuration)
        yield(x)
    }
}


@Suppress("DIVISION_BY_ZERO")
fun CoroutineScope.makeFailingIterator() = asyncIterator {
    for (x in makeIterator()) yield(x)
    1 / 0
}


@Test fun `Can be empty`() = runBlocking {
    for (x in asyncIterator<Int> {}) throw AssertionError()
}


@Test fun `Can be iterated over`() = runBlocking {
    var sum = 0
    for (x in makeIterator()) sum += x
    assertEquals(15, sum)
}


@Test fun `Is empty if iterated over again`() = runBlocking {
    val iterator = makeIterator()
    var iterations = 0

    for (element in iterator) iterations++
    for (element in iterator) iterations++

    assertEquals(5, iterations)
}


@Test fun `Can be iterated over concurrently`() {
    val single = measureTimeMillis {
        runBlocking {
            launch { for (x in makeIterator(100)) {} }
        }
    }

    val double = measureTimeMillis {
        runBlocking {
            launch { for (x in makeIterator(100)) {} }
            launch { for (x in makeIterator(100)) {} }
        }
    }

    assertTrue { double approximatelyEquals single }
}


@Test fun `Iterating will rethrow exceptions raised in the block`() = runBlocking<Unit> {
    assertFailsWith(ArithmeticException::class) {
        for (x in makeFailingIterator()) {}
    }
}


@Test fun `Throws NoSuchElementException if next() is called after exhaustion`() = runBlocking<Unit> {
    val iterator = makeIterator()

    for (x in iterator) {}

    assertFailsWith(NoSuchElementException::class) {
        iterator.next()
    }
}


@Test fun `Throws IllegalStateException if iterated over after block has thrown`() = runBlocking {
    val iterator = makeFailingIterator()

    assertFailsWith(ArithmeticException::class) {
        for (x in iterator) {}
    }

    val e = assertFailsWith(IllegalStateException::class) {
        for (x in iterator) {}
    }

    assertTrue { e.cause is ArithmeticException }
}


@Test fun `Doesn't automatically proceed after yielding`() = runBlocking {
    for (x in makeFailingIterator()) {
        if (x == 5) break
    }
}


@Test fun `Finalizer is run`() {
    var finalizerRun = false

    runBlocking {
        val iterator = asyncIterator {
            try {
                for (x in 1..5) yield(x)
            } finally {
                finalizerRun = true
            }
        }

        for (x in iterator) {
            if (x == 3) break
        }
    }

    assertTrue { finalizerRun }
}
