package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.dto.LottoNumberResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

/**
 * getFrequenciesPerPosition()을 스텁하지 않으면 Mockito가 빈 리스트를 반환하므로,
 * 아래 테스트는 모두 폴백 경로(자리별 상위 5개 조합)를 검증한다.
 * 의도한 경로를 탔는지 @AfterEach에서 확인한다 (가중 샘플링 nextDouble 0회, 폴백 조회 발생).
 * 가중 거절 샘플링 경로는 LottoNumberGeneratorServiceWeightedTest에서 검증한다.
 */
@ExtendWith(MockitoExtension::class)
class LottoNumberGeneratorServiceTest {
    @Mock
    private lateinit var drawingService: DrawingService

    private lateinit var lottoNumberGeneratorService: LottoNumberGeneratorService

    private lateinit var random: CountingRandom

    @BeforeEach
    fun setUp() {
        random = CountingRandom(42)
        lottoNumberGeneratorService = LottoNumberGeneratorService(drawingService, random, 1.0)
    }

    @AfterEach
    fun 폴백_경로만_탔는지_확인() {
        assertEquals(0, random.nextDoubleCount, "가중 샘플링 경로를 타면 안 된다")
        verify(drawingService, atLeastOnce()).getTopNumbersPerPosition()
    }

    private val 빈_결과 = LottoNumberResponse(0, 0, 0, 0, 0, 0)

    private fun LottoNumberResponse.toList() = listOf(one, two, three, four, five, six)

    /** 자리(1~6) -> 후보 번호 목록(빈도 내림차순)을 FrequencyResponse 목록으로 만든다. */
    private fun topNumbersOf(candidates: Map<Int, List<Int>>): List<FrequencyResponse> =
        candidates.flatMap { (position, numbers) ->
            numbers.mapIndexed { index, number -> FrequencyResponse(number, position, (100 - index).toLong()) }
        }

    // 실제 응답처럼 자리 간 후보가 일부 겹친다 (도메인 유효값 1~45)
    private val 자리별_후보 = mapOf(
        1 to listOf(1, 2, 3, 4, 5),
        2 to listOf(5, 8, 10, 12, 13),
        3 to listOf(12, 16, 19, 21, 23),
        4 to listOf(21, 25, 27, 30, 33),
        5 to listOf(33, 35, 37, 38, 40),
        6 to listOf(38, 40, 42, 43, 45),
    )

    @Test
    fun 추천_번호는_각_자리_후보에서_하나씩_뽑은_서로_다른_1부터_45_사이_번호() {
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(topNumbersOf(자리별_후보))

        repeat(200) {
            val numbers = lottoNumberGeneratorService.generateLottoNumbers().toList()

            assertEquals(6, numbers.size)
            assertEquals(6, numbers.toSet().size, "번호 중복: $numbers")
            numbers.forEachIndexed { index, number ->
                assertThat(number).isBetween(1, 45)
                assertThat(자리별_후보.getValue(index + 1)).contains(number)
            }
        }
    }

    @Test
    fun 추천은_매번_캐시되는_자리별_통계_한_번만_조회한다() {
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(topNumbersOf(자리별_후보))

        lottoNumberGeneratorService.generateLottoNumbers()

        verify(drawingService, times(1)).getFrequenciesPerPosition()
        verify(drawingService, times(1)).getTopNumbersPerPosition()
        verifyNoMoreInteractions(drawingService)
    }

    @Test
    fun 자리별_통계가_비어_있으면_0으로_채운_결과() {
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(emptyList())

        assertEquals(빈_결과, lottoNumberGeneratorService.generateLottoNumbers())
    }

    @Test
    fun 한_자리라도_후보가_없으면_0으로_채운_결과() {
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(topNumbersOf(자리별_후보 - 4))

        assertEquals(빈_결과, lottoNumberGeneratorService.generateLottoNumbers())
    }

    @Test
    fun 모든_자리_후보가_같은_번호_하나뿐이면_유효_조합이_없어_0으로_채운_결과() {
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(topNumbersOf((1..6).associateWith { listOf(7) }))

        assertEquals(빈_결과, lottoNumberGeneratorService.generateLottoNumbers())
    }

    @Test
    fun 자리마다_후보가_하나씩이고_모두_다르면_그_조합을_반환한다() {
        val candidates = mapOf(
            1 to listOf(3), 2 to listOf(11), 3 to listOf(19),
            4 to listOf(27), 5 to listOf(35), 6 to listOf(43),
        )
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(topNumbersOf(candidates))

        repeat(20) {
            assertEquals(LottoNumberResponse(3, 11, 19, 27, 35, 43), lottoNumberGeneratorService.generateLottoNumbers())
        }
    }

    @Test
    fun 유효_조합이_하나뿐이면_중복_후보를_건너뛰고_그_조합을_반환한다() {
        // 1자리 후보 [3, 11] 중 11은 2자리 유일 후보와 겹치므로 3만 가능
        val candidates = mapOf(
            1 to listOf(11, 3), 2 to listOf(11), 3 to listOf(19),
            4 to listOf(27), 5 to listOf(35), 6 to listOf(43),
        )
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(topNumbersOf(candidates))

        repeat(20) {
            assertEquals(LottoNumberResponse(3, 11, 19, 27, 35, 43), lottoNumberGeneratorService.generateLottoNumbers())
        }
    }

    @Test
    fun 자리별_후보는_빈도_상위_5개까지만_사용한다() {
        // 6자리 후보 6개 중 빈도 상위 5개(1~5)는 모두 다른 자리와 겹치고, 빈도 6위(6)만 유효하다
        val topNumbers = (1..5).map { FrequencyResponse(it, it, 10) } +
            (1..5).map { FrequencyResponse(it, 6, (10 - it).toLong()) } +
            FrequencyResponse(6, 6, 1)
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(topNumbers)

        assertEquals(빈_결과, lottoNumberGeneratorService.generateLottoNumbers())
    }

    @Test
    fun 가능한_유효_조합은_모두_추천될_수_있다() {
        // 1자리 [1, 2], 나머지 자리는 고정 -> 유효 조합 2개
        val candidates = mapOf(
            1 to listOf(1, 2), 2 to listOf(10), 3 to listOf(20),
            4 to listOf(30), 5 to listOf(40), 6 to listOf(45),
        )
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(topNumbersOf(candidates))

        val firstNumbers = (1..200).map { lottoNumberGeneratorService.generateLottoNumbers().one }.toSet()

        assertEquals(setOf(1, 2), firstNumbers)
    }
}
