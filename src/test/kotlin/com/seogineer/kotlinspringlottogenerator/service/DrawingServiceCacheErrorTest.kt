package com.seogineer.kotlinspringlottogenerator.service

import ch.qos.logback.classic.Level
import com.seogineer.kotlinspringlottogenerator.config.BrokenCache
import com.seogineer.kotlinspringlottogenerator.config.CacheErrorConfig
import com.seogineer.kotlinspringlottogenerator.config.LoggingCacheErrorHandler
import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호10
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호9
import com.seogineer.kotlinspringlottogenerator.support.LogCaptor
import com.seogineer.kotlinspringlottogenerator.support.messages
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.web.client.RestTemplate

/**
 * A2: Redis 장애(모든 캐시 연산이 예외) 상황에서 prod의 CacheErrorConfig가 적용된 DrawingService는
 * 조회 시 원본(repository) 결과를 돌려주고, evict 실패에도 업로드/스케줄러/워밍업 evict가 진행된다.
 * 운영 구성과 같게 실제 CacheErrorConfig를 prod 프로필로 등록한다. Repository/RestTemplate은 mock (외부 호출 없음).
 */
@SpringJUnitConfig(classes = [DrawingServiceCacheErrorTest.TestConfig::class, CacheErrorConfig::class])
@ActiveProfiles("prod")
class DrawingServiceCacheErrorTest {

    @Configuration
    @EnableCaching
    class TestConfig {
        @Bean
        fun cacheManager(): CacheManager =
            SimpleCacheManager().apply { setCaches(BrokenCache.CACHE_NAMES.map { BrokenCache(it) }) }

        @Bean
        fun drawingRepository(): DrawingRepository = mock(DrawingRepository::class.java)

        @Bean
        fun restTemplate(): RestTemplate = mock(RestTemplate::class.java)

        // ApplicationEventPublisher 타입 주입은 컨텍스트 자신으로 해석되므로 mock은 빈이 아니라 직접 넘긴다
        @Bean
        fun drawingService(drawingRepository: DrawingRepository, restTemplate: RestTemplate) =
            DrawingService(drawingRepository, restTemplate, EVENT_PUBLISHER)

        companion object {
            val EVENT_PUBLISHER: ApplicationEventPublisher = mock(ApplicationEventPublisher::class.java)
        }
    }

    @Autowired
    private lateinit var drawingService: DrawingService

    @Autowired
    private lateinit var drawingRepository: DrawingRepository

    @Autowired
    private lateinit var restTemplate: RestTemplate

    private val eventPublisher = TestConfig.EVENT_PUBLISHER

    @Autowired
    private lateinit var cacheManager: CacheManager

    private lateinit var page: Page<Drawing>

    @BeforeEach
    fun setUp() {
        reset(drawingRepository, restTemplate, eventPublisher)
        page = PageImpl(listOf(당첨번호10, 당첨번호9), PageRequest.of(0, 5), 10)
        `when`(drawingRepository.getDrawings(PageRequest.of(0, 5))).thenReturn(page)
    }

    private fun handlerWarnings(block: () -> Unit): List<String> =
        // get/put 실패는 WARN, evict/clear 실패는 ERROR (08 M2)
        LogCaptor.capture(LoggingCacheErrorHandler::class.java) { assertDoesNotThrow(block) }
            .filter { it.level == Level.WARN || it.level == Level.ERROR }.map { it.formattedMessage }

    private fun assertClearFailedForAllCaches(warnings: List<String>) {
        BrokenCache.CACHE_NAMES.forEach { name ->
            assertThat(warnings.filter { it.contains("전체 삭제 실패") && it.contains("cache=$name") })
                .withFailMessage("$name 캐시 clear 실패 WARN 없음: $warnings").hasSize(1)
        }
    }

