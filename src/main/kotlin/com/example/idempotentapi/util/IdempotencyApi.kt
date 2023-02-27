package com.example.idempotentapi.util

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IdempotencyApi(
    val usePayloadValidation: Boolean = false
)