package com.example.idempotentapi.component

import com.example.idempotentapi.util.ErrorCode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import java.lang.Thread.sleep
import java.util.concurrent.CompletableFuture.supplyAsync


@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
@SpringBootTest
class IdempotencyExecutorTest(
    private val idempotencyExecutor: IdempotencyExecutor,
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val TEST_KEY = "test"
    val service = spyk<TestService>()

    @BeforeEach
    fun clean() {
        redisTemplate.delete(IdempotencyExecutor.getRequestKey(TEST_KEY))
        redisTemplate.delete(IdempotencyExecutor.getResultKey(TEST_KEY))
    }

    @Test
    fun `저장된 결과가 없다면 요청을 실행한다`() {
        // when
        idempotencyExecutor.execute(TEST_KEY) { service.call() }
        // then
        verify { service.call() }
    }

    @Test
    fun `동일한 key로 요청이 중복되면 저장된 결과를 반환한다`() {
        // given
        val firstResult = idempotencyExecutor.execute(TEST_KEY) { service.getNewValue() }
        // when
        val secondResult = idempotencyExecutor.execute(TEST_KEY) { service.getNewValue() }
        // then
        secondResult shouldBe firstResult
        verify(exactly = 1) { service.getNewValue() }
    }

    @Test
    fun `동일한 key로 요청이 중복되면 저장된 결과를 반환한다 - Unit 반환 타입 처리`() {
        // given
        val firstResult = idempotencyExecutor.execute(TEST_KEY) { service.call() }
        // when
        val secondResult = idempotencyExecutor.execute(TEST_KEY) { service.call() }
        // then
        verify(exactly = 1) { service.call() }
        firstResult shouldBe secondResult
    }

    @Test
    fun `요청 처리중 동일한 key로 요청이 들어오면 예외를 반환한다`() {
        // given
        val firstRequest = supplyAsync {
            idempotencyExecutor.execute(TEST_KEY) {
                service.call()
                sleep(5000) // 처리 시간을 길게 잡아놓음
            }
        }
        sleep(100)
        // when
        val secondRequest = assertThrows<Exception> {
            supplyAsync { idempotencyExecutor.execute(TEST_KEY) { service.call() } }.get()
        }
        // then
        secondRequest.message shouldContain "${ErrorCode.CONFLICT_REQUEST}"
        verify(exactly = 1) { service.call() }
    }

    @Test
    fun `요청 처리중 예외 발생시 처리중 상태를 제거한다`() {
        // when
        assertThrows<Exception> {
            idempotencyExecutor.execute(TEST_KEY) { throw RuntimeException("예외 발생") }
        }
        // then
        redisTemplate.hasKey(IdempotencyExecutor.getRequestKey(TEST_KEY)) shouldBe false
    }
}

class TestService {

    private val log = LoggerFactory.getLogger(this::class.simpleName)
    fun call() {
        log.info("call")
    }

    fun getNewValue(): Long {
        return System.currentTimeMillis()
    }
}