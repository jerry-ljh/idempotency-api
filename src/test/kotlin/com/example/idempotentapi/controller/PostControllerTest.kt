package com.example.idempotentapi.controller

import com.example.idempotentapi.callAsync
import com.example.idempotentapi.component.IdempotencyExecutor
import com.example.idempotentapi.controller.dto.CreatePostRequest
import com.example.idempotentapi.service.PostService
import com.example.idempotentapi.util.HTTP_HEADER_IDEMPOTENCY_KEY
import com.example.idempotentapi.util.IdempotencyKey
import com.example.idempotentapi.util.getIdempotencyKey
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PostControllerTest(
    private val mockMvc: MockMvc,
    private val idempotencyExecutor: IdempotencyExecutor,
    private val redisTemplate: RedisTemplate<String, String>,
) {
    @MockkBean(relaxed = true)
    private lateinit var postService: PostService
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun clean() {
        val keys = redisTemplate.keys("*")
        redisTemplate.delete(keys)
    }

    @Test
    fun `게시글 생성 - 멱등성 API 요청이 성공하면 처리 결과는 저장된다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        // when
        val result = mockMvc.perform(post("/post", body, createIdempotencyHeader()))
            .andExpect(status().isOk)
            .andReturn()
        // then
        val savedResponse = idempotencyExecutor.getResult(result.request.getIdempotencyKey().toString())!!.data
        savedResponse shouldBe result.response.contentAsString
    }

    @Test
    fun `게시글 생성 - 멱등성 API 요청이 중복되면 동일한 결과로 응답한다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = createIdempotencyHeader()
        // when
        val firstRequest = mockMvc.perform(post("/post", body, idempotencyHeader))
            .andExpect(status().isOk)
            .andReturn()

        val secondRequest = mockMvc.perform(post("/post", body, idempotencyHeader))
            .andExpect(status().isOk)
            .andReturn()
        // then
        firstRequest.response.contentAsString shouldBe secondRequest.response.contentAsString
        verify(exactly = 1) { postService.createPost(body) }
    }

    @Test
    fun `게시글 생성 - 멱등성 API 요청 처리에 실패하면 요청 처리중 상태가 제거된다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        every { postService.createPost(body) } throws RuntimeException("게시글 등록 실패")
        // when
        shouldThrow<Exception> { mockMvc.perform(post("/post", body, idempotencyHeader)) }
        // then
        val key = IdempotencyKey(key = idempotencyHeader, httpMethod = HttpMethod.POST, uri = "/post")
        idempotencyExecutor.getExecutingStatus(key.toString()) shouldBe null
    }

    @Test
    fun `게시글 생성 - 멱등성 API CONFLICT 상황에서 요청 처리중 상태는 유지한다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        val key = IdempotencyKey(key = idempotencyHeader, httpMethod = HttpMethod.POST, uri = "/post")
        idempotencyExecutor.setExecutingStatus(key.toString())
        // when
        mockMvc.perform(post("/post", body, idempotencyHeader))
            .andExpect(status().isConflict)
            .andReturn()
        // then
        idempotencyExecutor.getExecutingStatus(key.toString()) shouldNotBe null
    }

    @Test
    fun `게시글 생성 - 멱등키가 잘못된 경우 400(BAD_REQUEST)를 응답한다 - 잘못된 HEADER`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val header = ""
        // when & then
        mockMvc.perform(post("/post", body, header))
            .andExpect(status().isBadRequest)
            .andReturn()
    }

    @Test
    fun `게시글 생성 - 멱등키가 잘못된 경우 400(BAD_REQUEST)를 응답한다 - 멱등키 크기 제한`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val tooLongHeader = "1234qwefasdvasgetqwefsdkjchaskfgqweiufgasdfbkajs"
        // when & then
        mockMvc.perform(post("/post", body, tooLongHeader))
            .andExpect(status().isBadRequest)
            .andReturn()
    }

    @Test
    fun `게시글 생성 - 멱등키가 잘못된 경우 400(BAD_REQUEST)를 응답한다 - 멱등키 누락`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        // when & then
        mockMvc.perform(post("/post", body))
            .andExpect(status().isBadRequest)
            .andReturn()
    }

    @Test
    fun `게시글 생성 - 멱등성 API 처리중 요청이 들어오면 409(CONFLICT)를 응답한다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        val key = IdempotencyKey(key = idempotencyHeader, httpMethod = HttpMethod.POST, uri = "/post")
        idempotencyExecutor.setExecutingStatus(key.toString())
        // when & then
        mockMvc.perform(post("/post", body, idempotencyHeader))
            .andExpect(status().isConflict)
            .andReturn()
    }

    @Test
    fun `게시글 생성 - 동시 요청 테스트`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        every { postService.createPost(any()) } returns Thread.sleep(1000)
        // when
        callAsync(thread = 300) { mockMvc.perform(post("/post", body, idempotencyHeader)).andReturn() }
        // then
        verify(exactly = 1) { postService.createPost(body) }
    }

    private fun post(uri: String, body: Any, idempotencyHeader: String): MockHttpServletRequestBuilder {
        return post(uri)
            .content(objectMapper.writeValueAsString(body))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header(HTTP_HEADER_IDEMPOTENCY_KEY, idempotencyHeader)
            .characterEncoding(Charsets.UTF_8)
    }

    private fun createIdempotencyHeader(): String {
        return System.currentTimeMillis().toString()
    }
}