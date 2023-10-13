package com.example.idempotentapi.util

import com.example.idempotentapi.configuration.CacheKeys
import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.cache.interceptor.SimpleKeyGenerator
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component
import java.time.Duration


@Aspect
@EnableAspectJAutoProxy
@Component
class IdempotencyAspect(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val objectMapper = ObjectMapper()
    private val parser = SpelExpressionParser()
    private val keyGenerator = SimpleKeyGenerator()

    companion object {
        fun getRequestKey(key: String): String {
            validateKey(key)
            return "${CacheKeys.IDEMPOTENCY_REQUEST_KEY}::$key"
        }

        fun getResultKey(key: String): String {
            validateKey(key)
            return "${CacheKeys.IDEMPOTENCY_RESPONSE_KEY}::$key"
        }

        private fun validateKey(key: String) {
            if (key.isBlank()) throw IdempotencyFormatException("key가 비어있습니다.")
        }
    }

    @Around("@annotation(Idempotency)")
    fun idempotencyProcess(joinPoint: ProceedingJoinPoint): Any? {
        val key = generateKey(joinPoint)
        validateKey(key)
        getResult(key)?.let { return it.data }
        return try {
            setExecutingStatus(key)
            execute(key) { joinPoint.proceed() }
        } finally {
            deleteExecutingStatus(key)
        }
    }

    fun execute(key: String, request: () -> Any?): Any? {
        val result = IdempotencyResultWrapper(data = request())
        setResult(key, result)
        return result.data
    }

    private fun getResult(key: String): IdempotencyResultWrapper? {
        val resultKey = getResultKey(key)
        val result = redisTemplate.opsForValue().get(resultKey)
        return result?.let { objectMapper.readValue(it, IdempotencyResultWrapper::class.java) }
    }

    private fun setResult(key: String, result: IdempotencyResultWrapper) {
        val resultKey = getResultKey(key)
        redisTemplate.opsForValue().set(resultKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(1))
    }

    private fun setExecutingStatus(key: String) {
        val requestKey = getRequestKey(key)
        val isExecuting = redisTemplate.opsForValue().setIfAbsent(requestKey, "", Duration.ofSeconds(10)) == false
        if (isExecuting) {
            throw IdempotencyConflictException("이미 처리중인 요청입니다.")
        }
    }

    private fun deleteExecutingStatus(key: String) {
        val requestKey = getRequestKey(key)
        redisTemplate.delete(requestKey)
    }


    private fun generateKey(joinPoint: ProceedingJoinPoint): String {
        val idempotency = (joinPoint.signature as MethodSignature).method.getAnnotation(Idempotency::class.java)
        val expressionValue = getSpeLExpressionValue(joinPoint) ?: keyGenerator.generate(
            joinPoint.target,
            (joinPoint.signature as MethodSignature).method,
            joinPoint.args
        )
        return "${idempotency.key}::$expressionValue"
    }

    private fun getSpeLExpressionValue(joinPoint: ProceedingJoinPoint): String? {
        val idempotency = (joinPoint.signature as MethodSignature).method.getAnnotation(Idempotency::class.java)
        val expression = idempotency.expression.ifEmpty { return null }
        val parameterNames = (joinPoint.signature as MethodSignature).parameterNames
        val context = StandardEvaluationContext().apply {
            parameterNames.indices.forEachIndexed { _, i ->
                val name = parameterNames[i]
                val value = joinPoint.args[i]
                setVariable(name, "[$name=$value]")
            }
        }
        return parser.parseExpression(expression).getValue(context).toString()
    }

    private data class IdempotencyResultWrapper(val data: Any? = null)
}
