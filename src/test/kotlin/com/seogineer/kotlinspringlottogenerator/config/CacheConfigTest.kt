package com.seogineer.kotlinspringlottogenerator.config

import ch.qos.logback.classic.Level
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호10
import com.seogineer.kotlinspringlottogenerator.service.DrawingService
import com.seogineer.kotlinspringlottogenerator.support.LogCaptor
import io.lettuce.core.ClientOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.cache.RedisCache
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.web.client.RestTemplate
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * M1: CacheConfig의 Lettuce 클라이언트 설정(타임아웃, 끊김 시 명령 거부)과 지연 연결을 검증한다.
 *
 * - T1: 설정값은 LettuceClientConfiguration/팩토리/캐시 매니저에서 직접 단언한다 (네트워크 없음).
 * - T2: 팩토리 초기화(afterPropertiesSet)는 연결하지 않는다. 닫힌 로컬 포트를 가리켜도 예외가 없다.
 * - T3: 로컬 임의 포트만 쓴다 (외부 호스트 접속 없음).
 *   accept만 하고 응답하지 않는 ServerSocket을 대상으로 캐시 연산이 약 1초 타임아웃으로 실패하는지 본다.
 *   TCP 연결은 즉시 성립하고 Lettuce 핸드셰이크(HELLO) 응답 대기에서 멈추는데, 이 대기는 commandTimeout(1초)이 끊는다
 *   (임시 측정: command 3s/connect 1s -> 약 3.3s, command 1s/connect 3s -> 약 1.1s). connectTimeout은 T1 설정값으로만 검증한다.
 *   하한(500ms)은 타임아웃 경로를 탔다는 증거이고(타임아웃은 일찍 터지지 않음), 상한(5초)은 Lettuce 기본값(연결 10초, 명령 60초)과 구분된다.
 */
class CacheConfigTest {

    // ---------- T1: 설정값 ----------

    private fun assertClientSettings(configuration: LettuceClientConfiguration) {
        assertEquals(Duration.ofSeconds(1), configuration.commandTimeout)
        val clientOptions = configuration.clientOptions.orElseThrow()
        assertEquals(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS, clientOptions.disconnectedBehavior)
        assertEquals(Duration.ofSeconds(1), clientOptions.socketOptions.connectTimeout)
    }

    @Test
    fun Lettuce_클라이언트는_명령_타임아웃_1초_연결_타임아웃_1초_끊김_시_명령_거부로_설정된다() {
        assertClientSettings(CacheConfig.lettuceClientConfiguration())
        assertEquals(Duration.ofSeconds(1), CacheConfig.COMMAND_TIMEOUT)
        assertEquals(Duration.ofSeconds(1), CacheConfig.CONNECT_TIMEOUT)
    }

    @Test
    fun 연결_팩토리는_redis_6379를_가리키고_같은_클라이언트_설정을_쓴다() {
        // afterPropertiesSet을 호출하지 않으므로 redis 호스트 이름 해석/연결이 일어나지 않는다
        val factory = CacheConfig().redisConnectionFactory() as LettuceConnectionFactory

        assertEquals("redis", factory.hostName)
        assertEquals(6379, factory.port)
        assertClientSettings(factory.clientConfiguration)
    }

    @Test
    fun 캐시_매니저는_TTL_7일이고_null_값을_캐싱하지_않는다() {
        val factory = localFactory(closedLocalPort())
        try {
            val cacheManager = CacheConfig().cacheManager(factory).apply { afterPropertiesSet() }

            val cache = cacheManager.getCache("drawings") as RedisCache
            assertEquals(Duration.ofDays(7), cache.cacheConfiguration.ttl)
            assertFalse(cache.cacheConfiguration.allowCacheNullValues)
        } finally {
            factory.destroy()
        }
    }

    // ---------- T2: 지연 연결 ----------

    @Test
    fun 팩토리_초기화는_연결하지_않아_Redis가_없어도_예외가_없다() {
        // 닫힌 포트: 초기화 시 연결을 시도했다면 RedisConnectionFailureException이 났을 것이다
        val factory = LettuceConnectionFactory(
            RedisStandaloneConfiguration(LOOPBACK.hostAddress, closedLocalPort()),
            CacheConfig.lettuceClientConfiguration(),
        )
        try {
            assertDoesNotThrow { factory.afterPropertiesSet() }
            assertFalse(factory.eagerInitialization)
        } finally {
            factory.destroy()
        }
    }

    @Test
    fun CacheConfig가_만든_팩토리는_즉시_초기화를_켜지_않는다() {
        val factory = CacheConfig().redisConnectionFactory() as LettuceConnectionFactory

        assertFalse(factory.eagerInitialization)
    }

    // prod 컨텍스트 기동(CacheConfig + CacheErrorConfig, Redis 없음)은
    // CacheErrorConfigTest.prod_실제_캐시_구성에서_캐시_인터셉터가_LoggingCacheErrorHandler를_사용한다 가 검증한다.

