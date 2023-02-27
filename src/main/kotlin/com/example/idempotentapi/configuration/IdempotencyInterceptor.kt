package com.example.idempotentapi.configuration

import com.example.idempotentapi.service.IdempotencyService
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
    private val idempotencyService: IdempotencyService,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (isIdempotencyRequest(request, handler as HandlerMethod)) {
            val key = request.getIdempotencyKey()
            idempotencyService.validateIdempotencyKey(key)
            if (handler.usePayloadValidation()) {
                key.payload = (request as CustomContentCachedRequestWrapper).payload
                idempotencyService.validatePayload(key)
            }
            if (setIdempotencyResponse(key, response)) {
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
        if (isIdempotencyRequest(request, handler as HandlerMethod) && ex != null) {
            val key = request.getIdempotencyKey()
            idempotencyService.evictIdempotencyRequest(key)
        }
    }

    private fun isIdempotencyRequest(request: HttpServletRequest, handler: HandlerMethod): Boolean {
        return handler.enableIdempotency() && request.containsIdempotencyHeader()
    }

    private fun setIdempotencyResponse(key: IdempotencyKey, response: HttpServletResponse): Boolean {
        val result = idempotencyService.getIdempotencyResult(key) ?: return false
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(result)
        return true
    }
}