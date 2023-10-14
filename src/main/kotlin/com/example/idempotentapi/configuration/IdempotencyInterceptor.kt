package com.example.idempotentapi.configuration

import com.example.idempotentapi.component.IdempotencyExecutor
import com.example.idempotentapi.util.*
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.ContentCachingResponseWrapper
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Configuration
class IdempotencyInterceptor(
    private val idempotencyExecutor: IdempotencyExecutor,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (isIdempotencyRequest(request, handler as HandlerMethod).not()) {
            return true
        }
        if (setIdempotencyResponse(request, response)) return false
        validateConflict(request)
        return true
    }

    override fun postHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        modelAndView: ModelAndView?
    ) {
        if (isIdempotencyRequest(request, handler as HandlerMethod)) {
            val key = request.getIdempotencyKey().toString()
            val responseBody = String((response as ContentCachingResponseWrapper).contentAsByteArray)
            idempotencyExecutor.setResult(key, responseBody)
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        if (isIdempotencyRequest(request, handler as HandlerMethod).not()) {
            return
        }
        if (isConflictException(ex)) {
            return
        }
        val key = request.getIdempotencyKey().toString()
        idempotencyExecutor.deleteExecutingStatus(key)
    }

    private fun isIdempotencyRequest(request: HttpServletRequest, handler: HandlerMethod): Boolean {
        return handler.enableIdempotency() && request.containsIdempotencyHeader()
    }

    private fun setIdempotencyResponse(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val key = request.getIdempotencyKey()
        val result = idempotencyExecutor.getResult(key.toString()) ?: return false
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(result.data.toString())
        return true
    }

    private fun validateConflict(request: HttpServletRequest) {
        val key = request.getIdempotencyKey().toString()
        idempotencyExecutor.setExecutingStatus(key)
    }

    private fun isConflictException(ex: Exception?): Boolean {
        return ex is IdempotencyException && ex.errorCode == ErrorCode.CONFLICT_REQUEST
    }
}