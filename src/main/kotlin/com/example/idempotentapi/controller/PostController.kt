package com.example.idempotentapi.controller

import com.example.idempotentapi.controller.dto.CreatePostRequest
import com.example.idempotentapi.controller.dto.PatchPostRequest
import com.example.idempotentapi.service.PostService
import com.example.idempotentapi.util.IdempotencyApi
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PostController(
    private val postService: PostService
) {
    @IdempotencyApi
    @PostMapping("/post")
    fun createPost(@RequestBody body: CreatePostRequest): ResponseEntity<String> {
        postService.createPost(body)
        return ResponseEntity.ok().body("게시글 생성 성공")
    }

    @IdempotencyApi(usePayloadValidation = true)
    @PatchMapping("/post")
    fun patchPost(@RequestBody body: PatchPostRequest): ResponseEntity<String> {
        postService.updatePost(body)
        return ResponseEntity.ok().body("게시글 수정 성공")
    }
}