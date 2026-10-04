package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호1
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호10
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호6
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호7
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호8
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호9
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.web.client.RestTemplate

/**
 * DrawingService 캐시 애노테이션 검증.
 * 운영 캐시(CacheConfig)는 prod 전용이므로, 테스트 전용 @EnableCaching + ConcurrentMapCacheManager로
 * DrawingService 프록시만 띄워 키 분리와 evict 시점을 확인한다. Repository/RestTemplate은 mock(외부 호출 없음).
 */
@SpringJUnitConfig(DrawingServiceCacheTest.CacheTestConfig::class)
class DrawingServiceCacheTest {

    @TestConfiguration
    @EnableCaching
    class CacheTestConfig {
        @Bean
        fun cacheManager(): CacheManager =
            ConcurrentMapCacheManager("drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition")

        @Bean
        fun drawingRepository(): DrawingRepository = mock(DrawingRepository::class.java)

        @Bean
        fun restTemplate(): RestTemplate = mock(RestTemplate::class.java)

        @Bean
        fun eventPublisher(): ApplicationEventPublisher = mock(ApplicationEventPublisher::class.java)

        @Bean
        fun drawingService(
            drawingRepository: DrawingRepository,
            restTemplate: RestTemplate,
            eventPublisher: ApplicationEventPublisher,
        ) = DrawingService(drawingRepository, restTemplate, eventPublisher)
    }

    @Autowired
    private lateinit var drawingService: DrawingService

    @Autowired
    private lateinit var drawingRepository: DrawingRepository

    @Autowired
    private lateinit var restTemplate: RestTemplate

    @Autowired
    private lateinit var cacheManager: CacheManager

    private val cacheNames = listOf("drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition")

    @BeforeEach
    fun setUp() {
        cacheNames.forEach { cacheManager.getCache(it)!!.clear() }
        reset(drawingRepository, restTemplate)
        stubPage(0, 5, listOf(당첨번호10, 당첨번호9, 당첨번호8, 당첨번호7, 당첨번호6))
        stubPage(0, 1, listOf(당첨번호10))
    }

    private fun stubPage(page: Int, size: Int, content: List<Drawing>) {
        val pageable = PageRequest.of(page, size)
        `when`(drawingRepository.getDrawings(pageable)).thenReturn(PageImpl(content, pageable, 10))
    }

    private fun drawingsCache() = cacheManager.getCache("drawings")!!

    @Suppress("UNCHECKED_CAST")
    private fun cachedPage(key: String): Page<Drawing>? = drawingsCache().get(key)?.get() as Page<Drawing>?

    /** 4개 캐시 모두에 임의 항목을 넣어 둔다 (evict 여부 확인용). */
    private fun seedAllCaches() {
        drawingsCache().put("9:9", PageImpl(listOf(당첨번호1)))
        cacheManager.getCache("mostFrequentNumbers")!!.put("sentinel", listOf(FrequencyResponse(1, 1, 1)))
        cacheManager.getCache("topNumbersPerPosition")!!.put("sentinel", listOf(FrequencyResponse(1, 1, 1)))
        cacheManager.getCache("frequenciesPerPosition")!!.put("sentinel", listOf(FrequencyResponse(1, 1, 1)))
    }

    private fun assertAllCachesEvicted() {
        assertNull(drawingsCache().get("9:9"))
        assertNull(cacheManager.getCache("mostFrequentNumbers")!!.get("sentinel"))
        assertNull(cacheManager.getCache("topNumbersPerPosition")!!.get("sentinel"))
        assertNull(cacheManager.getCache("frequenciesPerPosition")!!.get("sentinel"))
    }

    @Test
    fun 같은_페이지라도_사이즈가_다르면_캐시_키가_분리된다() {
        val page5 = drawingService.getDrawings(0, 5)
        val page1 = drawingService.getDrawings(0, 1)

        assertEquals(5, page5.content.size)
        assertEquals(1, page1.content.size)

        // 키는 "page:size" 형식 (파라미터 이름 해석 실패 시 "null:null"로 합쳐지는 회귀 방지)
        assertEquals(5, cachedPage("0:5")!!.content.size)
        assertEquals(1, cachedPage("0:1")!!.content.size)
        assertNull(drawingsCache().get("0"))
        assertNull(drawingsCache().get("null:null"))

        verify(drawingRepository, times(1)).getDrawings(PageRequest.of(0, 5))
        verify(drawingRepository, times(1)).getDrawings(PageRequest.of(0, 1))
    }

    @Test
    fun 같은_페이지와_사이즈를_다시_조회하면_캐시가_적중한다() {
        val first5 = drawingService.getDrawings(0, 5)
        val first1 = drawingService.getDrawings(0, 1)
        val second5 = drawingService.getDrawings(0, 5)
        val second1 = drawingService.getDrawings(0, 1)

        assertSame(first5, second5)
        assertSame(first1, second1)
        assertEquals(5, second5.content.size)
        assertEquals(1, second1.content.size)
        verify(drawingRepository, times(1)).getDrawings(PageRequest.of(0, 5))
        verify(drawingRepository, times(1)).getDrawings(PageRequest.of(0, 1))
    }

