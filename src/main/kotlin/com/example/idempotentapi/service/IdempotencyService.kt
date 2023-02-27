package com.example.idempotentapi.service

import com.example.idempotentapi.util.IdempotencyConflictException
import com.example.idempotentapi.util.IdempotencyKey
import com.example.idempotentapi.util.IdempotencyPayloadMismatchException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class IdempotencyService(
    private val redisTemplate: RedisTemplate<String, String>,
) {
    companion object {
        const val IDEMPOTENCY_REQUEST = "IDEMPOTENCY_REQUEST"
        const val IDEMPOTENCY_RESPONSE = "IDEMPOTENCY_RESPONSE"
    }

    fun validateConflict(idempotencyKey: IdempotencyKey) {
        val isDuplicatedRequest = setIdempotencyRequest(idempotencyKey) == false
        if (isDuplicatedRequest) throw IdempotencyConflictException("동일한 요청이 처리중입니다.")
    }

    fun validatePayload(idempotencyKey: IdempotencyKey) {
        val payload = getIdempotencyRequest(idempotencyKey) ?: return
        if (payload != idempotencyKey.payload) throw IdempotencyPayloadMismatchException("요청의 본문이 기존 내용과 다릅니다.")
    }

    fun setIdempotencyRequest(key: IdempotencyKey): Boolean? {
        return redisTemplate.opsForValue()
            .setIfAbsent("$IDEMPOTENCY_REQUEST:${key}", key.payload.toString(), Duration.ofMinutes(5))
    }

    fun getIdempotencyRequest(key: IdempotencyKey): String? {
        return redisTemplate.opsForValue().get("$IDEMPOTENCY_REQUEST:${key}")
    }

    fun getIdempotencyResponse(key: IdempotencyKey): String? {
        return redisTemplate.opsForValue().get("$IDEMPOTENCY_RESPONSE:$key")
    }

    fun setIdempotencyResponse(key: IdempotencyKey, value: String, ttl: Duration = Duration.ofMinutes(10)) {
        redisTemplate.opsForValue().setIfAbsent("$IDEMPOTENCY_RESPONSE:$key", value, ttl)
    }

    fun evictIdempotencyRequest(key: IdempotencyKey): Boolean? {
        return redisTemplate.delete("$IDEMPOTENCY_REQUEST:${key}")
    }
}