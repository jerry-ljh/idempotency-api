package com.example.idempotentapi.configuration

object CacheKeys {
    const val IDEMPOTENCY_REQUEST_KEY = "idempotency.request"
    const val IDEMPOTENCY_RESPONSE_KEY = "idempotency.response"
    const val POST_CREATE_KEY = "post.create"
}
