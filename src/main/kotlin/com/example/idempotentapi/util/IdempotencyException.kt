package com.example.idempotentapi.util

class IdempotencyException(val errorCode: ErrorCode, val msg: String? = null) : RuntimeException(msg) {
    override val message: String
        get() = "errorCode:$errorCode, msg: $msg"

}