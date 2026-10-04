package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.cache.interceptor.SimpleKey
import org.springframework.context.annotation.Bean
import org.springframework.data.domain.Page
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.client.RestTemplate

/**
 * 캐시 워밍업 통합 테스트.
 * 테스트 전용 @EnableCaching + ConcurrentMapCacheManager로 캐시를 켜고, 실제 프록시 경유 업로드 후
 * 트랜잭션 완료(AFTER_COMPLETION) 리스너가 캐시를 비우고 커밋된 데이터로 다시 채우는지 확인한다.
 * 워머는 예외를 삼키므로 "예외 없음"이 아니라 캐시 내용으로 검증한다.
 * 테스트 메서드에 @Transactional을 붙이지 않는다 (업로드 트랜잭션이 테스트 트랜잭션에 합류하면 리스너가 늦게 실행됨).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class DrawingCacheWarmerIntegrationTest {

    @TestConfiguration
    @EnableCaching
    class CacheTestConfig {
        // Redis가 클래스패스에 있으므로 자동 구성에 맡기지 않고 명시한다
        @Bean
        fun cacheManager(): CacheManager =
            ConcurrentMapCacheManager("drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition")
    }

    // 실제 동행복권 API를 호출하지 않도록 대체 (스케줄러 경로는 여기서 실행하지 않음)
    @MockBean
    private lateinit var restTemplate: RestTemplate

    @Autowired
    private lateinit var drawingService: DrawingService

    @Autowired
    private lateinit var drawingRepository: DrawingRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    private fun cache(name: String) = cacheManager.getCache(name)!!

    @Suppress("UNCHECKED_CAST")
    private fun cachedPage(key: String): Page<Drawing>? = cache("drawings").get(key)?.get() as Page<Drawing>?

    @Suppress("UNCHECKED_CAST")
    private fun cachedStats(name: String): List<FrequencyResponse>? =
        cache(name).get(SimpleKey.EMPTY)?.get() as List<FrequencyResponse>?

    private val cacheNames = listOf("drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition")

    /** 4개 캐시 모두에 이전 데이터 표식을 넣는다 (워밍업 키와 겹치지 않는 키). */
    private fun seedSentinel() {
        cacheNames.forEach { name ->
            cache(name).put("9:9", "이전 데이터")
            assertNotNull(cache(name).get("9:9"))
        }
    }

    private fun assertSentinelsEvicted() {
        cacheNames.forEach { name -> assertNull(cache(name).get("9:9"), "$name 캐시가 비워지지 않음") }
    }

    private fun assertWarmedWith(expectedTotal: Long) {
        assertEquals(expectedTotal, cachedPage("0:5")!!.totalElements)
        assertEquals(expectedTotal, cachedPage("0:1")!!.totalElements)
        assertNotNull(cachedStats("mostFrequentNumbers"))
        assertNotNull(cachedStats("topNumbersPerPosition"))
        assertNotNull(cachedStats("frequenciesPerPosition"))
    }

    private fun excelFile() = MockMultipartFile(
        "file", "excel.xlsx", "application/octet-stream",
        ClassLoader.getSystemResource("excel.xlsx").readBytes()
    )

    @Test
    fun 업로드_성공_후_캐시를_비우고_커밋된_데이터로_다시_채운다() {
        seedSentinel()

        drawingService.readExcelFile(excelFile())

        val total = drawingRepository.count()
        assertThat(total).isGreaterThan(0)
        assertSentinelsEvicted()
        assertWarmedWith(total)
        assertEquals(5, cachedPage("0:5")!!.content.size)
        assertEquals(1, cachedPage("0:1")!!.content.size)
        assertThat(cachedStats("mostFrequentNumbers")).isNotEmpty
        assertEquals(30, cachedStats("topNumbersPerPosition")!!.size)
        // 자리별 빈도 캐시는 커밋된 전체 데이터 기준 (자리마다 빈도 합 = 행 수)
        val frequencies = cachedStats("frequenciesPerPosition")!!
        assertEquals((1..6).toList(), frequencies.map { it.position }.distinct())
        frequencies.groupBy { it.position }.values.forEach { assertEquals(total, it.sumOf { f -> f.frequency }) }
    }

    @Test
    fun 업로드_실패로_롤백돼도_캐시를_비우고_다시_채운다() {
        val totalBefore = drawingRepository.count()
        seedSentinel()
        val file = MockMultipartFile("file", "invalid.txt", "text/plain", "invalid".toByteArray())

        assertThrows<IllegalArgumentException> { drawingService.readExcelFile(file) }

        assertSentinelsEvicted()
        assertWarmedWith(totalBefore)
    }

    @Test
    fun 추천은_워밍업된_자리별_빈도_캐시를_사용한다() {
        drawingService.readExcelFile(excelFile())
        val warmed = cachedStats("frequenciesPerPosition")!!

        // 캐시된 목록과 동일 인스턴스를 돌려준다 (repository 재조회 없음)
        assertThat(drawingService.getFrequenciesPerPosition()).isSameAs(warmed)
    }

    @Test
    fun 워밍업된_캐시는_사이즈별로_분리되어_조회에_사용된다() {
        drawingService.readExcelFile(excelFile())

        val page5 = drawingService.getDrawings(0, 5)
        val page1 = drawingService.getDrawings(0, 1)

        assertEquals(5, page5.content.size)
        assertEquals(1, page1.content.size)
        assertEquals(page5.content[0].round, page1.content[0].round)
    }
}
