package asynciterable

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.absoluteValue
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@Test fun `filter {} works`() = runBlocking {
    val evenNumbers = makeSequence().filter {
        delay(0)
        it % 2 == 0
    }.toList()

    assertEquals(listOf(2, 4), evenNumbers)
}


@Test fun `map {} works`() = runBlocking {
    val squaredNumbers = makeSequence().map {
        delay(0)
        it * it
    }.toList()

    assertEquals(listOf(1, 4, 9, 16, 25), squaredNumbers)
}


@Test fun `takeUpTo() works`() = runBlocking {
    val iterator = makeIterator()
    val noElements = iterator.takeUpTo(0).toList()
    val firstThreeElements = iterator.takeUpTo(3).toList()
    val remainingElementsSquared = iterator.takeUpTo(3).map { it * it }.toList()

    assertTrue { noElements.isEmpty() }
    assertEquals(listOf(1, 2, 3), firstThreeElements)
    assertEquals(listOf(16, 25), remainingElementsSquared)
}


@Test fun `toList() works for both asyncIterator & asyncSequence`() = runBlocking {
    assertEquals(listOf(1, 2, 3, 4, 5), makeIterator().toList())
    assertEquals(listOf(1, 2, 3, 4, 5), makeSequence().toList())
}


@Test fun `forEach {} works`() {
    var sum = 0
    runBlocking {
        makeIterator().forEach {
            delay(0)
            sum += it
        }
    }

    assertEquals(15, sum)
}


@Test fun `Async sequence made by toMemoizedAsyncSequence() only ever runs the body once`() = runBlocking {
    var yields = 0

    val memoizingAsyncSequence = asyncSequence {
        for (x in 1..5) {
            delay(10)
            yields++
            yield(x)
        }
    }.toMemoizedAsyncSequence()

    val firstTwo = memoizingAsyncSequence.takeUpTo(2).toList()
    val firstTwoAgain = memoizingAsyncSequence.takeUpTo(2).toList()

    assertEquals(firstTwo, firstTwoAgain)
    assertEquals(2, yields)

    val fullList = memoizingAsyncSequence.toList()

    assertEquals(5, yields)

    val fullListAgain = memoizingAsyncSequence.toList()

    assertEquals(5, yields)

    assertEquals(listOf(1, 2, 3, 4, 5), fullList)
    assertEquals(listOf(1, 2, 3, 4, 5), fullListAgain)
}


@Test fun `Async sequence made by toMemoizedAsyncSequence() can be iterated over concurrently`() = runBlocking {
    var yields = 0

    val memoizingAsyncSequence = asyncSequence {
        for (x in 1..5) {
            delay(100)
            yields++
            yield(x)
        }
    }.toMemoizedAsyncSequence()

    val fullList = async { memoizingAsyncSequence.toList() }
    val fullListAgain = async { memoizingAsyncSequence.toList() }

    val time = measureTimeMillis {
        assertEquals(listOf(1, 2, 3, 4, 5), fullList.await())
        assertEquals(listOf(1, 2, 3, 4, 5), fullListAgain.await())
    }

    assertTrue { time approximatelyEquals 500 }

    assertEquals(5, yields)
}


infix fun Long.approximatelyEquals(other: Long) = (this - other).absoluteValue.toDouble() / this < 0.2
