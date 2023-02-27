package com.example.idempotentapi.configuration

import com.example.idempotentapi.util.IdempotencyKey
import com.example.idempotentapi.util.getIdempotencyKey
import org.springframework.stereotype.Component
import org.springframework.web.util.ContentCachingResponseWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponse

@Component
class ContentCacheFilter : Filter {
    override fun doFilter(request: ServletRequest?, response: ServletResponse?, chain: FilterChain?) {
        val contentCachingRequestWrapper = CustomContentCachedRequestWrapper(request as HttpServletRequest)
        val contentCachingResponseWrapper = ContentCachingResponseWrapper(response as HttpServletResponse)
        chain?.doFilter(contentCachingRequestWrapper, contentCachingResponseWrapper)
        contentCachingResponseWrapper.characterEncoding = Charsets.UTF_8.name()
        contentCachingResponseWrapper.copyBodyToResponse()
    }
}

class CustomContentCachedRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

    val payload: String = request.inputStream.bufferedReader().readText()
    val idempotencyKey: IdempotencyKey by lazy { getIdempotencyKey() }

    override fun getInputStream(): ServletInputStream {
        val byteArrayInputStream = ByteArrayInputStream(payload.toByteArray())
        return object : ServletInputStream() {
            override fun isFinished(): Boolean {
                return false
            }

            override fun isReady(): Boolean {
                return false
            }

            override fun setReadListener(readListener: ReadListener) {}

            override fun read(): Int {
                return byteArrayInputStream.read()
            }
        }
    }

    override fun getReader(): BufferedReader {
        return BufferedReader(InputStreamReader(this.inputStream))
    }
}