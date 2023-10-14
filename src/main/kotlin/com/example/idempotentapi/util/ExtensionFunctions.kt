package com.example.idempotentapi.util

import com.example.idempotentapi.component.IdempotencyApi
import org.springframework.http.HttpMethod
import org.springframework.web.method.HandlerMethod
import javax.servlet.http.HttpServletRequest

const val HTTP_HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"

fun HandlerMethod.enableIdempotency(): Boolean {
    return this.method.annotations.find { it is IdempotencyApi } != null
}

fun HttpServletRequest.containsIdempotencyHeader(): Boolean {
    return this.getHeader(HTTP_HEADER_IDEMPOTENCY_KEY) != null
}

fun HttpServletRequest.getIdempotencyKey(): IdempotencyKey {
    return IdempotencyKey(
        key = this.getHeader(HTTP_HEADER_IDEMPOTENCY_KEY),
        httpMethod = HttpMethod.valueOf(this.method),
        uri = this.requestURI
    )
}