package com.example.idempotentapi.component

import com.example.idempotentapi.configuration.CacheKeys
import com.example.idempotentapi.util.ErrorCode
import com.example.idempotentapi.util.IdempotencyException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class IdempotencyExecutor(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    companion object {
        fun getRequestKey(key: String): String {
            return "${CacheKeys.IDEMPOTENCY_REQUEST_KEY}::$key"
        }

        fun getResultKey(key: String): String {
            return "${CacheKeys.IDEMPOTENCY_RESPONSE_KEY}::$key"
        }

        private fun validateKey(key: String) {
            if (key.isBlank()) throw IdempotencyException(ErrorCode.INVALID_FORMAT, "key가 비어있습니다.")
        }
    }


    fun execute(key: String, request: () -> Any?): Any? {
        validateKey(key)
        getResult(key)?.let { return it.data }
        return try {
            setExecutingStatus(key)
            setResult(key, request)
            deleteExecutingStatus(key)
        } catch (e: Exception) {
            if (e is IdempotencyException && e.errorCode == ErrorCode.CONFLICT_REQUEST) throw e
            deleteExecutingStatus(key)
            return e
        }
    }

    private fun setResult(key: String, request: () -> Any?): Any? {
        val result = IdempotencyResult(data = request())
        setResult(key, result)
        return result.data
    }

    private fun getResult(key: String): IdempotencyResult? {
        val resultKey = getResultKey(key)
        val result = redisTemplate.opsForValue().get(resultKey)
        return result?.let { it as IdempotencyResult }
    }

    private fun setResult(key: String, result: IdempotencyResult) {
        val resultKey = getResultKey(key)
        redisTemplate.opsForValue().set(resultKey, result, Duration.ofMinutes(1))
    }

    private fun setExecutingStatus(key: String) {
        val requestKey = getRequestKey(key)
        val isExecuting = redisTemplate.opsForValue().setIfAbsent(requestKey, "", Duration.ofSeconds(10)) == false
        if (isExecuting) {
            throw IdempotencyException(ErrorCode.CONFLICT_REQUEST, "requestKey: $requestKey, 이미 처리중인 요청입니다.")
        }
    }

    private fun deleteExecutingStatus(key: String) {
        val requestKey = getRequestKey(key)
        redisTemplate.delete(requestKey)
    }
}
