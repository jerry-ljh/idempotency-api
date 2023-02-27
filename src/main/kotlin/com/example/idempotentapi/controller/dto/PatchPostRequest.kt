package com.example.idempotentapi.controller.dto

data class PatchPostRequest(
    val userId: Long,
    val contentId: Long,
    val content: String
)