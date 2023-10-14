package com.example.idempotentapi.component

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotency(
    val key: String,
    val expression: String = "" // SpeL expression
)