package com.example.idempotentapi.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfiguration(
    @Value("\${spring.redis.host}") private val redisHost: String,
    @Value("\${spring.redis.port}") private val redisPort: String,
) {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val serverConfig = RedisStandaloneConfiguration(redisHost, redisPort.toInt())
        return LettuceConnectionFactory(serverConfig)
    }

    @Bean
    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            this.setConnectionFactory(redisConnectionFactory)
            this.keySerializer = StringRedisSerializer()
            this.valueSerializer = JdkSerializationRedisSerializer()
            this.hashKeySerializer = StringRedisSerializer()
            this.hashValueSerializer = JdkSerializationRedisSerializer()
        }
    }
}