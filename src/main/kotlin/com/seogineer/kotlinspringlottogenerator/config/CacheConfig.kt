package com.seogineer.kotlinspringlottogenerator.config

import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.time.Duration

@Configuration
@EnableCaching
@Profile("prod")
class CacheConfig {

    /**
     * 직접 만든 빈이라 spring.redis.* 설정이 적용되지 않으므로 타임아웃을 명시한다.
     * Redis가 멈추거나 끊겨도 캐시 연산이 약 1초 안에 실패해 LoggingCacheErrorHandler로 넘어가게 한다.
     * - commandTimeout 1초 (Lettuce 기본 60초)
     * - 연결이 끊긴 동안 명령을 큐에 쌓지 않고 즉시 거부 (기본 DEFAULT는 재연결될 때까지 큐잉)
     * - 소켓 연결 타임아웃 1초 (기본 10초)
     * 연결은 첫 사용 시 맺는다(LettuceConnectionFactory 기본 지연 연결). Redis가 아직 안 떠 있어도 기동은 막히지 않는다.
     */
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        return LettuceConnectionFactory(RedisStandaloneConfiguration(REDIS_HOST, REDIS_PORT), lettuceClientConfiguration())
    }

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofDays(7))
            .disableCachingNullValues()
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build()
    }

    companion object {
        const val REDIS_HOST = "redis"
        const val REDIS_PORT = 6379
        val COMMAND_TIMEOUT: Duration = Duration.ofSeconds(1)
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(1)

        /** 테스트에서 설정값을 확인할 수 있도록 분리한다. */
        fun lettuceClientConfiguration(): LettuceClientConfiguration =
            LettuceClientConfiguration.builder()
                .commandTimeout(COMMAND_TIMEOUT)
                .clientOptions(
                    ClientOptions.builder()
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .socketOptions(SocketOptions.builder().connectTimeout(CONNECT_TIMEOUT).build())
                        .build()
                )
                .build()
    }
}
