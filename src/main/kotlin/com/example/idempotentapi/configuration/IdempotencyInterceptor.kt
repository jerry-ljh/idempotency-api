package com.example.idempotentapi.configuration

import com.example.idempotentapi.service.IdempotencyService
import com.example.idempotentapi.util.containsIdempotencyHeader
import com.example.idempotentapi.util.enableIdempotency
import com.example.idempotentapi.util.getIdempotencyKey
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.ContentCachingResponseWrapper
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Configuration
class IdempotencyInterceptor(
    private val idempotencyService: IdempotencyService,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (isIdempotencyRequest(request, handler as HandlerMethod)) {
            val key = request.getIdempotencyKey()
            idempotencyService.validateIdempotencyKey(key)
            val result = idempotencyService.getIdempotencyResult(key)
            if (result != null) {
                response.characterEncoding = Charsets.UTF_8.name()
                response.writer.write(result)
                return false
            }
            idempotencyService.validateConflict(key)
        }
        return true
    }

    override fun postHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        modelAndView: ModelAndView?
    ) {
        if (isIdempotencyRequest(request, handler as HandlerMethod)) {
            val key = request.getIdempotencyKey()
            val responseBody = String((response as ContentCachingResponseWrapper).contentAsByteArray)
            idempotencyService.setIdempotencyResult(key, responseBody)
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        if (isIdempotencyRequest(request, handler as HandlerMethod)) {
            val key = request.getIdempotencyKey()
            idempotencyService.evictIdempotencyRequest(key)
        }
    }

    private fun isIdempotencyRequest(request: HttpServletRequest, handler: HandlerMethod): Boolean {
        return handler.enableIdempotency() && request.containsIdempotencyHeader()
    }
}