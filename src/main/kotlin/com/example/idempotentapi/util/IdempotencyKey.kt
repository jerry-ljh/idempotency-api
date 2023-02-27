package com.example.idempotentapi.util

import org.springframework.http.HttpMethod

data class IdempotencyKey(
    val key: String,
    val httpMethod: HttpMethod,
    val uri: String,
) {
    var payload: String = ""
}
