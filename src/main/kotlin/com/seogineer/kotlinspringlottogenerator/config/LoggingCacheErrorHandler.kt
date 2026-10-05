package com.seogineer.kotlinspringlottogenerator.config

import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.interceptor.CacheErrorHandler
import java.util.concurrent.atomic.AtomicLong

/**
 * 캐시 계층(Redis) 오류를 로그로 남기고 삼킨다.
 * 조회는 캐시 없이 원본(DB)으로 처리되고, put/evict/clear 실패는 요청을 실패시키지 않는다.
 *
 * - get/put 실패: WARN. 다음 요청에서 다시 시도되므로 데이터 정합성 문제는 없다.
 * - evict/clear 실패: ERROR. 이전 캐시 데이터가 TTL(7일)까지 남을 수 있어 조치가 필요하다.
 * - 로그 폭주를 막기 위해 WARN/ERROR에는 예외 클래스와 메시지 한 줄만 남기고, 전체 스택은 DEBUG로 남긴다.
 * - 실패 누적 횟수([failureCount])를 제공해 DrawingCacheWarmer가 워밍업 중 캐시 오류 여부를 판단할 수 있다.
 *
 * 프로필과 무관한 일반 클래스로 두어 단위 테스트할 수 있게 했다. 등록은 [CacheErrorConfig](prod 전용)에서 한다.
 */
class LoggingCacheErrorHandler : CacheErrorHandler {

    private val failures = AtomicLong()

    /** 생성 이후 처리한 캐시 오류(get/put/evict/clear) 누적 횟수. */
    fun failureCount(): Long = failures.get()

    override fun handleCacheGetError(exception: RuntimeException, cache: Cache, key: Any) {
        failures.incrementAndGet()
        log.warn("캐시 조회 실패, 원본으로 조회합니다 (cache={}, key={}, error={})", cache.name, key, summary(exception))
        logStack(exception)
    }

    override fun handleCachePutError(exception: RuntimeException, cache: Cache, key: Any, value: Any?) {
        failures.incrementAndGet()
        log.warn("캐시 저장 실패 (cache={}, key={}, error={})", cache.name, key, summary(exception))
        logStack(exception)
    }

    override fun handleCacheEvictError(exception: RuntimeException, cache: Cache, key: Any) {
        failures.incrementAndGet()
        log.error("캐시 항목 삭제 실패 (cache={}, key={}, error={}). {}", cache.name, key, summary(exception), STALE_DATA_ACTION)
        logStack(exception)
    }

    override fun handleCacheClearError(exception: RuntimeException, cache: Cache) {
        failures.incrementAndGet()
        log.error("캐시 전체 삭제 실패 (cache={}, error={}). {}", cache.name, summary(exception), STALE_DATA_ACTION)
        logStack(exception)
    }

    private fun summary(exception: RuntimeException): String =
        "${exception.javaClass.name}: ${exception.message?.replace(Regex("[\\r\\n]+"), " ")}"

    private fun logStack(exception: RuntimeException) {
        if (log.isDebugEnabled) {
            log.debug("캐시 오류 상세 스택", exception)
        }
    }

    companion object {
        const val STALE_DATA_ACTION = "이전 캐시 데이터가 남을 수 있음. 앱 재시작 또는 캐시 키 삭제 필요"
        private val log = LoggerFactory.getLogger(LoggingCacheErrorHandler::class.java)
    }
}
