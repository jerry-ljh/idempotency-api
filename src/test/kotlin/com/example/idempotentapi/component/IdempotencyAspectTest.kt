package com.example.idempotentapi.component

import com.example.idempotentapi.util.ErrorCode
import com.example.idempotentapi.util.IdempotencyException
import io.kotest.matchers.shouldBe
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

@Import(IdempotencyAspectTest.AopTestService::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
@SpringBootTest
class IdempotencyAspectTest(
    private val redisTemplate: RedisTemplate<String, String>,
    private val service: AopTestService
) {
    companion object {
        const val TEST_KEY1 = "testKey1"
        const val TEST_KEY2 = "testKey2"
        const val TEST_KEY3 = "testKey3"
    }

    @BeforeEach
    fun clean() {
        val keys = redisTemplate.keys("idempotency*")
        redisTemplate.delete(keys)
    }

    @Test
    fun `동일한 key로 요청이 중복되면 저장된 결과를 반환한다`() {
        // given
        val firstResult = service.getNewValue()
        // when
        val secondResult = service.getNewValue()
        // then
        secondResult shouldBe firstResult
    }

    @Test
    fun `요청 처리중 동일한 key로 요청이 들어오면 예외를 반환한다`() {
        // given
        supplyAsync { service.call(delay = 1000) }
        sleep(100)
        // when & then
        val result = assertThrows<IdempotencyException> { service.call(delay = 1000) }
        result.errorCode shouldBe ErrorCode.CONFLICT_REQUEST
    }

    @Test
    fun `요청 처리중 예외 발생시 처리중 상태를 제거한다`() {
        // when
        assertThrows<Exception> { service.callWithException() }
        // then
        redisTemplate.hasKey(IdempotencyExecutor.getRequestKey(TEST_KEY3)) shouldBe false
    }

    @Service
    class AopTestService {

        @Idempotency(key = TEST_KEY1, expression = "#delay")
        fun call(delay: Int? = null) {
            delay?.let { sleep(1000) }
        }

        @Idempotency(key = TEST_KEY2)
        fun callWithException() {
            throw RuntimeException()
        }

        @Idempotency(key = TEST_KEY3)
        fun getNewValue(): Long {
            sleep(10)
            return System.currentTimeMillis()
        }
    }
}
