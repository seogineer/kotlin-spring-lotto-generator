package com.seogineer.kotlinspringlottogenerator.entity

import com.seogineer.kotlinspringlottogenerator.config.QuerydslConfig
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호1
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호10
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호11
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호12
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호13
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호14
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호15
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호16
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호17
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호18
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호19
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호20
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호21
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

    @Test
    fun 가장_많이_뽑힌_번호_동률은_번호_오름차순이고_position은_1부터_연속() {
        val mostFrequentNumbers = drawingRepository.getMostFrequentNumbers()

        // 기존 기대값 유지: 40(5) / 16·25·42(4) / 9·27(3, cutoff 동률 모두 포함)
        assertEquals(listOf(40, 16, 25, 42, 9, 27), mostFrequentNumbers.map { it.number })
        assertEquals(listOf(5L, 4L, 4L, 4L, 3L, 3L), mostFrequentNumbers.map { it.frequency })
        assertEquals((1..6).toList(), mostFrequentNumbers.map { it.position })
    }

    @Test
    fun 가장_많이_뽑힌_번호_동률_정렬과_cutoff_동률_포함() {
        drawingRepository.deleteAll()
        drawingRepository.saveAll(listOf(당첨번호11, 당첨번호12, 당첨번호13))

        val mostFrequentNumbers = drawingRepository.getMostFrequentNumbers()

        // 빈도 내림차순, 같은 빈도는 번호 오름차순. 5번째 빈도(2)와 같은 3·33·41은 모두 포함되어 6개
        assertEquals(listOf(7, 12, 20, 3, 33, 41), mostFrequentNumbers.map { it.number })
        assertEquals(listOf(3L, 3L, 3L, 2L, 2L, 2L), mostFrequentNumbers.map { it.frequency })
        assertEquals((1..6).toList(), mostFrequentNumbers.map { it.position })
    }

    @Test
    fun 데이터가_없으면_가장_많이_뽑힌_번호는_빈_리스트() {
        drawingRepository.deleteAll()

        val mostFrequentNumbers = drawingRepository.getMostFrequentNumbers()

        assertTrue(mostFrequentNumbers.isEmpty())
    }

    /** 자리별 5위 경계 동률 데이터(당첨번호14~21, 8행)만 남긴다. 큰 번호부터 적재한다. */
    private fun 동률_데이터만_적재() {
        drawingRepository.deleteAll()
        drawingRepository.saveAll(listOf(당첨번호14, 당첨번호15, 당첨번호16, 당첨번호17, 당첨번호18, 당첨번호19, 당첨번호20, 당첨번호21))
    }

    @Test
    fun 자리별_빈도는_자리마다_빈도_합이_행_수와_같다() {
        val frequencies = drawingRepository.getFrequenciesPerPosition()

        val byPosition = frequencies.groupBy { it.position }
        assertEquals((1..6).toList(), byPosition.keys.sorted())
        byPosition.forEach { (position, items) ->
            assertEquals(10L, items.sumOf { it.frequency }, "자리 $position 빈도 합")
            assertEquals(items.size, items.map { it.number }.toSet().size, "자리 $position 번호 중복")
            items.forEach { assertTrue(it.number in 1..45) }
        }
    }

    @Test
    fun 자리별_빈도는_자리_오름차순_빈도_내림차순_동률은_번호_오름차순() {
        val frequencies = drawingRepository.getFrequenciesPerPosition()

        val expectedOrder = frequencies.sortedWith(
            compareBy<com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse> { it.position }
                .thenByDescending { it.frequency }
                .thenBy { it.number }
        )
        assertEquals(expectedOrder, frequencies)

        // 픽스처 1~10의 1번 자리: 2·9·14(2회), 8·10·11·16(1회)
        val position1 = frequencies.filter { it.position == 1 }
        assertEquals(listOf(2, 9, 14, 8, 10, 11, 16), position1.map { it.number })
        assertEquals(listOf(2L, 2L, 2L, 1L, 1L, 1L, 1L), position1.map { it.frequency })
    }

    @Test
    fun 자리별_빈도는_관측된_번호만_포함한다() {
        동률_데이터만_적재()

        val frequencies = drawingRepository.getFrequenciesPerPosition()

        assertEquals(6 * 7, frequencies.size)
        frequencies.groupBy { it.position }.forEach { (position, items) ->
            assertEquals(8L, items.sumOf { it.frequency }, "자리 $position 빈도 합")
        }
        val position1 = frequencies.filter { it.position == 1 }
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), position1.map { it.number })
        assertEquals(listOf(2L, 1L, 1L, 1L, 1L, 1L, 1L), position1.map { it.frequency })
    }

    @Test
    fun 데이터가_없으면_자리별_빈도는_빈_리스트() {
        drawingRepository.deleteAll()

        assertTrue(drawingRepository.getFrequenciesPerPosition().isEmpty())
    }

    @Test
    fun 자리별_상위_5개는_5위_경계_동률에서_작은_번호를_포함하고_동률은_번호_오름차순() {
        동률_데이터만_적재()

        val top = drawingRepository.getTopNumbersPerPosition()

        assertEquals(30, top.size)
        val expected = mapOf(
            1 to listOf(7, 1, 2, 3, 4),
            2 to listOf(16, 10, 11, 12, 13),
            3 to listOf(23, 17, 18, 19, 20),
            4 to listOf(30, 24, 25, 26, 27),
            5 to listOf(37, 31, 32, 33, 34),
            6 to listOf(44, 38, 39, 40, 41),
        )
        val byPosition = top.groupBy { it.position }
        assertEquals((1..6).toList(), byPosition.keys.toList())
        expected.forEach { (position, numbers) ->
            assertEquals(numbers, byPosition.getValue(position).map { it.number }, "자리 $position")
            assertEquals(listOf(2L, 1L, 1L, 1L, 1L), byPosition.getValue(position).map { it.frequency }, "자리 $position")
        }
    }

    @Test
    fun 자리별_상위_5개는_자리별_빈도의_앞_5개와_같다() {
        // 픽스처 1~10 (5위 경계 동률 포함)
        val top = drawingRepository.getTopNumbersPerPosition()
        val frequencies = drawingRepository.getFrequenciesPerPosition()
        assertEquals(frequencies.groupBy { it.position }.mapValues { it.value.take(5) }, top.groupBy { it.position })

        // 당첨번호14~21
        동률_데이터만_적재()
        val top동률 = drawingRepository.getTopNumbersPerPosition()
        val frequencies동률 = drawingRepository.getFrequenciesPerPosition()
        assertEquals(frequencies동률.groupBy { it.position }.mapValues { it.value.take(5) }, top동률.groupBy { it.position })
    }
}
