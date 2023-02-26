package com.example.idempotentapi.util

class IdempotencyFormatException(msg: String?) : RuntimeException(msg)

class IdempotencyConflictException(msg: String?) : RuntimeException(msg)

class IdempotencyPayloadMismatchException(msg: String?) : RuntimeException(msg)