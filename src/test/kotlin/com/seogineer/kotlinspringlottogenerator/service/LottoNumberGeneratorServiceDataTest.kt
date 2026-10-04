package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.config.QuerydslConfig
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호1
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호10
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호2
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호3
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호4
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호5
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호6
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호7
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호8
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호9
import com.seogineer.kotlinspringlottogenerator.dto.LottoNumberResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.client.RestTemplate

/**
 * 삭제된 DrawingRepositoryTest.생성된_추천_번호의_숫자_범위_유효성_검사의 대체 테스트.
 * 실제 QueryDSL 통계(getTopNumbersPerPosition) -> DrawingService -> LottoNumberGeneratorService 경로를
 * DrawingFixtures(당첨번호1~10) 데이터로 검증한다. 외부 API(RestTemplate)는 호출되지 않는다.
 */
@Import(QuerydslConfig::class)
@DataJpaTest
@DirtiesContext
class LottoNumberGeneratorServiceDataTest(
    @Autowired private val drawingRepository: DrawingRepository,
) {
    private lateinit var drawingService: DrawingService
    private lateinit var lottoNumberGeneratorService: LottoNumberGeneratorService

    @BeforeEach
    fun setUp() {
        drawingRepository.saveAll(
            listOf(당첨번호1, 당첨번호2, 당첨번호3, 당첨번호4, 당첨번호5, 당첨번호6, 당첨번호7, 당첨번호8, 당첨번호9, 당첨번호10)
        )
        drawingService = DrawingService(drawingRepository, mock(RestTemplate::class.java), mock(ApplicationEventPublisher::class.java))
        lottoNumberGeneratorService = LottoNumberGeneratorService(drawingService, java.util.Random(42), 1.0)
    }

    @Test
    fun 생성된_추천_번호의_숫자_범위_유효성_검사() {
        // 가중 무작위 추천은 자리별 상위 5개가 아니라 관측된 모든 번호에서 뽑는다
        val candidatesByPosition = drawingRepository.getFrequenciesPerPosition()
            .groupBy({ it.position }, { it.number })

        repeat(50) {
            val response = lottoNumberGeneratorService.generateLottoNumbers()
            val numbers = listOf(response.one, response.two, response.three, response.four, response.five, response.six)

            assertNotEquals(LottoNumberResponse(0, 0, 0, 0, 0, 0), response)
            assertEquals(6, numbers.toSet().size, "번호 중복: $numbers")
            numbers.forEachIndexed { index, number ->
                assertThat(number).isBetween(1, 45)
                assertThat(candidatesByPosition.getValue(index + 1)).contains(number)
            }
        }
    }

    @Test
    fun 데이터가_없으면_추천_번호는_0으로_채운_결과() {
        drawingRepository.deleteAll()

        assertEquals(LottoNumberResponse(0, 0, 0, 0, 0, 0), lottoNumberGeneratorService.generateLottoNumbers())
    }
}
