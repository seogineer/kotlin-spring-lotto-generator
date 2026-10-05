package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.config.LoggingCacheErrorHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate

/**
 * 첫 요청이 캐시 미스로 느려지지 않도록 자주 조회되는 캐시를 미리 채운다.
 *
 * - 같은 클래스 내부 호출은 캐시 프록시를 우회하므로, 별도 빈에서 DrawingService(프록시)를 호출한다.
 * - 워밍업 실패는 앱 시작이나 데이터 갱신을 막지 않는다 (예외를 잡아 경고 로그만 남김).
 * - 캐시가 활성화되지 않은 프로필(@EnableCaching은 prod CacheConfig에만 있음)에서는 evict가 no-op이고 조회만 수행된다.
 */
@Component
class DrawingCacheWarmer(
    private val drawingService: DrawingService,
    transactionManager: PlatformTransactionManager,
    // prod에서만 존재한다 (CacheErrorConfig). 워밍업 중 캐시 오류가 있었는지 판단하는 데만 쓴다
    private val cacheErrorHandler: ObjectProvider<LoggingCacheErrorHandler>? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 이전 트랜잭션 자원이 아직 바인딩된 afterCompletion 시점에서도 독립된 새 읽기 트랜잭션으로 조회한다
    private val readTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        warmUp("application-ready")
    }

    /**
     * 엑셀 업로드: 업로드 트랜잭션 완료 후(AFTER_COMPLETION, 커밋/롤백 모두) 실행된다.
     * 스케줄러: 실제 트랜잭션 없이(NOT_SUPPORTED) 모든 묶음 저장이 끝난 뒤 finally에서 발행하므로
     * fallbackExecution = true에 따라 발행 시점에 즉시 실행된다.
     * 두 경우 모두 해당 메서드의 @CacheEvict(beforeInvocation = true)가 이미 실행된 뒤다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    fun onDrawingsChanged(event: DrawingsChangedEvent) {
        warmUp(event.source.name)
    }

    fun warmUp(trigger: String) {
        val errorHandler = cacheErrorHandler?.ifAvailable
        val failuresBefore = errorHandler?.failureCount() ?: 0L
        try {
            // evict와 트랜잭션 완료 사이에 동시 요청이 이전 데이터로 채운 캐시를 제거한 뒤 다시 채운다
            drawingService.evictAllCaches()
            readTransaction.executeWithoutResult {
                WARM_UP_PAGES.forEach { (page, size) -> drawingService.getDrawings(page, size) }
                drawingService.getMostFrequentNumbers()
                drawingService.getTopNumbersPerPosition()
                drawingService.getFrequenciesPerPosition()
            }
            // 캐시 오류는 처리기가 삼키므로 예외가 없어도 실제로는 비우기/채우기가 실패했을 수 있다.
            // 같은 시각 다른 요청의 캐시 오류도 함께 집계될 수 있다 (장애 중이라는 신호로는 충분하다).
            val cacheErrors = (errorHandler?.failureCount() ?: 0L) - failuresBefore
            if (cacheErrors > 0) {
                log.error("캐시 워밍업 중 캐시 서버 오류 {}건 (trigger={}). DB 조회는 끝났지만 캐시 비우기/채우기가 실패했을 수 있음. " +
                    "LoggingCacheErrorHandler의 ERROR/WARN 로그 확인", cacheErrors, trigger)
            } else {
                log.info("캐시 워밍업 완료 (trigger={})", trigger)
            }
        } catch (e: Exception) {
            log.warn("캐시 워밍업 실패 (trigger={})", trigger, e)
        }
    }

    companion object {
        /**
         * 프런트엔드(react-lotto-generator) 첫 화면이 호출하는 drawings 페이지 (page, size).
         * - WinningNumbers: page=0, size=5 (ITEMS_PER_PAGE)
         * - LatestDraw: page=0, size=1
         */
        val WARM_UP_PAGES = listOf(0 to 5, 0 to 1)
    }
}
