package com.example.idempotentapi.controller

import com.example.idempotentapi.util.IdempotencyConflictException
import com.example.idempotentapi.util.IdempotencyFormatException
import com.example.idempotentapi.util.IdempotencyPayloadMismatchException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class IdempotencyControllerAdvice {

    @ExceptionHandler(IdempotencyFormatException::class)
    fun idempotencyFormatException(exception: IdempotencyFormatException): ResponseEntity<String?>? {
        return ResponseEntity.badRequest().body(exception.message)
    }

    @ExceptionHandler(IdempotencyConflictException::class)
    fun idempotencyConflictException(exception: IdempotencyConflictException): ResponseEntity<String?>? {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.message)
    }

    @ExceptionHandler(IdempotencyPayloadMismatchException::class)
    fun idempotencyPayloadMismatchException(exception: IdempotencyPayloadMismatchException): ResponseEntity<String?>? {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(exception.message)
    }
}