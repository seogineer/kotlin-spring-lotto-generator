package com.seogineer.kotlinspringlottogenerator.entity

import com.seogineer.kotlinspringlottogenerator.config.QuerydslConfig
import com.seogineer.kotlinspringlottogenerator.dto.LottoNumberResponse
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.annotation.DirtiesContext
import kotlin.test.assertEquals


@Import(QuerydslConfig::class)
@DataJpaTest
@DirtiesContext
class DrawingRepositoryTest(
    @Autowired private val drawingRepository: DrawingRepository,
) {

    @BeforeEach
    fun setUp() {
        drawingRepository.save(당첨번호1)
        drawingRepository.save(당첨번호2)
        drawingRepository.save(당첨번호3)
        drawingRepository.save(당첨번호4)
        drawingRepository.save(당첨번호5)
        drawingRepository.save(당첨번호6)
        drawingRepository.save(당첨번호7)
        drawingRepository.save(당첨번호8)
        drawingRepository.save(당첨번호9)
        drawingRepository.save(당첨번호10)
    }

    @Test
    fun 역대_당첨_번호_조회() {
        val pageable: Pageable = PageRequest.of(0, 5)

        val drawings: Page<Drawing> = drawingRepository.getDrawings(pageable)

        assertEquals(5, drawings.content.size)
        assertEquals(10, drawings.totalElements)
        assertEquals(0, drawings.pageable.pageNumber)
        assertEquals(5, drawings.pageable.pageSize)
    }

    @Test
    fun 생성된_추천_번호의_숫자_범위_유효성_검사() {
        val lottoNumber: LottoNumberResponse = drawingRepository.generateLottoNumbers()

        val isValid = lottoNumber.one in 1..45 &&
                lottoNumber.two in 1..45 &&
                lottoNumber.three in 1..45 &&
                lottoNumber.four in 1..45 &&
                lottoNumber.five in 1..45 &&
                lottoNumber.six in 1..45

        assertTrue(isValid)
    }

    @Test
    fun 가장_최근_회차_조회() {
        val latestRound = drawingRepository.findTopByOrderByRoundDesc().get().round
        assertEquals(10, latestRound)
    }

    @Test
    fun 가장_많이_뽑힌_번호_조회() {
        val mostFrequentNumbers = drawingRepository.getMostFrequentNumbers()

        assertEquals(6, mostFrequentNumbers.size)

        val isValid = mostFrequentNumbers[0].number in 1..45 &&
                mostFrequentNumbers[1].number in 1..45 &&
                mostFrequentNumbers[2].number in 1..45 &&
                mostFrequentNumbers[3].number in 1..45 &&
                mostFrequentNumbers[4].number in 1..45 &&
                mostFrequentNumbers[5].number in 1..45
        assertTrue(isValid)

        assertEquals(40, mostFrequentNumbers[0].number)
        assertEquals(5, mostFrequentNumbers[0].frequency)
        assertEquals(16, mostFrequentNumbers[1].number)
        assertEquals(4, mostFrequentNumbers[1].frequency)
        assertEquals(25, mostFrequentNumbers[2].number)
        assertEquals(4, mostFrequentNumbers[2].frequency)
        assertEquals(42, mostFrequentNumbers[3].number)
        assertEquals(4, mostFrequentNumbers[3].frequency)
        assertEquals(9, mostFrequentNumbers[4].number)
        assertEquals(3, mostFrequentNumbers[4].frequency)
        assertEquals(27, mostFrequentNumbers[5].number)
        assertEquals(3, mostFrequentNumbers[5].frequency)
    }

    @Test
    fun 각_자리별_가장_많이_뽑힌_번호_상위_5개_조회() {
        val response = drawingRepository.getTopNumbersPerPosition()

        assertEquals(true, response.isNotEmpty())
        assertEquals(30, response.size)

        val groupedByPosition = response.groupBy { it.position }
        groupedByPosition.forEach { (position, frequencies) ->
            assertEquals(5, frequencies.size)
            val sortedFrequencies = frequencies.sortedByDescending { it.frequency }
            assertEquals(sortedFrequencies, frequencies)
        }

        val sortedPositions = response.sortedBy { it.position }
        assertEquals(sortedPositions, response)
    }
}
