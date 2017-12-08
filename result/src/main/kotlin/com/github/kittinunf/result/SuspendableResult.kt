package com.github.kittinunf.result


inline fun <reified X> SuspendableResult<*, *>.getAs() = when (this) {
    is SuspendableResult.Success -> value as? X
    is SuspendableResult.Failure -> error as? X
}

suspend fun <V : Any> SuspendableResult<V, *>.success(f: suspend (V) -> Unit) = fold(f, {})

suspend fun <E : Exception> SuspendableResult<*, E>.failure(f: suspend (E) -> Unit) = fold({}, f)

infix fun <V : Any, E : Exception> SuspendableResult<V, E>.or(fallback: V) = when (this) {
    is SuspendableResult.Success -> this
    else -> SuspendableResult.Success(fallback)
}

infix fun <V : Any, E : Exception> SuspendableResult<V, E>.getOrElse(fallback: V) = when (this) {
    is SuspendableResult.Success -> value
    else -> fallback
}

suspend fun <V : Any, U : Any, E : Exception> SuspendableResult<V, E>.map(transform: suspend (V) -> U): SuspendableResult<U, E> = when (this) {
    is SuspendableResult.Success -> SuspendableResult.Success(transform(value))
    is SuspendableResult.Failure -> SuspendableResult.Failure(error)
}

suspend fun <V : Any, U : Any, E : Exception> SuspendableResult<V, E>.flatMap(transform: suspend (V) -> SuspendableResult<U, E>): SuspendableResult<U, E> = when (this) {
    is SuspendableResult.Success -> transform(value)
    is SuspendableResult.Failure -> SuspendableResult.Failure(error)
}

suspend fun <V : Any, E : Exception, E2 : Exception> SuspendableResult<V, E>.mapError(transform: suspend (E) -> E2) = when (this) {
    is SuspendableResult.Success -> SuspendableResult.Success<V, E2>(value)
    is SuspendableResult.Failure -> SuspendableResult.Failure<V, E2>(transform(error))
}

suspend fun <V : Any, E : Exception, E2 : Exception> SuspendableResult<V, E>.flatMapError(transform: suspend (E) -> SuspendableResult<V, E2>) = when (this) {
    is SuspendableResult.Success -> SuspendableResult.Success(value)
    is SuspendableResult.Failure -> transform(error)
}

suspend fun <V : Any> SuspendableResult<V, *>.any(predicate: suspend (V) -> Boolean): Boolean = when (this) {
    is SuspendableResult.Success -> predicate(value)
    is SuspendableResult.Failure -> false
}

suspend fun <V : Any, U: Any> SuspendableResult<V, *>.fanout(other: suspend () -> SuspendableResult<U, *>): SuspendableResult<Pair<V, U>, *> =
    flatMap { outer -> other().map { outer to it } }

sealed class SuspendableResult<out V : Any, out E : Exception> {

    abstract operator fun component1(): V?
    abstract operator fun component2(): E?

    suspend fun <X> fold(success: suspend (V) -> X, failure: suspend (E) -> X): X {
      return when (this) {
        is Success -> success(this.value)
        is Failure -> failure(this.error)
      }
    }

    abstract fun get(): V

    class Success<out V : Any, out E : Exception>(val value: V) : SuspendableResult<V, E>() {
        override fun component1(): V? = value
        override fun component2(): E? = null

        override fun get(): V = value

        override fun toString() = "[Success: $value]"

        override fun hashCode(): Int = value.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Success<*, *> && value == other.value
        }
    }

    class Failure<out V : Any, out E : Exception>(val error: E) : SuspendableResult<V, E>() {
        override fun component1(): V? = null
        override fun component2(): E? = error

        override fun get(): V = throw error

        fun getException(): E = error

        override fun toString() = "[Failure: $error]"

        override fun hashCode(): Int = error.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Failure<*, *> && error == other.error
        }
    }

    companion object {
        // Factory methods
        fun <E : Exception> error(ex: E) = Failure<Nothing, E>(ex)

        suspend fun <V : Any> of(value: V?, fail: (() -> Exception) = { Exception() }): SuspendableResult<V, Exception> {
            return value?.let { Success<V, Nothing>(it) } ?: error(fail())
        }

        suspend fun <V : Any> of(f: () -> V): SuspendableResult<V, Exception> = try {
            Success(f())
        } catch(ex: Exception) {
            Failure(ex)
        }
    }

}
