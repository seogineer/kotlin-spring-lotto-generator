package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.config.QuerydslConfig
import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.dto.LottoNumberResponse
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.client.RestTemplate

/**
 * G8: excel.xlsx 전체 이력(실제 데이터)의 자리별 빈도로 추천했을 때의 분포.
 * 통계는 한 번만 집계해 mock DrawingService로 돌려준다 (캐시 적중 상태와 같음, 요청마다 쿼리하지 않음).
 * 시드를 고정해 결정적이며, 허용 오차(평균 ±1)는 개발자 실측 차이(0.2 이하)보다 넉넉하다.
 */
@Import(QuerydslConfig::class)
@DataJpaTest
@DirtiesContext
class LottoNumberGeneratorServiceDistributionTest(
    @Autowired private val drawingRepository: DrawingRepository,
) {
    private lateinit var frequencies: List<FrequencyResponse>
    private lateinit var topNumbers: List<FrequencyResponse>
    private lateinit var actualMeans: List<Double>

    @BeforeEach
    fun setUp() {
        val loader = DrawingService(drawingRepository, mock(RestTemplate::class.java), mock(ApplicationEventPublisher::class.java))
        loader.readExcelFile(
            MockMultipartFile("file", "excel.xlsx", "application/octet-stream", ClassLoader.getSystemResource("excel.xlsx").readBytes())
        )
        val drawings = drawingRepository.findAll()
        assertThat(drawings).hasSizeGreaterThan(1000)
        actualMeans = listOf(
            drawings.map { it.one }, drawings.map { it.two }, drawings.map { it.three },
            drawings.map { it.four }, drawings.map { it.five }, drawings.map { it.six },
        ).map { it.average() }
        frequencies = drawingRepository.getFrequenciesPerPosition()
        topNumbers = drawingRepository.getTopNumbersPerPosition()
    }

    /** 폴백(상한 1,000회 초과) 횟수는 random.nextIntCount로 센다. */
    private fun generate(exponent: Double, random: CountingRandom, n: Int): List<List<Int>> {
        val drawingService = mock(DrawingService::class.java)
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(frequencies)
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(topNumbers)
        val service = LottoNumberGeneratorService(drawingService, random, exponent)
        return (1..n).map { service.generateLottoNumbers().toList() }
    }

    private fun LottoNumberResponse.toList() = listOf(one, two, three, four, five, six)

    @Test
    fun 지수_0이면_자리별_평균이_실제_데이터와_1_이내다() {
        val random = CountingRandom(20261004)
        val results = generate(exponent = 0.0, random = random, n = 3_000)

        // 개발자 실측 폴백 비율 0.38% -> 넉넉히 2% 미만
        assertThat(random.nextIntCount).isLessThan(60)

        results.forEach { numbers ->
            assertThat(numbers).allMatch { it in 1..45 }
            assertThat(numbers.zipWithNext().all { (a, b) -> a < b }).isTrue
        }
        (0 until 6).forEach { index ->
            assertThat(results.map { it[index] }.average())
                .`as`("${index + 1}번 자리 평균")
                .isCloseTo(actualMeans[index], within(1.0))
        }
    }

    @Test
    fun 기본_지수_0_5로_실제_데이터에서_생성한_결과는_모두_유효한_오름차순이다() {
        val candidatesByPosition = frequencies.groupBy({ it.position }, { it.number }).mapValues { it.value.toSet() }

        val random = CountingRandom(7)
        val results = generate(exponent = 0.5, random = random, n = 2_000)

        assertThat(random.nextIntCount).isEqualTo(0) // 기본 지수에서는 폴백 없이 가중 경로로만 생성
        results.forEach { numbers ->
            assertThat(numbers.zipWithNext().all { (a, b) -> a < b }).withFailMessage("$numbers").isTrue
            numbers.forEachIndexed { index, number ->
                assertThat(number).isBetween(1, 45)
                assertThat(candidatesByPosition.getValue(index + 1)).contains(number)
            }
        }
    }
}
