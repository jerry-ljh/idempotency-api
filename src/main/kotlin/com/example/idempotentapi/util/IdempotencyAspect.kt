package com.example.idempotentapi.util

import com.example.idempotentapi.component.IdempotencyExecutor
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.cache.interceptor.SimpleKeyGenerator
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component


@Aspect
@EnableAspectJAutoProxy
@Component
class IdempotencyAspect(
    private val idempotencyExecutor: IdempotencyExecutor,
) {
    private val parser = SpelExpressionParser()
    private val keyGenerator = SimpleKeyGenerator()

    @Around("@annotation(Idempotency)")
    fun idempotencyProcess(joinPoint: ProceedingJoinPoint): Any? {
        val key = generateKey(joinPoint)
        return idempotencyExecutor.execute(key) {
            joinPoint.proceed()
        }
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
}
