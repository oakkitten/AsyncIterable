package asynciterable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


inline fun <T> AsyncIterable<T>.filter(crossinline block: suspend (T) -> Boolean): AsyncIterator<T> {
    return asyncIterator {
        for (element in this@filter) {
            if (block(element)) yield(element)
        }
    }
}


inline fun <T, R> AsyncIterable<T>.map(crossinline block: suspend (T) -> R): AsyncIterator<R> {
    return asyncIterator {
        for (element in this@map) yield(block(element))
    }
}


inline fun <T> AsyncIterable<T>.takeUpTo(count: Int): AsyncIterator<T> {
    require(count >= 0)
    return asyncIterator {
        val iterator = iterator()
        var index = 0
        while (index++ < count && iterator.hasNext()) {
            yield(iterator.next())
        }
    }
}


suspend fun <T> AsyncIterable<T>.toList(list: MutableList<T> = mutableListOf()): List<T> {
    for (element in this) list.add(element)
    return list
}


suspend inline fun <T> AsyncIterable<T>.forEach(block: (T) -> Unit) {
    for (element in this) block(element)
}


// as the resulting iterable is meant to be iterated over multiple times,
// a feeble attempt was made to make it async-safe;
// this locking logic can probably be significantly optimized
fun <T> AsyncIterable<T>.toMemoizedAsyncSequence(): AsyncIterable<T> {
    val memoizedValues = mutableListOf<T>()
    val sourceIterator = iterator()
    val mutex = Mutex()

    return object : AsyncIterable<T>, CoroutineScope by this@toMemoizedAsyncSequence {
        override fun iterator() = object : AsyncIterator<T>, CoroutineScope by this@toMemoizedAsyncSequence {
            var index = 0

            override suspend fun hasNext() = mutex.withLock {
                index in memoizedValues.indices || sourceIterator.hasNext()
            }

            override suspend fun next() = mutex.withLock {
                if (index in memoizedValues.indices) {
                    memoizedValues[index++]
                } else {
                    sourceIterator.next().also {
                        memoizedValues.add(it)
                        index++
                    }
                }
            }
        }
    }
}