    // ---------- T3: 타임아웃 동작 (로컬 소켓) ----------

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun 응답_없는_서버에서_캐시_조회는_약_1초_타임아웃으로_RedisConnectionFailureException이_난다() {
        SilentServer().use { server ->
            val factory = localFactory(server.port)
            try {
                val cache = CacheConfig().cacheManager(factory).apply { afterPropertiesSet() }.getCache("drawings")!!

                val started = System.nanoTime()
                assertThrows<RedisConnectionFailureException> { cache.get("0:5") }
                val elapsed = elapsedMillis(started)

                assertThat(elapsed).`as`("타임아웃 경로(약 1초)를 거쳐야 함: ${elapsed}ms").isGreaterThanOrEqualTo(500)
                assertThat(elapsed).`as`("기본값(10초/60초)보다 훨씬 빨라야 함: ${elapsed}ms").isLessThan(5_000)
                assertThat(server.acceptedCount()).isGreaterThanOrEqualTo(1)
            } finally {
                factory.destroy()
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun 응답_없는_서버에서_캐시_전체_삭제도_약_1초_안에_RedisConnectionFailureException이_난다() {
        SilentServer().use { server ->
            val factory = localFactory(server.port)
            try {
                val cache = CacheConfig().cacheManager(factory).apply { afterPropertiesSet() }.getCache("drawings")!!

                val started = System.nanoTime()
                assertThrows<RedisConnectionFailureException> { cache.clear() }
                val elapsed = elapsedMillis(started)

                assertThat(elapsed).`as`("${elapsed}ms").isGreaterThanOrEqualTo(500).isLessThan(5_000)
            } finally {
                factory.destroy()
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun 닫힌_포트에서는_캐시_조회가_즉시_RedisConnectionFailureException으로_실패한다() {
        val factory = localFactory(closedLocalPort())
        try {
            val cache = CacheConfig().cacheManager(factory).apply { afterPropertiesSet() }.getCache("drawings")!!

            val started = System.nanoTime()
            assertThrows<RedisConnectionFailureException> { cache.get("0:5") }
            val elapsed = elapsedMillis(started)

            assertThat(elapsed).`as`("${elapsed}ms").isLessThan(3_000)
        } finally {
            factory.destroy()
        }
    }

    @Configuration
    @EnableCaching
    class SilentRedisCachingConfig {
        @Bean(destroyMethod = "destroy")
        fun redisConnectionFactory(): LettuceConnectionFactory = localFactory(SILENT_PORT.get())

        @Bean
        fun cacheManager(factory: LettuceConnectionFactory): CacheManager = CacheConfig().cacheManager(factory)

        @Bean
        fun drawingRepository(): DrawingRepository = mock(DrawingRepository::class.java)

        @Bean
        fun drawingService(drawingRepository: DrawingRepository) =
            DrawingService(drawingRepository, mock(RestTemplate::class.java), mock(ApplicationEventPublisher::class.java))
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun prod_오류_처리기를_거치면_응답_없는_Redis에서도_조회가_수_초_안에_원본_결과를_돌려준다() {
        SilentServer().use { server ->
            SILENT_PORT.set(server.port)
            ApplicationContextRunner()
                .withUserConfiguration(SilentRedisCachingConfig::class.java, CacheErrorConfig::class.java)
                .withPropertyValues("spring.profiles.active=prod")
                .run { context ->
                    assertThat(context).hasNotFailed()
                    val page: Page<Drawing> = PageImpl(listOf(당첨번호10), PageRequest.of(0, 5), 10)
                    `when`(context.getBean(DrawingRepository::class.java).getDrawings(PageRequest.of(0, 5))).thenReturn(page)
                    val handler = context.getBean(LoggingCacheErrorHandler::class.java)

                    val started = System.nanoTime()
                    val result = LogCaptor.withLevel(LoggingCacheErrorHandler::class.java, Level.OFF) {
                        context.getBean(DrawingService::class.java).getDrawings(0, 5)
                    }
                    val elapsed = elapsedMillis(started)

                    assertThat(result).isSameAs(page)
                    // get 실패 + put 실패
                    assertEquals(2L, handler.failureCount())
                    // 연산 2회 x 약 1초. 기본값이었다면 최소 수십 초가 걸린다
                    assertThat(elapsed).`as`("${elapsed}ms").isLessThan(8_000)
                }
        }
    }

    /** accept만 하고 아무 응답도 보내지 않는 로컬 TCP 서버. 받은 소켓은 닫지 않고 들고 있는다. */
    private class SilentServer : AutoCloseable {
        private val serverSocket = ServerSocket(0, 50, LOOPBACK)
        private val accepted = CopyOnWriteArrayList<Socket>()
        val port: Int = serverSocket.localPort

        init {
            Thread({
                while (!serverSocket.isClosed) {
                    try {
                        accepted += serverSocket.accept()
                    } catch (e: Exception) {
                        break
                    }
                }
            }, "silent-redis-$port").apply { isDaemon = true }.start()
        }

        fun acceptedCount(): Int = accepted.size

        override fun close() {
            serverSocket.close()
            accepted.forEach { runCatching { it.close() } }
        }
    }

    companion object {
        private val LOOPBACK: InetAddress = InetAddress.getLoopbackAddress()
        private val SILENT_PORT = java.util.concurrent.atomic.AtomicInteger()

        private fun localFactory(port: Int): LettuceConnectionFactory =
            LettuceConnectionFactory(
                RedisStandaloneConfiguration(LOOPBACK.hostAddress, port),
                CacheConfig.lettuceClientConfiguration(),
            ).apply { afterPropertiesSet() }

        /** 잠시 열었다 닫은 로컬 포트 (연결 거부). */
        private fun closedLocalPort(): Int = ServerSocket(0, 1, LOOPBACK).use { it.localPort }

        private fun elapsedMillis(startedNanos: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
    }
}
