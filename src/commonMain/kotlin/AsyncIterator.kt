package asynciterable

import kotlinx.coroutines.*
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.*
import kotlin.experimental.ExperimentalTypeInference


interface AsyncCoroutineScope<T> : CoroutineScope {
    suspend fun yield(value: T)
}


// implements AsyncIterable as this makes it possible
// to use the same extension methods for both AsyncIterator and AsyncIterable
interface AsyncIterator<T>: AsyncIterable<T>, CoroutineScope {
    override operator fun iterator() = this
    suspend operator fun hasNext(): Boolean
    suspend operator fun next(): T
}


private sealed class Status<in T> {
    object NotReady : Status<Any?>()
    class Ready<T>(val value: T) : Status<T>()
    class Failed<T>(val exception: Throwable) : Status<T>()
    object Done : Status<Any?>()
}


private class AsyncIteratorImpl<T>(
    override val coroutineContext: CoroutineContext
) : AsyncIterator<T>, AsyncCoroutineScope<T>, Continuation<Unit> {
    lateinit var outerContinuation: Continuation<Status<T>>
    lateinit var innerContinuation: Continuation<Unit>

    var status: Status<T> = Status.NotReady

    override operator fun iterator() = this

    suspend fun ensureNext() {
        if (status is Status.NotReady) {
            val status = suspendCoroutine<Status<T>> {
                outerContinuation = it
                innerContinuation.resume(Unit)
            }

            this.status = status
            if (status is Status.Failed) throw status.exception
        }
    }

    fun throwBadStatus(): Nothing {
        throw when (val status = status) {
            is Status.Done -> NoSuchElementException("Asynchronous iterator is exhausted")
            is Status.Failed -> IllegalStateException("Asynchronous sequence block previously threw an exception",
                                                      status.exception)
            else -> IllegalStateException("This shouldn't be possible")
        }
    }

    override suspend operator fun hasNext(): Boolean {
        ensureNext()
        return when (status) {
            is Status.Done -> false
            is Status.Ready -> true
            else -> throwBadStatus()
        }
    }

    override suspend operator fun next(): T {
        ensureNext()
        when (val status = status) {
            is Status.Ready -> return status.value.also { this.status = Status.NotReady }
            else -> throwBadStatus()
        }
    }

    override suspend fun yield(value: T) = suspendCancellableCoroutine<Unit> {
        innerContinuation = it
        outerContinuation.resume(Status.Ready(value))
    }

    // this and below is Continuation<Unit> stuff
    override val context = coroutineContext

    override fun resumeWith(result: Result<Unit>) {
        when (val exception = result.exceptionOrNull()) {
            is CancellationException -> return
            null -> outerContinuation.resume(Status.Done)
            else -> outerContinuation.resume(Status.Failed(exception))
        }
    }
}


// BuilderInference allows inferring the generic parameter of asyncIterator from the type passed to yield
@OptIn(ExperimentalTypeInference::class)
fun <T> CoroutineScope.asyncIterator(
    @BuilderInference block: suspend AsyncCoroutineScope<T>.() -> Unit
): AsyncIterator<T> {
    return AsyncIteratorImpl<T>(coroutineContext).apply {
        innerContinuation = block.createCoroutineUnintercepted(this, this)
    }
}