    @Test
    fun 통계_조회는_캐시가_적중한다() {
        `when`(drawingRepository.getMostFrequentNumbers()).thenReturn(listOf(FrequencyResponse(40, 1, 5)))
        `when`(drawingRepository.getTopNumbersPerPosition()).thenReturn(listOf(FrequencyResponse(2, 1, 3)))
        `when`(drawingRepository.getFrequenciesPerPosition()).thenReturn(listOf(FrequencyResponse(2, 1, 3), FrequencyResponse(5, 1, 1)))

        repeat(3) {
            drawingService.getMostFrequentNumbers()
            drawingService.getTopNumbersPerPosition()
            drawingService.getFrequenciesPerPosition()
        }

        verify(drawingRepository, times(1)).getMostFrequentNumbers()
        verify(drawingRepository, times(1)).getTopNumbersPerPosition()
        verify(drawingRepository, times(1)).getFrequenciesPerPosition()
    }

    @Test
    fun evictAllCaches는_4개_캐시를_모두_비운다() {
        drawingService.getDrawings(0, 5)
        seedAllCaches()

        drawingService.evictAllCaches()

        assertAllCachesEvicted()
        assertNull(drawingsCache().get("0:5"))
    }

    @Test
    fun 엑셀_업로드가_실패해도_메서드_실행_전에_4개_캐시를_비운다() {
        seedAllCaches()
        val file = MockMultipartFile("file", "invalid.txt", "text/plain", "invalid".toByteArray())

        // beforeInvocation = false(기본값)였다면 예외 시 evict가 생략되어 항목이 남는다
        assertThrows<IllegalArgumentException> { drawingService.readExcelFile(file) }

        assertAllCachesEvicted()
    }

    @Test
    fun 엑셀_업로드가_성공하면_4개_캐시를_비운다() {
        seedAllCaches()
        val file = MockMultipartFile(
            "file", "excel.xlsx", "application/octet-stream",
            ClassLoader.getSystemResource("excel.xlsx").readBytes()
        )

        drawingService.readExcelFile(file)

        assertAllCachesEvicted()
    }

    @Test
    fun 스케줄러는_API_실패로_종료돼도_4개_캐시를_비운다() {
        seedAllCaches()
        `when`(restTemplate.getForObject(anyString(), eq(String::class.java))).thenThrow(RuntimeException("API 실패"))

        drawingService.fetchAndStoreLottoNumbers()

        assertAllCachesEvicted()
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String::class.java))
    }

    @Test
    fun 캐시_비운_뒤_다시_조회하면_repository를_다시_호출한다() {
        drawingService.getDrawings(0, 5)
        drawingService.evictAllCaches()

        val reloaded = drawingService.getDrawings(0, 5)

        assertNotNull(cachedPage("0:5"))
        assertEquals(5, reloaded.content.size)
        verify(drawingRepository, times(2)).getDrawings(PageRequest.of(0, 5))
    }

    // ----- C1: drawings 캐시 조건 (size <= 20 && page <= 300) -----

    @Test
    fun 사이즈가_20을_넘는_요청은_캐시하지_않고_매번_repository를_조회한다() {
        stubPage(0, 21, listOf(당첨번호10))

        drawingService.getDrawings(0, 21)
        drawingService.getDrawings(0, 21)

        assertNull(drawingsCache().get("0:21"))
        verify(drawingRepository, times(2)).getDrawings(PageRequest.of(0, 21))
    }

    @Test
    fun 페이지가_300을_넘는_요청은_캐시하지_않고_매번_repository를_조회한다() {
        stubPage(301, 5, listOf(당첨번호1))

        drawingService.getDrawings(301, 5)
        drawingService.getDrawings(301, 5)

        assertNull(drawingsCache().get("301:5"))
        verify(drawingRepository, times(2)).getDrawings(PageRequest.of(301, 5))
    }

    @Test
    fun 경계값_사이즈_20과_페이지_300은_캐시된다() {
        stubPage(0, 20, listOf(당첨번호10, 당첨번호9))
        stubPage(300, 5, listOf(당첨번호1))

        val first20 = drawingService.getDrawings(0, 20)
        val second20 = drawingService.getDrawings(0, 20)
        val first300 = drawingService.getDrawings(300, 5)
        val second300 = drawingService.getDrawings(300, 5)

        assertSame(first20, second20)
        assertSame(first300, second300)
        assertNotNull(cachedPage("0:20"))
        assertNotNull(cachedPage("300:5"))
        verify(drawingRepository, times(1)).getDrawings(PageRequest.of(0, 20))
        verify(drawingRepository, times(1)).getDrawings(PageRequest.of(300, 5))
    }

    @Test
    fun 캐시되지_않는_요청도_응답은_repository_결과와_같다() {
        stubPage(0, 21, listOf(당첨번호10, 당첨번호9))

        val page = drawingService.getDrawings(0, 21)

        // 스텁한 PageImpl 그대로 (내용 2건 < size 21 이므로 PageImpl이 total을 2로 보정한다)
        assertSame(drawingRepository.getDrawings(PageRequest.of(0, 21)), page)
        assertEquals(listOf(10, 9), page.content.map { it.round })
    }
}
