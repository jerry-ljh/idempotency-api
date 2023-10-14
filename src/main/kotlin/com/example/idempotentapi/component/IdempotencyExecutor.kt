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
        try {
            setExecutingStatus(key)
            val result = request()
            setResult(key, result)
            deleteExecutingStatus(key)
            return result
        } catch (e: Exception) {
            if (e is IdempotencyException && e.errorCode == ErrorCode.CONFLICT_REQUEST) throw e
            deleteExecutingStatus(key)
            throw e
        }
    }

    fun getResult(key: String): IdempotencyResult? {
        val resultKey = getResultKey(key)
        val result = redisTemplate.opsForValue().get(resultKey)
        return result?.let { it as IdempotencyResult }
    }

    fun setResult(key: String, data: Any?) {
        val resultKey = getResultKey(key)
        redisTemplate.opsForValue().set(resultKey, IdempotencyResult(data = data), Duration.ofMinutes(1))
    }

    fun getExecutingStatus(key: String): String? {
        val requestKey = getRequestKey(key)
        val result = redisTemplate.opsForValue().get(requestKey)
        return result?.let { it as String }
    }

    fun setExecutingStatus(key: String) {
        val requestKey = getRequestKey(key)
        val isExecuting = redisTemplate.opsForValue().setIfAbsent(requestKey, "", Duration.ofSeconds(10)) == false
        if (isExecuting) {
            throw IdempotencyException(ErrorCode.CONFLICT_REQUEST, "requestKey: $requestKey, 이미 처리중인 요청입니다.")
        }
    }

    fun deleteExecutingStatus(key: String) {
        val requestKey = getRequestKey(key)
        redisTemplate.delete(requestKey)
    }
}
