package com.seogineer.kotlinspringlottogenerator.Service

import com.seogineer.kotlinspringlottogenerator.Dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import com.seogineer.kotlinspringlottogenerator.Entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호1
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호10
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호2
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호3
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호4
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호5
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호6
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호7
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호8
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호9
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.math.BigInteger
import java.time.LocalDate
import java.util.*

@ExtendWith(MockitoExtension::class)
class DrawingServiceTest {
    @Mock
    private lateinit var drawingRepository: DrawingRepository

    @InjectMocks
    private lateinit var drawingService: DrawingService

    @Test
    fun 역대_당첨_번호_조회() {
        val page = 0
        val size = 5
        val pageable: Pageable = PageRequest.of(page, size)
        val drawings = listOf(당첨번호1, 당첨번호2, 당첨번호3, 당첨번호4, 당첨번호5, 당첨번호6, 당첨번호7, 당첨번호8, 당첨번호9, 당첨번호10)
        val drawingPage: Page<Drawing> = PageImpl(drawings, pageable, drawings.size.toLong())

        `when`(drawingRepository.getDrawings(pageable)).thenReturn(drawingPage)

        val result = drawingService.getDrawings(page, size)

        assertNotNull(result)
        assertEquals(10, result.content.size)
        assertEquals(10, result.totalElements)
        assertEquals(0, result.pageable.pageNumber)
        assertEquals(5, result.pageable.pageSize)
        assertEquals(1, result.content[0].round)
        assertEquals(LocalDate.of(2002, 12, 7), result.content[0].date)
        assertEquals(10, result.content[0].one)
        assertEquals(23, result.content[0].two)
        assertEquals(29, result.content[0].three)
        assertEquals(33, result.content[0].four)
        assertEquals(37, result.content[0].five)
        assertEquals(40, result.content[0].six)
        assertEquals(16, result.content[0].bonus)
        assertEquals(BigInteger("0"), result.content[0].firstWinPrize)
        assertEquals(0, result.content[0].firstWinners)
        verify(drawingRepository, times(1)).getDrawings(pageable)
    }

    @Test
    fun 가장_최근_회차_조회() {
        `when`(drawingRepository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(당첨번호3))

        val latestRound = drawingRepository.findTopByOrderByRoundDesc().get().round
        assertEquals(3, latestRound)
    }

    @Test
    fun 가장_많이_뽑힌_번호_조회() {
        val drawings = listOf(
            FrequencyResponse(1, 0, 30),
            FrequencyResponse(3, 0, 15),
            FrequencyResponse(2, 0, 10),
            FrequencyResponse(4, 0, 7),
            FrequencyResponse(5, 0, 7),
        )

        `when`(drawingRepository.getMostFrequentNumbers()).thenReturn(drawings)

        val result = drawingRepository.getMostFrequentNumbers()
        assertEquals(5, result.size)
        assertEquals(1, result[0].number)
        assertEquals(30, result[0].frequency)
        assertEquals(3, result[1].number)
        assertEquals(15, result[1].frequency)
        assertEquals(2, result[2].number)
        assertEquals(10, result[2].frequency)
        assertEquals(4, result[3].number)
        assertEquals(7, result[3].frequency)
        assertEquals(5, result[4].number)
        assertEquals(7, result[4].frequency)
    }
}
