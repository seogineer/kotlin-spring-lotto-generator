package com.seogineer.kotlinspringlottogenerator.config

import org.springframework.cache.annotation.CachingConfigurerSupport
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * prod(@EnableCaching이 있는 CacheConfig와 같은 프로필)에서만 캐시 오류 처리기를 등록한다.
 * CacheConfig의 cacheManager 빈 메서드와 이름이 겹치지 않도록 별도 클래스로 둔다.
 * cacheManager()/keyGenerator() 등은 CachingConfigurerSupport 기본값(null)이라 기존 빈과 기본 설정을 그대로 쓴다.
 * 처리기는 빈으로도 등록해 DrawingCacheWarmer가 실패 횟수를 읽을 수 있게 한다 (@Configuration 프록시라 같은 인스턴스).
 */
@Configuration
@Profile("prod")
class CacheErrorConfig : CachingConfigurerSupport() {

    @Bean
    fun loggingCacheErrorHandler(): LoggingCacheErrorHandler = LoggingCacheErrorHandler()

    override fun errorHandler(): CacheErrorHandler = loggingCacheErrorHandler()
}
