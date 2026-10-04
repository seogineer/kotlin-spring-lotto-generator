package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.dto.LottoNumberResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Random

/**
 * 가중 거절 샘플링 경로 검증 (Mockito, 시드 고정 Random).
 * getFrequenciesPerPosition()을 스텁해 실제로 가중 경로를 타게 하고, 폴백(getTopNumbersPerPosition)은
 * 의도한 테스트(G5, G6)에서만 스텁한다. 다른 테스트에서 폴백을 스텁하면 strict stubs가 불필요 스텁으로 실패시킨다.
 *
 * 시나리오: _workspace/06_developer_algo_changes.md 4절 G1~G7.
 */
@ExtendWith(MockitoExtension::class)
class LottoNumberGeneratorServiceWeightedTest {
    @Mock
    private lateinit var drawingService: DrawingService

    private val 빈_결과 = LottoNumberResponse(0, 0, 0, 0, 0, 0)

    private fun LottoNumberResponse.toList() = listOf(one, two, three, four, five, six)

    private fun service(random: Random, exponent: Double = 0.5) =
        LottoNumberGeneratorService(drawingService, random, exponent)

    /** 자리 -> (번호 -> 빈도) */
    private fun frequenciesOf(candidates: Map<Int, Map<Int, Long>>): List<FrequencyResponse> =
        candidates.flatMap { (position, numbers) ->
            numbers.map { (number, frequency) -> FrequencyResponse(number, position, frequency) }
        }

    /** 자리 -> 번호 목록 (빈도는 모두 같음) */
    private fun uniformOf(candidates: Map<Int, List<Int>>): List<FrequencyResponse> =
        frequenciesOf(candidates.mapValues { (_, numbers) -> numbers.associateWith { 1L } })

    private fun assertStrictlyAscendingInRange(numbers: List<Int>) {
        assertEquals(6, numbers.size)
        numbers.forEach { assertThat(it).isBetween(1, 45) }
        assertThat(numbers.zipWithNext().all { (a, b) -> a < b }).withFailMessage("오름차순 아님: $numbers").isTrue
    }

    // 실제 데이터처럼 자리별 후보 구간이 서로 겹치고 빈도가 제각각인 구성 (자리당 약 15~18개 후보)
    private val 겹치는_자리별_빈도: Map<Int, Map<Int, Long>> = (1..6).associateWith { position ->
        val from = maxOf(1, (position - 1) * 7 - 3)
        val to = minOf(45, (position - 1) * 7 + 14)
        (from..to).associateWith { number -> (1 + (number * 7 + position) % 9).toLong() }
    }

    // ----- G1 -----

    @Test
    fun 가중_경로_결과는_항상_1부터_45_사이의_엄격한_오름차순이고_각_자리_관측_후보에_속한다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(frequenciesOf(겹치는_자리별_빈도))
        val random = CountingRandom(20261004)
        val service = service(random)

        repeat(500) {
            val numbers = service.generateLottoNumbers().toList()

            assertStrictlyAscendingInRange(numbers)
            assertEquals(6, numbers.toSet().size)
            numbers.forEachIndexed { index, number ->
                assertThat(겹치는_자리별_빈도.getValue(index + 1).keys).contains(number)
            }
        }

