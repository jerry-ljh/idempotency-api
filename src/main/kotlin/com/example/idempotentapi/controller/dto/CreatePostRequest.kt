package com.example.idempotentapi.controller.dto

data class CreatePostRequest(
    val userId: Long,
    val contents: String
)