package com.seogineer.kotlinspringlottogenerator.config

import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호10
import com.seogineer.kotlinspringlottogenerator.service.DrawingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheInterceptor
import org.springframework.cache.interceptor.SimpleCacheErrorHandler
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.web.client.RestTemplate

/**
 * A3: CacheErrorConfig는 prod 프로필에서만 등록된다.
 * ApplicationContextRunner로 프로필(spring.profiles.active)만 바꿔 같은 구성을 띄운다 (DB/Redis 연결 없음).
 * 대조: 같은 고장 난 캐시 구성에서 prod는 원본 결과를 돌려주고, 기본 프로필은 캐시 예외가 그대로 전파된다.
 */
class CacheErrorConfigTest {

    @Configuration
    @EnableCaching
    class BrokenCachingConfig {
        @Bean
        fun cacheManager(): CacheManager =
            SimpleCacheManager().apply { setCaches(BrokenCache.CACHE_NAMES.map { BrokenCache(it) }) }

        @Bean
        fun drawingRepository(): DrawingRepository = mock(DrawingRepository::class.java)

        @Bean
        fun drawingService(drawingRepository: DrawingRepository) =
            DrawingService(drawingRepository, mock(RestTemplate::class.java), mock(ApplicationEventPublisher::class.java))
    }

    private val configOnly = ApplicationContextRunner().withUserConfiguration(CacheErrorConfig::class.java)

    private val brokenCaching = ApplicationContextRunner()
        .withUserConfiguration(BrokenCachingConfig::class.java, CacheErrorConfig::class.java)

    @Test
    fun 기본_프로필에는_CacheErrorConfig가_등록되지_않는다() {
        configOnly.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(CacheErrorConfig::class.java)
            assertThat(context).doesNotHaveBean(CachingConfigurer::class.java)
        }
    }

    @Test
    fun dev_프로필에는_CacheErrorConfig가_등록되지_않는다() {
        configOnly.withPropertyValues("spring.profiles.active=dev").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(CacheErrorConfig::class.java)
        }
    }

    @Test
    fun prod_프로필에서만_CacheErrorConfig가_등록되고_LoggingCacheErrorHandler를_제공한다() {
        configOnly.withPropertyValues("spring.profiles.active=prod").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(CacheErrorConfig::class.java)
            val configurer = context.getBean(CachingConfigurer::class.java)
            assertThat(configurer.errorHandler()).isInstanceOf(LoggingCacheErrorHandler::class.java)
            // 캐시 매니저 등 나머지는 기본값(null) -> 기존 cacheManager 빈을 그대로 쓴다
            assertThat(configurer.cacheManager()).isNull()
        }
    }

    @Test
    fun CacheErrorConfig와_CacheConfig는_같은_prod_프로필에_묶여_있다() {
        val errorConfigProfile = AnnotationUtils.findAnnotation(CacheErrorConfig::class.java, Profile::class.java)!!.value
        val cacheConfigProfile = AnnotationUtils.findAnnotation(CacheConfig::class.java, Profile::class.java)!!.value

        assertThat(errorConfigProfile).containsExactly("prod")
        assertThat(cacheConfigProfile).containsExactly("prod")
    }

    @Test
    fun prod_실제_캐시_구성에서_캐시_인터셉터가_LoggingCacheErrorHandler를_사용한다() {
        // CacheConfig(Redis)와 함께 띄운다. Lettuce/RedisCacheManager는 기동 시 연결하지 않으므로 Redis 없이도 뜬다.
        ApplicationContextRunner()
            .withUserConfiguration(CacheConfig::class.java, CacheErrorConfig::class.java)
            .withPropertyValues("spring.profiles.active=prod")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(CacheManager::class.java)).isInstanceOf(RedisCacheManager::class.java)
                assertThat(context.getBean(CacheInterceptor::class.java).errorHandler)
                    .isInstanceOf(LoggingCacheErrorHandler::class.java)
            }
    }

    private fun stubPage(context: org.springframework.context.ApplicationContext): Page<Drawing> {
        val page: Page<Drawing> = PageImpl(listOf(당첨번호10), PageRequest.of(0, 5), 10)
        `when`(context.getBean(DrawingRepository::class.java).getDrawings(PageRequest.of(0, 5))).thenReturn(page)
        return page
    }

    @Test
    fun prod에서는_캐시가_고장_나도_조회가_원본_결과를_돌려준다() {
        brokenCaching.withPropertyValues("spring.profiles.active=prod").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(CacheInterceptor::class.java).errorHandler)
                .isInstanceOf(LoggingCacheErrorHandler::class.java)
            val page = stubPage(context)

            assertThat(context.getBean(DrawingService::class.java).getDrawings(0, 5)).isSameAs(page)
        }
    }

    @Test
    fun 대조군_기본_프로필에서는_캐시_예외가_그대로_전파된다() {
        brokenCaching.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(CacheErrorConfig::class.java)
            assertThat(context.getBean(CacheInterceptor::class.java).errorHandler)
                .isInstanceOf(SimpleCacheErrorHandler::class.java)
            stubPage(context)

            val error = assertThrows<IllegalStateException> { context.getBean(DrawingService::class.java).getDrawings(0, 5) }
            assertThat(error.message).contains("Redis 연결 실패")
        }
    }

    // ---------- T8: 처리기 빈 동일성 (M2) ----------

    @Test
    fun prod에서_LoggingCacheErrorHandler_빈과_errorHandler와_캐시_인터셉터의_처리기는_같은_인스턴스다() {
        ApplicationContextRunner()
            .withUserConfiguration(CacheConfig::class.java, CacheErrorConfig::class.java)
            .withPropertyValues("spring.profiles.active=prod")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(LoggingCacheErrorHandler::class.java)
                val bean = context.getBean(LoggingCacheErrorHandler::class.java)

                assertThat(context.getBean(CachingConfigurer::class.java).errorHandler()).isSameAs(bean)
                assertThat(context.getBean(CacheErrorConfig::class.java).loggingCacheErrorHandler()).isSameAs(bean)
                assertThat(context.getBean(CacheInterceptor::class.java).errorHandler).isSameAs(bean)
            }
    }

    @Test
    fun 기본_프로필에는_LoggingCacheErrorHandler_빈이_없다() {
        configOnly.run { context ->
            assertThat(context).doesNotHaveBean(LoggingCacheErrorHandler::class.java)
        }
    }

    @Test
    fun 캐시_오류는_빈으로_등록된_처리기의_카운터에_집계된다() {
        brokenCaching.withPropertyValues("spring.profiles.active=prod").run { context ->
            assertThat(context).hasNotFailed()
            val bean = context.getBean(LoggingCacheErrorHandler::class.java)
            stubPage(context)

            context.getBean(DrawingService::class.java).getDrawings(0, 5)

            // 캐시 인터셉터가 쓰는 처리기와 빈이 같아야 워머가 실제 오류 횟수를 읽는다 (get + put)
            assertThat(bean.failureCount()).isEqualTo(2L)
        }
    }
}
