package com.tayf.app.core

sealed class Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>()
    data class Fail(val message: String, val cause: Throwable? = null) : Outcome<Nothing>()
}
