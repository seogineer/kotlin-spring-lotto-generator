package com.seogineer.kotlinspringlottogenerator.Service

import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import com.seogineer.kotlinspringlottogenerator.Entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호1
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호2
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호3
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
        val drawings = listOf(당첨번호1, 당첨번호2, 당첨번호3)
        val drawingPage: Page<Drawing> = PageImpl(drawings, pageable, drawings.size.toLong())

        `when`(drawingRepository.getDrawings(pageable)).thenReturn(drawingPage)

        val result = drawingService.getDrawings(page, size)

        assertNotNull(result)
        assertEquals(3, result.content.size)
        assertEquals(3, result.totalElements)
        assertEquals(0, result.pageable.pageNumber)
        assertEquals(5, result.pageable.pageSize)
        assertEquals(1, result.content[0].round)
        assertEquals(LocalDate.of(2024, 9, 1), result.content[0].date)
        assertEquals(1, result.content[0].one)
        assertEquals(2, result.content[0].two)
        assertEquals(3, result.content[0].three)
        assertEquals(4, result.content[0].four)
        assertEquals(5, result.content[0].five)
        assertEquals(6, result.content[0].six)
        assertEquals(7, result.content[0].bonus)
        assertEquals(BigInteger("2000000000"), result.content[0].firstWinPrize)
        assertEquals(1, result.content[0].firstWinners)
        verify(drawingRepository, times(1)).getDrawings(pageable)
    }

    @Test
    fun 가장_최근_회차_조회() {
        `when`(drawingRepository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(당첨번호3))

        val latestRound = drawingRepository.findTopByOrderByRoundDesc().get().round
        assertEquals(3, latestRound)
    }
}
