package asynciterable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals


fun CoroutineScope.makeSequence() = asyncSequence {
    for (x in 1..5) yield(x)
}


@Test fun `Can be iterated over again`() = runBlocking {
    val sequence = makeSequence()
    var iterations = 0

    for (element in sequence) iterations++
    for (element in sequence) iterations++

    assertEquals(10, iterations)
}
