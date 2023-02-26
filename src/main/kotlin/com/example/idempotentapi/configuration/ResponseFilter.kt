package com.example.idempotentapi.configuration

import org.springframework.stereotype.Component
import org.springframework.web.util.ContentCachingResponseWrapper
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

@Component
class ResponseFilter : Filter {
    override fun doFilter(request: ServletRequest?, response: ServletResponse?, chain: FilterChain?) {
        val contentCachingResponseWrapper = ContentCachingResponseWrapper(response as HttpServletResponse)
        chain?.doFilter(request, contentCachingResponseWrapper)
        contentCachingResponseWrapper.characterEncoding = Charsets.UTF_8.name()
        contentCachingResponseWrapper.copyBodyToResponse()
    }
}
