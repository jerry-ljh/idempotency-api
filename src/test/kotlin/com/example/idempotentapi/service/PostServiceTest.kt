package com.example.idempotentapi.service

import com.example.idempotentapi.controller.dto.CreatePostRequest
import com.example.idempotentapi.repository.PostJpaRepository
import com.example.idempotentapi.util.ErrorCode
import com.example.idempotentapi.util.IdempotencyException
import com.example.idempotentapi.util.callAsync
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
@SpringBootTest
class PostServiceTest(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val postJpaRepository: PostJpaRepository,
    private val sut: PostService,
) {

    @BeforeEach
    fun clear() {
        postJpaRepository.deleteAllInBatch()
        val keys = redisTemplate.keys("*")
        redisTemplate.delete(keys)
    }

    @Test
    fun `게시글 생성 - 동시 요청 테스트 idempotencyExecutor`() {
        // given
        val input = CreatePostRequest(userId = 1, contents = "안녕하세요")
        // when
        val requests = callAsync(thread = 300) { sut.createPostWithIdempotencyExecutor(input) }
        // then
        requests.filterIsInstance<IdempotencyException>().forEach { it.errorCode shouldBe ErrorCode.CONFLICT_REQUEST }
        postJpaRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `게시글 생성 - 순차 요청 테스트 idempotencyExecutor`() {
        // given
        val input = CreatePostRequest(userId = 1, contents = "안녕하세요")
        // when
        sut.createPostWithIdempotencyExecutor(input)
        sut.createPostWithIdempotencyExecutor(input)
        // then
        postJpaRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `게시글 생성 - 동시 요청 테스트 AOP`() {
        // given
        val input = CreatePostRequest(userId = 1, contents = "안녕하세요")
        // when
        val requests = callAsync(thread = 300) { sut.createPostWithIdempotencyAop(input) }
        // then
        requests.filterIsInstance<IdempotencyException>().forEach { it.errorCode shouldBe ErrorCode.CONFLICT_REQUEST }
        postJpaRepository.findAll() shouldHaveSize 1
    }

    @Test
    fun `게시글 생성 - 순차 요청 테스트 AOP`() {
        // given
        val input = CreatePostRequest(userId = 1, contents = "안녕하세요")
        // when
        sut.createPostWithIdempotencyAop(input)
        sut.createPostWithIdempotencyAop(input)
        // then
        postJpaRepository.findAll() shouldHaveSize 1
    }
}