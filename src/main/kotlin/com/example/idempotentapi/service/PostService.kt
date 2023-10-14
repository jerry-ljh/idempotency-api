package com.example.idempotentapi.service

import com.example.idempotentapi.component.Idempotency
import com.example.idempotentapi.component.IdempotencyExecutor
import com.example.idempotentapi.configuration.CacheKeys.POST_CREATE_KEY
import com.example.idempotentapi.controller.dto.CreatePostRequest
import com.example.idempotentapi.controller.dto.PatchPostRequest
import com.example.idempotentapi.domain.Post
import com.example.idempotentapi.repository.PostJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.lang.Thread.sleep

@Service
class PostService(
    private val postRepository: PostJpaRepository,
    private val idempotencyExecutor: IdempotencyExecutor
) {

    private val log = LoggerFactory.getLogger(this::class.simpleName)

    fun createPost(postRequest: CreatePostRequest) {
        val postEntity = Post(userId = postRequest.userId, contents = postRequest.contents)
        postRepository.save(postEntity)
        sleep(3000)
        log.info("게시글 포스팅 content: ${postEntity.contents}")
    }

    fun createPostWithIdempotencyExecutor(postRequest: CreatePostRequest) {
        val key = "$POST_CREATE_KEY::${postRequest.userId},${postRequest.contents.hashCode()}"
        idempotencyExecutor.execute(key) { createPost(postRequest) }
    }

    @Idempotency(key = POST_CREATE_KEY, expression = "#postRequest.userId + ',' + #postRequest.contents.hashCode()")
    fun createPostWithIdempotencyAop(postRequest: CreatePostRequest) {
        createPost(postRequest)
    }

    fun updatePost(postRequest: PatchPostRequest) {
        log.info("게시글이 포스팅 수정 content: $postRequest")
    }
}