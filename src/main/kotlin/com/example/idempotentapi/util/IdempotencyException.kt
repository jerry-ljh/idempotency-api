package com.example.idempotentapi.util

class IdempotencyException(val errorCode: ErrorCode, val msg: String? = null) : RuntimeException(msg) {
    override val message: String
        get() = "errorCode:$errorCode, msg: $msg"
}

class IdempotencyFormatException(msg: String?) : RuntimeException(msg)

class IdempotencyConflictException(msg: String?) : RuntimeException(msg)

class IdempotencyPayloadMismatchException(msg: String?) : RuntimeException(msg)