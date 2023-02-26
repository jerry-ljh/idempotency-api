package com.example.idempotentapi.controller

import com.example.idempotentapi.controller.dto.CreatePostRequest
import com.example.idempotentapi.service.IdempotencyService
import com.example.idempotentapi.service.PostService
import com.example.idempotentapi.util.HTTP_HEADER_IDEMPOTENCY_KEY
import com.example.idempotentapi.util.IdempotencyKey
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.SpykBean
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.CompletableFuture

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PostControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var idempotencyService: IdempotencyService

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @SpykBean
    private lateinit var postService: PostService
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun clean() {
        redisTemplate.delete("*")
    }

    @Test
    fun `게시글 생성 테스트`() {
        val body = CreatePostRequest(userId = 1, contents = "test")
        mockMvc.perform(
            post("/post")
                .content(objectMapper.writeValueAsString(body))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .characterEncoding(Charsets.UTF_8)
        )
            .andExpect(status().isOk)
            .andExpect(content().string("게시글 생성 성공"))

        verify(exactly = 1) { postService.createPost(body) }
    }

    @Test
    fun `게시글 생성 - 멱등성 요청에 성공하면 처리중 플래그가 삭제되고 처리 결과가 저장된다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        mockMvc.perform(
            post("/post")
                .content(objectMapper.writeValueAsString(body))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HTTP_HEADER_IDEMPOTENCY_KEY, idempotencyHeader)
                .characterEncoding(Charsets.UTF_8)
        )
            .andExpect(status().isOk)
            .andExpect(content().string("게시글 생성 성공"))

        val key = IdempotencyKey(key = idempotencyHeader, httpMethod = HttpMethod.POST, uri = "/post")
        idempotencyService.getIdempotencyRequest(key) shouldBe null
        idempotencyService.getIdempotencyResult(key) shouldNotBe null

    }

    @Test
    fun `게시글 생성 - 멱등성 요청이 중복된 경우 이미 처리된 결과로 응답한다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        mockMvc.perform(
            post("/post")
                .content(objectMapper.writeValueAsString(body))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HTTP_HEADER_IDEMPOTENCY_KEY, idempotencyHeader)
                .characterEncoding(Charsets.UTF_8)
        )
            .andExpect(status().isOk)
            .andExpect(content().string("게시글 생성 성공"))

        val key = IdempotencyKey(key = idempotencyHeader, httpMethod = HttpMethod.POST, uri = "/post")
        mockMvc.perform(
            post("/post")
                .content(objectMapper.writeValueAsString(body))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HTTP_HEADER_IDEMPOTENCY_KEY, idempotencyHeader)
                .characterEncoding(Charsets.UTF_8)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(idempotencyService.getIdempotencyResult(key)!!))

        verify(exactly = 1) { postService.createPost(body) }
    }

    @Test
    fun `게시글 생성 - 멱등성 키가 잘못된 경우 400로 응답한다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = ""
        mockMvc.perform(
            post("/post")
                .content(objectMapper.writeValueAsString(body))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HTTP_HEADER_IDEMPOTENCY_KEY, idempotencyHeader)
                .characterEncoding(Charsets.UTF_8)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `게시글 생성 - 멱등성 요청을 처리중 중복 요청이 발생한 경우 경우 409로 응답한다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        val firstRequest = CompletableFuture.supplyAsync {
            mockMvc.perform(
                post("/post")
                    .content(objectMapper.writeValueAsString(body))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HTTP_HEADER_IDEMPOTENCY_KEY, idempotencyHeader)
                    .characterEncoding(Charsets.UTF_8)
            )
                .andExpect(status().isOk)
        }
        Thread.sleep(300)
        val secondRequest = CompletableFuture.supplyAsync {
            mockMvc.perform(
                post("/post")
                    .content(objectMapper.writeValueAsString(body))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HTTP_HEADER_IDEMPOTENCY_KEY, idempotencyHeader)
                    .characterEncoding(Charsets.UTF_8)
            )
                .andExpect(status().isConflict)
        }
        firstRequest.join()
        secondRequest.join()

        verify(exactly = 1) { postService.createPost(body) }
    }
}