        // 가중 경로만 탔다: 폴백 조회와 nextInt 없음, 시도마다 nextDouble 6회, 거절도 실제로 일어났다
        verify(drawingService, times(500)).getFrequenciesPerPosition()
        verify(drawingService, never()).getTopNumbersPerPosition()
        assertEquals(0, random.nextIntCount)
        assertEquals(0, random.nextDoubleCount % 6)
        assertThat(random.nextDoubleCount).isGreaterThan(6 * 500)
    }

    @Test
    fun 첫_시도에_수락되면_nextDouble을_정확히_6회_호출하고_폴백은_조회하지_않는다() {
        `when`(drawingService.getFrequenciesPerPosition())
            .thenReturn(uniformOf(mapOf(1 to listOf(3), 2 to listOf(11), 3 to listOf(19), 4 to listOf(27), 5 to listOf(35), 6 to listOf(43))))
        val random = CountingRandom(1)

        val response = service(random).generateLottoNumbers()

        assertEquals(LottoNumberResponse(3, 11, 19, 27, 35, 43), response)
        assertEquals(6, random.nextDoubleCount)
        assertEquals(0, random.nextIntCount)
        verify(drawingService, times(1)).getFrequenciesPerPosition()
        verifyNoMoreInteractions(drawingService)
    }

    // ----- G2 -----

    @Test
    fun 같은_시드와_입력과_지수면_결과_시퀀스가_같다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(frequenciesOf(겹치는_자리별_빈도))

        val first = service(Random(7)).let { s -> (1..100).map { s.generateLottoNumbers() } }
        val second = service(Random(7)).let { s -> (1..100).map { s.generateLottoNumbers() } }

        assertEquals(first, second)
    }

    @Test
    fun 다른_시드면_결과_시퀀스가_다르다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(frequenciesOf(겹치는_자리별_빈도))

        val seed7 = service(Random(7)).let { s -> (1..100).map { s.generateLottoNumbers() } }
        val seed8 = service(Random(8)).let { s -> (1..100).map { s.generateLottoNumbers() } }

        assertNotEquals(seed7, seed8)
    }

    // ----- G3 / G7(exponent 0 균등) -----

    /**
     * 1번 자리 {1: 빈도 9, 2: 빈도 1}, 2~6번 자리는 고정 후보 (거절이 없는 구성).
     * P(1) = 9^e / (9^e + 1): e=0 -> 1/2, e=0.5(기본) -> 3/4, e=1 -> 9/10, e=2 -> 81/82, e=-1 -> 1/10.
     * 시드 고정이라 결정적이며, 허용 오차 0.02는 N=10,000에서 표준편차(최대 0.005)의 4배 이상이다.
     */
    @ParameterizedTest(name = "exponent {0} -> 1번 자리 1의 비율 {1}")
    @CsvSource(
        "0.0, 0.5",
        "0.5, 0.75",
        "1.0, 0.9",
        "2.0, 0.987804878",
        "-1.0, 0.1",
    )
    fun 가중치는_빈도의_지수승에_비례한다(exponent: Double, expectedRatio: Double) {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(
            frequenciesOf(
                mapOf(
                    1 to mapOf(1 to 9L, 2 to 1L),
                    2 to mapOf(10 to 5L), 3 to mapOf(20 to 5L), 4 to mapOf(30 to 5L), 5 to mapOf(40 to 5L), 6 to mapOf(45 to 5L),
                )
            )
        )
        val random = CountingRandom(12345)
        val service = service(random, exponent)
        val n = 10_000

        val results = (1..n).map { service.generateLottoNumbers() }
        val ratioOfOne = results.count { it.one == 1 }.toDouble() / n

        assertThat(ratioOfOne).isCloseTo(expectedRatio, within(0.02))
        assertThat(results.map { it.one }.toSet()).isSubsetOf(1, 2)
        assertThat(results.map { it.toList().drop(1) }.toSet()).containsExactly(listOf(10, 20, 30, 40, 45))
        assertEquals(6 * n, random.nextDoubleCount) // 거절 없음
        verify(drawingService, never()).getTopNumbersPerPosition()
    }

    @Test
    fun 지수가_클수록_빈도_높은_번호가_더_자주_나온다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(
            frequenciesOf(
                mapOf(
                    1 to mapOf(1 to 9L, 2 to 1L),
                    2 to mapOf(10 to 5L), 3 to mapOf(20 to 5L), 4 to mapOf(30 to 5L), 5 to mapOf(40 to 5L), 6 to mapOf(45 to 5L),
                )
            )
        )
        val ratios = listOf(0.0, 0.5, 1.0, 2.0).map { exponent ->
            val service = service(Random(99), exponent)
            (1..5_000).count { service.generateLottoNumbers().one == 1 }
        }

        assertThat(ratios).isSorted
        assertThat(ratios.zipWithNext().all { (a, b) -> a < b }).withFailMessage("$ratios").isTrue
    }

    // ----- G4 -----

    @Test
    fun 오름차순이_아닌_조합은_버리고_재추첨하며_수락된_조합은_조건부_분포를_따른다() {
        // 독립 조합 4개 중 (40, 2)만 거절 -> (1,2) (1,41) (40,41)이 각각 1/3
        // (순차 조건부 샘플링이었다면 1/4, 1/4, 1/2가 된다)
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(
            uniformOf(mapOf(1 to listOf(1, 40), 2 to listOf(2, 41), 3 to listOf(42), 4 to listOf(43), 5 to listOf(44), 6 to listOf(45)))
        )
        val random = CountingRandom(4242)
        val service = service(random, 0.0)
        val n = 6_000

        val counts = (1..n).map { service.generateLottoNumbers().toList() }.groupingBy { it }.eachCount()

        assertEquals(
            setOf(listOf(1, 2, 42, 43, 44, 45), listOf(1, 41, 42, 43, 44, 45), listOf(40, 41, 42, 43, 44, 45)),
            counts.keys,
        )
        counts.values.forEach { assertThat(it.toDouble() / n).isCloseTo(1.0 / 3, within(0.03)) }
        // 시도당 nextDouble 6회이고, 거절이 있었으므로 시도 수 > 생성 수 (기대 시도 수 = n * 4/3)
        assertEquals(0, random.nextDoubleCount % 6)
        assertThat(random.nextDoubleCount / 6).isBetween(n + n / 4, n + n / 2)
        verify(drawingService, never()).getTopNumbersPerPosition()
    }

    @Test
    fun 자리_간_중복_번호는_거절된다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(
            uniformOf(mapOf(1 to listOf(5), 2 to listOf(5, 6), 3 to listOf(10), 4 to listOf(20), 5 to listOf(30), 6 to listOf(40)))
        )
        val random = CountingRandom(77)
        val service = service(random, 0.0)

        repeat(300) {
            assertEquals(LottoNumberResponse(5, 6, 10, 20, 30, 40), service.generateLottoNumbers())
        }

        assertThat(random.nextDoubleCount).isGreaterThan(6 * 300) // (5, 5)가 실제로 뽑혀 거절됐다
        verify(drawingService, never()).getTopNumbersPerPosition()
    }

    // ----- G5 -----

    private val 오름차순_불가능한_빈도 = uniformOf(
        mapOf(1 to listOf(30), 2 to listOf(10), 3 to listOf(40), 4 to listOf(41), 5 to listOf(42), 6 to listOf(43))
    )

    @Test
    fun 재시도_상한은_1000회다() {
        assertEquals(1_000, LottoNumberGeneratorService.MAX_ATTEMPTS)
    }

    @Test
    fun 상한까지_모두_거절되면_nextDouble_6000회_후_상위_5개_조합으로_폴백한다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(오름차순_불가능한_빈도)
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(
            uniformOf(mapOf(1 to listOf(1, 2), 2 to listOf(10), 3 to listOf(20), 4 to listOf(30), 5 to listOf(40), 6 to listOf(45)))
        )
        val random = CountingRandom(5)

        val numbers = service(random).generateLottoNumbers().toList()

        assertThat(numbers).isIn(listOf(1, 10, 20, 30, 40, 45), listOf(2, 10, 20, 30, 40, 45))
        assertStrictlyAscendingInRange(numbers)
        assertEquals(6 * LottoNumberGeneratorService.MAX_ATTEMPTS, random.nextDoubleCount)
        assertEquals(1, random.nextIntCount)
        val inOrder = inOrder(drawingService)
        inOrder.verify(drawingService).getFrequenciesPerPosition()
        inOrder.verify(drawingService).getTopNumbersPerPosition()
        verifyNoMoreInteractions(drawingService)
    }

    @Test
    fun 상한까지_거절되고_폴백_조합도_없으면_0으로_채운_결과() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(오름차순_불가능한_빈도)
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(uniformOf((1..6).associateWith { listOf(7) }))
        val random = CountingRandom(5)

        assertEquals(빈_결과, service(random).generateLottoNumbers())
        assertEquals(6_000, random.nextDoubleCount)
        assertEquals(0, random.nextIntCount)
        verify(drawingService, times(1)).getTopNumbersPerPosition()
    }

    @Test
    fun 폴백_결과도_엄격한_오름차순이다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(오름차순_불가능한_빈도)
        // 자리 간 후보가 겹치고 오름차순이 아닌 조합도 섞인 상위 5개
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(
            uniformOf(
                mapOf(
                    1 to listOf(1, 2, 3, 4, 12), 2 to listOf(5, 8, 10, 12, 13), 3 to listOf(12, 16, 19, 21, 23),
                    4 to listOf(21, 25, 27, 30, 33), 5 to listOf(33, 35, 37, 38, 40), 6 to listOf(38, 40, 42, 43, 45),
                )
            )
        )
        val service = service(Random(3))

        repeat(30) { assertStrictlyAscendingInRange(service.generateLottoNumbers().toList()) }
    }

    @Test
    fun 범위_밖_번호만_있는_자리가_있으면_상한까지_거절된_뒤_폴백한다() {
        // 비정상 데이터 방어: 6번 자리 관측 번호가 46뿐이면 어떤 시도도 1~45 조건을 통과하지 못한다
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(
            uniformOf(mapOf(1 to listOf(1), 2 to listOf(2), 3 to listOf(3), 4 to listOf(4), 5 to listOf(5), 6 to listOf(46)))
        )
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(emptyList())
        val random = CountingRandom(9)

        assertEquals(빈_결과, service(random).generateLottoNumbers())
        assertEquals(6_000, random.nextDoubleCount)
    }

    // ----- G6 -----

    @Test
    fun 자리별_빈도가_비어_있으면_샘플링_없이_바로_폴백한다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(emptyList())
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(
            uniformOf(mapOf(1 to listOf(3), 2 to listOf(11), 3 to listOf(19), 4 to listOf(27), 5 to listOf(35), 6 to listOf(43)))
        )
        val random = CountingRandom(1)

        assertEquals(LottoNumberResponse(3, 11, 19, 27, 35, 43), service(random).generateLottoNumbers())
        assertEquals(0, random.nextDoubleCount)
        assertEquals(1, random.nextIntCount)
    }

    @Test
    fun 자리별_빈도와_폴백이_모두_비어_있으면_0으로_채운_결과() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(emptyList())
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(emptyList())
        val random = CountingRandom(1)

        assertEquals(빈_결과, service(random).generateLottoNumbers())
        assertEquals(0, random.nextDoubleCount)
        assertEquals(0, random.nextIntCount)
    }

    @Test
    fun 한_자리라도_관측_후보가_없으면_샘플링_없이_폴백한다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(frequenciesOf(겹치는_자리별_빈도 - 4))
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(emptyList())
        val random = CountingRandom(1)

        assertEquals(빈_결과, service(random).generateLottoNumbers())
        assertEquals(0, random.nextDoubleCount)
        verify(drawingService, times(1)).getTopNumbersPerPosition()
    }

    @Test
    fun 빈도가_0인_항목만_있는_자리는_후보가_없는_것으로_보고_폴백한다() {
        `when`(drawingService.getFrequenciesPerPosition()).thenReturn(
            frequenciesOf(겹치는_자리별_빈도 + (6 to mapOf(45 to 0L)))
        )
        `when`(drawingService.getTopNumbersPerPosition()).thenReturn(emptyList())
        val random = CountingRandom(1)

        assertEquals(빈_결과, service(random).generateLottoNumbers())
        assertEquals(0, random.nextDoubleCount)
    }

    // ----- G7 (생성자 검증) -----

    @ParameterizedTest
    @ValueSource(doubles = [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY])
    fun 지수가_NaN이거나_무한대면_생성자에서_IllegalArgumentException(exponent: Double) {
        assertThrows<IllegalArgumentException> { LottoNumberGeneratorService(drawingService, Random(1), exponent) }
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.0, 0.5, 1.0, 2.0, -1.0])
    fun 유한한_지수는_생성할_수_있다(exponent: Double) {
        assertDoesNotThrow { LottoNumberGeneratorService(drawingService, Random(1), exponent) }
    }
}
