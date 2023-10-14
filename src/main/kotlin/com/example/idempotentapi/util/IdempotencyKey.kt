package com.example.idempotentapi.util

import org.springframework.http.HttpMethod

data class IdempotencyKey(
    val key: String,
    val httpMethod: HttpMethod,
    val uri: String,
) {
    var payload: String? = null

    init {
        if (key.isBlank()) throw IdempotencyException(ErrorCode.INVALID_FORMAT, "멱등키는 빈 값이 될 수 없습니다.")
        if (key.length > 32) {
            throw IdempotencyException(
                ErrorCode.INVALID_FORMAT,
                "멱등키는 최대 32자 이하로 가능합니다. ${key}, length: ${{ key.length }}"
            )
        }
    }
}
