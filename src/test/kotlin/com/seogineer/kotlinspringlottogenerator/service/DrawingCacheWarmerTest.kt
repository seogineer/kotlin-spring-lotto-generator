package com.seogineer.kotlinspringlottogenerator.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.slf4j.LoggerFactory
import com.seogineer.kotlinspringlottogenerator.config.LoggingCacheErrorHandler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.assertj.core.api.Assertions.assertThat

/**
 * DrawingCacheWarmer 단위 테스트 (Mockito).
 * 캐시 자체는 검증하지 않고, DrawingService(프록시) 호출 순서와 트랜잭션 경계, 예외 비전파만 검증한다.
 */
@ExtendWith(MockitoExtension::class)
class DrawingCacheWarmerTest {
    @Mock
    private lateinit var drawingService: DrawingService

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var drawingCacheWarmer: DrawingCacheWarmer

    private val status: TransactionStatus = SimpleTransactionStatus()

    @BeforeEach
    fun setUp() {
        // 생성자에서 TransactionTemplate을 만들므로 @InjectMocks 대신 직접 생성한다
        drawingCacheWarmer = DrawingCacheWarmer(drawingService, transactionManager)
    }

    private fun givenTransactionStarts() {
        `when`(transactionManager.getTransaction(any())).thenReturn(status)
    }

    private fun verifyWarmUpInOrder() {
        val inOrder = inOrder(drawingService, transactionManager)
        inOrder.verify(drawingService).evictAllCaches()
        inOrder.verify(transactionManager).getTransaction(any())
        inOrder.verify(drawingService).getDrawings(0, 5)
        inOrder.verify(drawingService).getDrawings(0, 1)
        inOrder.verify(drawingService).getMostFrequentNumbers()
        inOrder.verify(drawingService).getTopNumbersPerPosition()
        inOrder.verify(drawingService).getFrequenciesPerPosition()
        inOrder.verify(transactionManager).commit(status)
        verify(transactionManager, never()).rollback(any())
        verifyNoMoreInteractions(drawingService)
    }

    @Test
    fun 워밍업_대상_페이지는_첫_화면의_0_5와_0_1() {
        assertEquals(listOf(0 to 5, 0 to 1), DrawingCacheWarmer.WARM_UP_PAGES)
    }

    @Test
    fun 워밍업은_evict_후_새_읽기_트랜잭션에서_4개_캐시를_순서대로_조회한다() {
        givenTransactionStarts()

        assertDoesNotThrow { drawingCacheWarmer.warmUp("test") }

        verifyWarmUpInOrder()
    }

