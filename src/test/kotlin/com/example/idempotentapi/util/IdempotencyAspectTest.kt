package com.example.idempotentapi.util

import com.example.idempotentapi.util.IdempotencyAspectTest.Companion.TEST_KEY1
import com.example.idempotentapi.util.IdempotencyAspectTest.Companion.TEST_KEY2
import com.ninjasquad.springmockk.SpykBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import java.lang.Thread.sleep
import java.util.concurrent.CompletableFuture.supplyAsync

@Import(TestService::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
@SpringBootTest
class IdempotencyAspectTest(
    private val redisTemplate: RedisTemplate<String, String>,
    private val service: TestService,
) {
    companion object {
        const val TEST_KEY1 = "testKey1"
        const val TEST_KEY2 = "testKey2"
    }

    @SpykBean
    private lateinit var aspect: IdempotencyAspect

    @BeforeEach
    fun clean() {
        val keys = redisTemplate.keys("idempotency*")
        redisTemplate.delete(keys)
    }

    @Test
    fun `저장된 결과가 없다면 요청을 실행한다`() {
        // when
        service.execute(delay = 100)
        // then
        verify(exactly = 1) { aspect.execute(any(), any()) }
    }

    @Test
    fun `동일한 key로 요청이 중복되면 저장된 결과를 반환한다`() {
        // given
        val firstResult = service.getNewValue()
        // when
        val secondResult = service.getNewValue()
        // then
        secondResult shouldBe firstResult
        verify(exactly = 1) { aspect.execute(any(), any()) }
    }

    @Test
    fun `요청 처리중 동일한 key로 요청이 들어오면 예외를 반환한다`() {
        // given
        supplyAsync { service.execute(delay = 1000) }
        sleep(100)
        // when & then
        assertThrows<IdempotencyConflictException> { service.execute(delay = 1000) }
    }

    @Test
    fun `요청 처리중 동일한 key로 요청이 들어오면 하나의 요청만 처리된다`() {
        // given
        supplyAsync { service.execute(delay = 1000) }
        supplyAsync { service.execute(delay = 1000) }
        sleep(100)
        // when & then
        verify(exactly = 1) { aspect.execute(any(), any()) }
    }

    @Test
    fun `요청 처리중 예외 발생시 처리중 상태를 제거한다`() {
        // given
        every { aspect.execute(any(), any()) } throws RuntimeException("예외 발생")
        // when
        assertThrows<Exception> { service.execute() }
        // then
        redisTemplate.hasKey(IdempotencyAspect.getRequestKey(TEST_KEY1)) shouldBe false
    }


}

@Service
class TestService {

    @Idempotency(key = TEST_KEY1, expression = "#delay")
    fun execute(delay: Int? = null) {
        delay?.let { sleep(1000) }
    }

    @Idempotency(key = TEST_KEY2)
    fun getNewValue(): Long {
        sleep(10)
        return System.currentTimeMillis()
    }
}