package com.example.idempotentapi.service

import com.example.idempotentapi.util.IdempotencyConflictException
import com.example.idempotentapi.util.IdempotencyFormatException
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
        const val IDEMPOTENCY_RESULT = "IDEMPOTENCY_RESULT"
    }

    fun validateIdempotencyKey(idempotencyKey: IdempotencyKey) {
        if (idempotencyKey.key.isBlank()) throw IdempotencyFormatException("멱등키는 빈 값이 될 수 없습니다.")
        if (idempotencyKey.key.length > 32) throw IdempotencyFormatException("멱등키는 최대 32자 이하로 가능합니다. ${idempotencyKey.key}, length: ${{ idempotencyKey.key.length }}")
    }

    fun validateConflict(idempotencyKey: IdempotencyKey) {
        val isDuplicatedRequest = setIdempotencyRequest(idempotencyKey) == false
        if (isDuplicatedRequest) throw IdempotencyConflictException("동일한 요청이 처리중입니다.")
    }

    fun validatePayload(idempotencyKey: IdempotencyKey) {
        val payload = getIdempotencyRequest(idempotencyKey) ?: return
        if (payload != idempotencyKey.payload) throw IdempotencyPayloadMismatchException("요청의 본문이 기존 내용과 다릅니다.")
    }

    fun getIdempotencyResult(key: IdempotencyKey): String? {
        return redisTemplate.opsForValue().get("$IDEMPOTENCY_RESULT:$key")
    }

    fun setIdempotencyResult(key: IdempotencyKey, value: String, ttl: Duration = Duration.ofMinutes(10)) {
        redisTemplate.opsForValue().setIfAbsent("$IDEMPOTENCY_RESULT:$key", value, ttl)
    }

    fun setIdempotencyRequest(key: IdempotencyKey): Boolean? {
        return redisTemplate.opsForValue()
            .setIfAbsent("$IDEMPOTENCY_REQUEST:${key}", key.payload, Duration.ofMinutes(5))
    }

    fun getIdempotencyRequest(key: IdempotencyKey): String? {
        return redisTemplate.opsForValue().get("$IDEMPOTENCY_REQUEST:${key}")
    }

    fun evictIdempotencyRequest(key: IdempotencyKey): Boolean? {
        return redisTemplate.delete("$IDEMPOTENCY_REQUEST:${key}")
    }
}