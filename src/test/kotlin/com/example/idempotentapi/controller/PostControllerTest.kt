package com.example.idempotentapi.controller

import com.example.idempotentapi.controller.dto.CreatePostRequest
import com.example.idempotentapi.controller.dto.PatchPostRequest
import com.example.idempotentapi.service.IdempotencyService
import com.example.idempotentapi.service.PostService
import com.example.idempotentapi.util.HTTP_HEADER_IDEMPOTENCY_KEY
import com.example.idempotentapi.util.IdempotencyKey
import com.example.idempotentapi.util.getIdempotencyKey
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.SpykBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.util.NestedServletException

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
    fun `게시글 생성 - 멱등성 요청의 처리 결과는 저장된다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        // when
        val result = mockMvc.perform(post("/post", body, idempotencyHeader)).andExpect(status().isOk).andReturn()
        // then
        val savedIdempotencyResponse = idempotencyService.getIdempotencyResponse(result.request.getIdempotencyKey())
        val responseBody = result.response.contentAsString
        savedIdempotencyResponse shouldBe responseBody
    }

    @Test
    fun `게시글 생성 - 멱등성 요청이 중복된 경우 저장된 결과로 응답한다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        val firstResult = mockMvc.perform(post("/post", body, idempotencyHeader)).andExpect(status().isOk).andReturn()
        // when
        val secondResult = mockMvc.perform(post("/post", body, idempotencyHeader)).andExpect(status().isOk).andReturn()
        // then
        val savedIdempotencyResponse =
            idempotencyService.getIdempotencyResponse(firstResult.request.getIdempotencyKey())
        val responseBody = secondResult.response.contentAsString
        savedIdempotencyResponse shouldBe responseBody
        verify(exactly = 1) { postService.createPost(body) }
    }

    @Test
    fun `게시글 생성 - 멱등성 요청 처리에 실패하면 처리중 상태가 제거된다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        every { postService.createPost(body) } throws RuntimeException("게시글 등록 실패")
        // when
        assertThrows<NestedServletException> { mockMvc.perform(post("/post", body, idempotencyHeader)) }
        // then
        val key = IdempotencyKey(key = idempotencyHeader, httpMethod = HttpMethod.POST, uri = "/post")
        idempotencyService.getIdempotencyRequest(key) shouldBe null
    }

    @Test
    fun `게시글 생성 - 멱등키가 잘못된 경우 400(BAD_REQUEST)를 응답한다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val invalidIdempotencyHeader = ""
        // when
        val result = mockMvc.perform(post("/post", body, invalidIdempotencyHeader)).andReturn()
        // then
        result.response.status shouldBe HttpStatus.BAD_REQUEST.value()
    }

    @Test
    fun `게시글 생성 - 멱등성 요청 처리중 중복 요청이 들어오면 409(CONFLICT)를 응답한다`() {
        // given
        val body = CreatePostRequest(userId = 1, contents = "test")
        val idempotencyHeader = System.currentTimeMillis().toString()
        val firstRequest = mockMvc.perform(post("/post", body, idempotencyHeader)).andExpect(status().isOk).andReturn()
        resetIdempotencyResponse(firstRequest.request.getIdempotencyKey())
        // when
        val result = mockMvc.perform(post("/post", body, idempotencyHeader)).andReturn()
        // then
        result.response.status shouldBe HttpStatus.CONFLICT.value()
        verify(exactly = 1) { postService.createPost(body) }
    }

    @Test
    fun `게시글 수정 - 동일한 멱등성 요청에 payload가 다른 경우 422(UNPROCESSABLE_ENTITY)를 응답한다`() {
        // given
        val body1 = PatchPostRequest(userId = 1, contentId = 1, content = "수정 1")
        val body2 = PatchPostRequest(userId = 1, contentId = 1, content = "수정 2")
        val idempotencyHeader = System.currentTimeMillis().toString()
        mockMvc.perform(patch("/post", body1, idempotencyHeader)).andExpect(status().isOk)
        // when
        val result = mockMvc.perform(patch("/post", body2, idempotencyHeader)).andReturn()
        // then
        result.response.status shouldBe HttpStatus.UNPROCESSABLE_ENTITY.value()
    }

    private fun resetIdempotencyResponse(key: IdempotencyKey) {
        redisTemplate.delete("${IdempotencyService.IDEMPOTENCY_RESPONSE}:${key}")
    }

    private fun post(uri: String, body: Any, idempotencyHeader: String): MockHttpServletRequestBuilder {
        return post(uri)
            .content(objectMapper.writeValueAsString(body))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header(HTTP_HEADER_IDEMPOTENCY_KEY, idempotencyHeader)
            .characterEncoding(Charsets.UTF_8)
    }

    private fun patch(uri: String, body: Any, idempotencyHeader: String): MockHttpServletRequestBuilder {
        return patch(uri)
            .content(objectMapper.writeValueAsString(body))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header(HTTP_HEADER_IDEMPOTENCY_KEY, idempotencyHeader)
            .characterEncoding(Charsets.UTF_8)
    }
}