    @Test
    fun 캐시_조회와_저장이_실패해도_repository_결과를_돌려준다() {
        lateinit var first: Page<Drawing>
        lateinit var second: Page<Drawing>

        val warnings = handlerWarnings {
            first = drawingService.getDrawings(0, 5)
            second = drawingService.getDrawings(0, 5)
        }

        assertSame(page, first)
        assertSame(page, second)
        // 캐시가 동작하지 않으므로 매번 원본을 조회한다
        verify(drawingRepository, times(2)).getDrawings(PageRequest.of(0, 5))
        assertThat(warnings.filter { it.contains("조회 실패") && it.contains("cache=drawings") && it.contains("key=0:5") }).hasSize(2)
        assertThat(warnings.filter { it.contains("저장 실패") && it.contains("cache=drawings") && it.contains("key=0:5") }).hasSize(2)
    }

    @Test
    fun 통계_조회도_캐시_오류와_무관하게_repository_결과를_돌려준다() {
        val most = listOf(FrequencyResponse(40, 0, 5))
        val top = listOf(FrequencyResponse(2, 1, 3))
        val all = listOf(FrequencyResponse(2, 1, 3), FrequencyResponse(5, 1, 1))
        `when`(drawingRepository.getMostFrequentNumbers()).thenReturn(most)
        `when`(drawingRepository.getTopNumbersPerPosition()).thenReturn(top)
        `when`(drawingRepository.getFrequenciesPerPosition()).thenReturn(all)

        val warnings = handlerWarnings {
            assertSame(most, drawingService.getMostFrequentNumbers())
            assertSame(top, drawingService.getTopNumbersPerPosition())
            assertSame(all, drawingService.getFrequenciesPerPosition())
        }

        listOf("mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition").forEach { name ->
            assertThat(warnings).anyMatch { it.contains("조회 실패") && it.contains("cache=$name") }
            assertThat(warnings).anyMatch { it.contains("저장 실패") && it.contains("cache=$name") }
        }
    }

    @Test
    fun evictAllCaches는_캐시_삭제가_실패해도_예외를_던지지_않는다() {
        val warnings = handlerWarnings { drawingService.evictAllCaches() }

        assertClearFailedForAllCaches(warnings)
        verifyNoInteractions(drawingRepository)
    }

    @Test
    fun 엑셀_업로드는_beforeInvocation_evict가_실패해도_저장까지_진행된다() {
        val file = MockMultipartFile(
            "file", "excel.xlsx", "application/octet-stream",
            ClassLoader.getSystemResource("excel.xlsx").readBytes()
        )

        val warnings = handlerWarnings { drawingService.readExcelFile(file) }

        assertClearFailedForAllCaches(warnings)
        verify(drawingRepository, times(1)).saveAll(anyIterable<Drawing>())
        verify(eventPublisher, times(1)).publishEvent(DrawingsChangedEvent(DrawingsChangedEvent.Source.EXCEL_UPLOAD))
    }

    @Test
    fun 스케줄러는_beforeInvocation_evict가_실패해도_API를_호출하고_이벤트를_발행한다() {
        `when`(drawingRepository.findTopByOrderByRoundDesc()).thenReturn(java.util.Optional.of(당첨번호10))
        `when`(restTemplate.getForObject(anyString(), eq(String::class.java)))
            .thenReturn("""{"resultCode":null,"resultMessage":null,"data":{"list":[]}}""")

        val warnings = handlerWarnings { drawingService.fetchAndStoreLottoNumbers() }

        assertClearFailedForAllCaches(warnings)
        verify(restTemplate, times(1)).getForObject(contains("srchLtEpsd=11"), eq(String::class.java))
        verify(eventPublisher, times(1)).publishEvent(DrawingsChangedEvent(DrawingsChangedEvent.Source.SCHEDULER))
    }

    @Test
    fun 구성된_캐시는_모두_고장_난_캐시다() {
        // 테스트 전제 확인: 캐시가 정상 동작해 우연히 통과하는 것이 아니다
        BrokenCache.CACHE_NAMES.forEach { assertThat(cacheManager.getCache(it)).isInstanceOf(BrokenCache::class.java) }
    }
}
