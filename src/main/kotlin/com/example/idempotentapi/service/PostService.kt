package com.example.idempotentapi.service

import com.example.idempotentapi.controller.dto.CreatePostRequest
import com.example.idempotentapi.domain.Post
import com.example.idempotentapi.repository.PostJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PostService(
    val postRepository: PostJpaRepository
) {

    private val log = LoggerFactory.getLogger(this::class.simpleName)

    fun createPost(postRequest: CreatePostRequest) {
        val postEntity = Post(contents = postRequest.contents)
        postRepository.save(postEntity)
        log.info("게시글이 포스팅 content: ${postEntity.contents}")
    }
}