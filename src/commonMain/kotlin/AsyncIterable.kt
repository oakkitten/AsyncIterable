package asynciterable

import kotlinx.coroutines.CoroutineScope
import kotlin.experimental.ExperimentalTypeInference


interface AsyncIterable<T>: CoroutineScope {
    operator fun iterator(): AsyncIterator<T>
}


@OptIn(ExperimentalTypeInference::class)
fun <T> CoroutineScope.asyncSequence(
    @BuilderInference block: suspend AsyncCoroutineScope<T>.() -> Unit
): AsyncIterable<T> {
    return object : AsyncIterable<T>, CoroutineScope by this@asyncSequence {
        override fun iterator() = asyncIterator(block)
    }
}
