package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.web.client.RestTemplate

/**
 * C3: evictAllCaches()는 DB 트랜잭션을 시작하지 않는다 (@Transactional(NOT_SUPPORTED)).
 * DB 장애를 흉내 내는 트랜잭션 매니저(doBegin이 항상 실패)를 두고, 캐시 + 트랜잭션 프록시를 함께 적용한 DrawingService로 확인한다.
 * 클래스 기본값(REQUIRED, readOnly)이 적용됐다면 evict도 트랜잭션 시작에서 실패한다.
 */
@SpringJUnitConfig(DrawingServiceTransactionTest.TestConfig::class)
class DrawingServiceTransactionTest {

    /** 실제 트랜잭션 시작(doBegin)마다 실패하고 시도 횟수를 센다. */
    class FailingTransactionManager : AbstractPlatformTransactionManager() {
        var beginAttempts = 0

        override fun doGetTransaction(): Any = Any()

        override fun doBegin(transaction: Any, definition: TransactionDefinition) {
            beginAttempts++
            throw CannotCreateTransactionException("DB 연결 실패 (테스트)")
        }

        override fun doCommit(status: DefaultTransactionStatus) {}

        override fun doRollback(status: DefaultTransactionStatus) {}
    }

    @TestConfiguration
    @EnableCaching
    @EnableTransactionManagement
    class TestConfig {
        @Bean
        fun cacheManager(): CacheManager =
            ConcurrentMapCacheManager("drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition")

        @Bean
        fun transactionManager(): PlatformTransactionManager = FailingTransactionManager()

        @Bean
        fun drawingRepository(): DrawingRepository = mock(DrawingRepository::class.java)

        @Bean
        fun drawingService(drawingRepository: DrawingRepository) =
            DrawingService(drawingRepository, mock(RestTemplate::class.java), mock(ApplicationEventPublisher::class.java))
    }

    @Autowired
    private lateinit var drawingService: DrawingService

    @Autowired
    private lateinit var drawingRepository: DrawingRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val cacheNames = listOf("drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition")

    private val failingTransactionManager get() = transactionManager as FailingTransactionManager

    @BeforeEach
    fun setUp() {
        reset(drawingRepository)
        failingTransactionManager.beginAttempts = 0
        cacheNames.forEach { name ->
            cacheManager.getCache(name)!!.clear()
            cacheManager.getCache(name)!!.put("sentinel", listOf(FrequencyResponse(1, 1, 1)))
        }
    }

    @Test
    fun evictAllCaches는_DB_트랜잭션_시작_없이_4개_캐시를_비운다() {
        assertDoesNotThrow { drawingService.evictAllCaches() }

        cacheNames.forEach { assertNull(cacheManager.getCache(it)!!.get("sentinel"), "$it 캐시가 비워지지 않음") }
        assertEquals(0, failingTransactionManager.beginAttempts)
        verifyNoInteractions(drawingRepository)
    }

    @Test
    fun 대조군_조회_메서드는_같은_트랜잭션_매니저에서_트랜잭션_시작에_실패한다() {
        // 프록시와 실패하는 트랜잭션 매니저가 실제로 적용됐음을 보여 준다
        assertThrows<CannotCreateTransactionException> { drawingService.getDrawings(0, 5) }

        assertEquals(1, failingTransactionManager.beginAttempts)
        verifyNoInteractions(drawingRepository)
    }

    @Test
    fun evictAllCaches의_전파_속성은_NOT_SUPPORTED다() {
        val method = DrawingService::class.java.getMethod("evictAllCaches")
        val transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional::class.java)!!

        assertEquals(Propagation.NOT_SUPPORTED, transactional.propagation)
    }
}
