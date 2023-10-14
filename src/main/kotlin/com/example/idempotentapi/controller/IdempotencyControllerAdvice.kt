package com.example.idempotentapi.controller

import com.example.idempotentapi.util.ErrorCode
import com.example.idempotentapi.util.IdempotencyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class IdempotencyControllerAdvice {

    @ExceptionHandler(IdempotencyException::class)
    fun idempotencyExceptionHandler(exception: IdempotencyException): ResponseEntity<String?>? {
        return when (exception.errorCode) {
            ErrorCode.INVALID_FORMAT -> ResponseEntity.badRequest().body(exception.message)
            ErrorCode.CONFLICT_REQUEST -> ResponseEntity.status(HttpStatus.CONFLICT).body(exception.message)
            ErrorCode.PAYLOAD_MISMATCH -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(exception.message)
        }
    }
}