    @Test
    fun 워밍업_트랜잭션은_REQUIRES_NEW_읽기_전용이다() {
        givenTransactionStarts()

        drawingCacheWarmer.warmUp("test")

        val captor = ArgumentCaptor.forClass(TransactionDefinition::class.java)
        verify(transactionManager).getTransaction(captor.capture())
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, captor.value.propagationBehavior)
        assertEquals(true, captor.value.isReadOnly)
    }

    @Test
    fun 애플리케이션_시작_이벤트에서_워밍업한다() {
        givenTransactionStarts()

        assertDoesNotThrow { drawingCacheWarmer.onApplicationReady() }

        verifyWarmUpInOrder()
    }

    @Test
    fun 엑셀_업로드_완료_이벤트에서_워밍업한다() {
        givenTransactionStarts()

        assertDoesNotThrow {
            drawingCacheWarmer.onDrawingsChanged(DrawingsChangedEvent(DrawingsChangedEvent.Source.EXCEL_UPLOAD))
        }

        verifyWarmUpInOrder()
    }

    @Test
    fun 스케줄러_완료_이벤트에서_워밍업한다() {
        givenTransactionStarts()

        assertDoesNotThrow {
            drawingCacheWarmer.onDrawingsChanged(DrawingsChangedEvent(DrawingsChangedEvent.Source.SCHEDULER))
        }

        verifyWarmUpInOrder()
    }

    @Test
    fun 조회_중_예외가_발생해도_예외를_던지지_않고_롤백한다() {
        givenTransactionStarts()
        `when`(drawingService.getMostFrequentNumbers()).thenThrow(RuntimeException("DB 오류"))

        assertDoesNotThrow { drawingCacheWarmer.warmUp("test") }
        assertDoesNotThrow { drawingCacheWarmer.onApplicationReady() }
        assertDoesNotThrow {
            drawingCacheWarmer.onDrawingsChanged(DrawingsChangedEvent(DrawingsChangedEvent.Source.EXCEL_UPLOAD))
        }

        verify(transactionManager, times(3)).rollback(status)
        verify(transactionManager, never()).commit(any())
        // 실패 지점 이후 항목은 워밍업되지 않는다 (현재 동작)
        verify(drawingService, never()).getTopNumbersPerPosition()
        verify(drawingService, never()).getFrequenciesPerPosition()
    }

    @Test
    fun 트랜잭션_생성에_실패해도_예외를_던지지_않는다() {
        `when`(transactionManager.getTransaction(any())).thenThrow(CannotCreateTransactionException("커넥션 없음"))

        assertDoesNotThrow { drawingCacheWarmer.warmUp("test") }
        assertDoesNotThrow { drawingCacheWarmer.onApplicationReady() }
        assertDoesNotThrow {
            drawingCacheWarmer.onDrawingsChanged(DrawingsChangedEvent(DrawingsChangedEvent.Source.SCHEDULER))
        }

        verify(drawingService, times(3)).evictAllCaches()
        verify(drawingService, never()).getDrawings(anyInt(), anyInt())
        verify(drawingService, never()).getMostFrequentNumbers()
        verify(drawingService, never()).getTopNumbersPerPosition()
        verify(drawingService, never()).getFrequenciesPerPosition()
    }

    @Test
    fun evict에_실패해도_예외를_던지지_않는다() {
        doThrow(IllegalStateException("Redis 연결 실패")).`when`(drawingService).evictAllCaches()

        assertDoesNotThrow { drawingCacheWarmer.warmUp("test") }
        assertDoesNotThrow { drawingCacheWarmer.onApplicationReady() }

        verifyNoInteractions(transactionManager)
        verify(drawingService, never()).getDrawings(anyInt(), anyInt())
    }

    /** DrawingCacheWarmer 로거에 ListAppender를 붙여 block 실행 중 로그를 수집한다. */
    private fun captureWarmerLogs(block: () -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(DrawingCacheWarmer::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.toList()
    }

    @Test
    fun 워밍업_실패_로그는_warn_레벨이고_예외_스택을_포함한다() {
        givenTransactionStarts()
        `when`(drawingService.getFrequenciesPerPosition()).thenThrow(IllegalStateException("자리별 빈도 조회 실패"))

        val events = captureWarmerLogs { drawingCacheWarmer.warmUp("test-trigger") }

        val warn = events.single { it.level == Level.WARN }
        assertEquals("캐시 워밍업 실패 (trigger=test-trigger)", warn.formattedMessage)
        // 예외가 포맷 인자가 아니라 throwable로 전달되어 스택이 남는다
        assertEquals(IllegalStateException::class.java.name, warn.throwableProxy!!.className)
        assertEquals("자리별 빈도 조회 실패", warn.throwableProxy!!.message)
        verify(transactionManager).rollback(status)
    }

    @Test
    fun 워밍업_성공_로그는_info_레벨이고_경고가_없다() {
        givenTransactionStarts()

        val events = captureWarmerLogs { drawingCacheWarmer.warmUp("ok") }

        assertEquals(listOf("캐시 워밍업 완료 (trigger=ok)"), events.filter { it.level == Level.INFO }.map { it.formattedMessage })
        assertEquals(0, events.count { it.level == Level.WARN })
    }

    // ---------- T7: 워밍업 중 캐시 서버 오류 요약 (M2) ----------

    private val handler = LoggingCacheErrorHandler()

    private fun providerOf(vararg handlers: LoggingCacheErrorHandler): ObjectProvider<LoggingCacheErrorHandler> {
        val beanFactory = StaticListableBeanFactory()
        handlers.forEachIndexed { index, h -> beanFactory.addBean("loggingCacheErrorHandler$index", h) }
        return beanFactory.getBeanProvider(LoggingCacheErrorHandler::class.java)
    }

    private fun warmerWith(provider: ObjectProvider<LoggingCacheErrorHandler>?) =
        DrawingCacheWarmer(drawingService, transactionManager, provider)

    /** 실제 처리기를 거친 것처럼 evict 중 clear 오류 [count]건을 처리기에 기록한다 (처리기가 삼키므로 예외는 없음). */
    private fun givenEvictCacheErrors(count: Int) {
        doAnswer {
            repeat(count) { handler.handleCacheClearError(IllegalStateException("Redis 연결 실패"), ConcurrentMapCache("drawings")) }
            null
        }.`when`(drawingService).evictAllCaches()
    }

    @Test
    fun 워밍업_중_캐시_서버_오류가_있으면_완료_대신_오류_건수를_ERROR로_남긴다() {
        givenTransactionStarts()
        givenEvictCacheErrors(2)

        val events = captureWarmerLogs { warmerWith(providerOf(handler)).warmUp("t") }

        val error = events.single { it.level == Level.ERROR }
        assertThat(error.formattedMessage).contains("캐시 서버 오류 2건", "trigger=t")
        assertEquals(0, events.count { it.level == Level.INFO }, "완료 로그가 남으면 안 됨")
        assertEquals(0, events.count { it.level == Level.WARN })
        // 캐시 오류가 있어도 DB 조회(워밍업 본체)는 끝까지 진행한다
        verifyWarmUpInOrder()
    }

    @Test
    fun 조회_단계의_캐시_오류도_건수에_포함된다() {
        givenTransactionStarts()
        `when`(drawingService.getMostFrequentNumbers()).thenAnswer {
            handler.handleCacheGetError(IllegalStateException("timeout"), ConcurrentMapCache("mostFrequentNumbers"), "SimpleKey []")
            handler.handleCachePutError(IllegalStateException("timeout"), ConcurrentMapCache("mostFrequentNumbers"), "SimpleKey []", null)
            emptyList<Any>()
        }

        val events = captureWarmerLogs { warmerWith(providerOf(handler)).warmUp("t") }

        assertThat(events.single { it.level == Level.ERROR }.formattedMessage).contains("캐시 서버 오류 2건")
    }

    @Test
    fun 처리기가_있어도_워밍업_중_오류가_없으면_INFO_완료만_남긴다() {
        givenTransactionStarts()

        val events = captureWarmerLogs { warmerWith(providerOf(handler)).warmUp("t2") }

        assertEquals(listOf("캐시 워밍업 완료 (trigger=t2)"), events.filter { it.level == Level.INFO }.map { it.formattedMessage })
        assertEquals(0, events.count { it.level == Level.ERROR || it.level == Level.WARN })
    }

    @Test
    fun 이전에_누적된_오류는_세지_않고_워밍업_전후_차이만_본다() {
        givenTransactionStarts()
        // 워밍업 전에 다른 요청에서 생긴 오류 3건
        repeat(3) { handler.handleCacheGetError(IllegalStateException("old"), ConcurrentMapCache("drawings"), "k") }

        val events = captureWarmerLogs { warmerWith(providerOf(handler)).warmUp("t3") }

        assertEquals(listOf("캐시 워밍업 완료 (trigger=t3)"), events.filter { it.level == Level.INFO }.map { it.formattedMessage })
        assertEquals(0, events.count { it.level == Level.ERROR })
        assertEquals(3L, handler.failureCount())
    }

    @Test
    fun 처리기_빈이_없으면_항상_INFO_완료를_남긴다() {
        givenTransactionStarts()

        val events = captureWarmerLogs { warmerWith(providerOf()).warmUp("dev") }

        assertEquals(listOf("캐시 워밍업 완료 (trigger=dev)"), events.filter { it.level == Level.INFO }.map { it.formattedMessage })
        assertEquals(0, events.count { it.level == Level.ERROR })
        verifyWarmUpInOrder()
    }

    @Test
    fun provider가_null이면_항상_INFO_완료를_남긴다() {
        givenTransactionStarts()

        val events = captureWarmerLogs { warmerWith(null).warmUp("null") }

        assertEquals(listOf("캐시 워밍업 완료 (trigger=null)"), events.filter { it.level == Level.INFO }.map { it.formattedMessage })
        assertEquals(0, events.count { it.level == Level.ERROR })
    }

    @Test
    fun 처리기가_있어도_조회_예외가_나면_기존처럼_WARN_워밍업_실패를_남긴다() {
        givenTransactionStarts()
        givenEvictCacheErrors(1)
        `when`(drawingService.getMostFrequentNumbers()).thenThrow(IllegalStateException("DB 오류"))

        val events = captureWarmerLogs { warmerWith(providerOf(handler)).warmUp("fail") }

        val warn = events.single { it.level == Level.WARN }
        assertThat(warn.formattedMessage).contains("워밍업 실패", "trigger=fail")
        assertEquals("DB 오류", warn.throwableProxy!!.message)
        assertEquals(0, events.count { it.level == Level.ERROR || it.level == Level.INFO })
        verify(transactionManager).rollback(status)
    }
}
