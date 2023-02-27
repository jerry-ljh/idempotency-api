package com.example.idempotentapi.configuration

import com.example.idempotentapi.service.IdempotencyService
import com.example.idempotentapi.util.containsIdempotencyHeader
import com.example.idempotentapi.util.enableIdempotency
import com.example.idempotentapi.util.usePayloadValidation
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
            validatePayload(request, handler)
            if (setIdempotencyResponse(request, response)) return false
            validateConflict(request)
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
            saveIdempotencyResponse(request, response)
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        if (isIdempotencyRequest(request, handler as HandlerMethod) && ex != null) {
            deleteIdempotencyRequestStatus(request)
        }
    }

    private fun isIdempotencyRequest(request: HttpServletRequest, handler: HandlerMethod): Boolean {
        return handler.enableIdempotency() && request.containsIdempotencyHeader()
    }

    private fun setIdempotencyResponse(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val key = (request as CustomContentCachedRequestWrapper).idempotencyKey
        val result = idempotencyService.getIdempotencyResponse(key) ?: return false
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(result)
        return true
    }

    private fun saveIdempotencyResponse(request: HttpServletRequest, response: HttpServletResponse) {
        val key = (request as CustomContentCachedRequestWrapper).idempotencyKey
        val responseBody = String((response as ContentCachingResponseWrapper).contentAsByteArray)
        idempotencyService.setIdempotencyResponse(key, responseBody)
    }

    private fun deleteIdempotencyRequestStatus(request: HttpServletRequest) {
        val key = (request as CustomContentCachedRequestWrapper).idempotencyKey
        idempotencyService.evictIdempotencyRequest(key)
    }

    private fun validatePayload(request: HttpServletRequest, handler: HandlerMethod) {
        if (handler.usePayloadValidation().not()) return
        val key = (request as CustomContentCachedRequestWrapper).idempotencyKey
        key.payload = request.payload
        idempotencyService.validatePayload(key)
    }

    private fun validateConflict(request: HttpServletRequest) {
        val key = (request as CustomContentCachedRequestWrapper).idempotencyKey
        idempotencyService.validateConflict(key)
    }
}