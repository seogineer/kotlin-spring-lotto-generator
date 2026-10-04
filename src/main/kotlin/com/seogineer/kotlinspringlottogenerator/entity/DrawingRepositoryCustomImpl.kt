package com.seogineer.kotlinspringlottogenerator.entity

import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.impl.JPAQueryFactory
import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.entity.QDrawing.drawing
import lombok.RequiredArgsConstructor
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable


@RequiredArgsConstructor
class DrawingRepositoryCustomImpl(private val queryFactory: JPAQueryFactory) : DrawingRepositoryCustom {

    override fun getDrawings(pageable: Pageable): Page<Drawing> {
        val drawings: List<Drawing> = queryFactory
            .selectFrom(drawing)
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .orderBy(drawing.round.desc())
            .fetch()

        val total: Long = queryFactory
            .select(drawing.count())
            .from(drawing)
            .fetchOne() ?: 0L

        return PageImpl(drawings, pageable, total)
    }

    override fun getMostFrequentNumbers(): List<FrequencyResponse> {
        // 6개 컬럼을 한 번의 쿼리로 가져와 메모리에서 번호별 출현 횟수를 집계한다 (테이블 약 1,200행 x 6).
        val allCounts = HashMap<Int, Long>()
        for (row in fetchNumberRows()) {
            for (number in row) {
                allCounts.merge(number, 1L, Long::plus)
            }
        }

        // 동률은 번호 오름차순으로 고정한다 (기존 group by 결과 순서 의존 제거)
        val sortedCounts = allCounts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })

        val top5Cutoff = sortedCounts.getOrNull(4)?.value ?: 0L // 중복이 존재하는 경우

        return sortedCounts
            .filter { it.value >= top5Cutoff }
            .mapIndexed { index, entry ->
                FrequencyResponse(
                    number = entry.key,
                    position = index + 1,
                    frequency = entry.value
                )
            }
    }

    override fun getFrequenciesPerPosition(): List<FrequencyResponse> {
        // 쿼리 1개로 6개 컬럼을 가져와 자리별로 관측된 모든 번호의 출현 횟수를 집계한다.
        val countsByPosition = List(POSITION_COLUMNS.size) { HashMap<Int, Long>() }
        for (row in fetchNumberRows()) {
            row.forEachIndexed { index, number -> countsByPosition[index].merge(number, 1L, Long::plus) }
        }

        // 자리 오름차순, 빈도 내림차순, 동률은 번호 오름차순
        return countsByPosition.flatMapIndexed { index, counts ->
            counts.entries
                .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
                .map { FrequencyResponse(number = it.key, position = index + 1, frequency = it.value) }
        }
    }

    /** 각 행의 1~6번 자리 번호 목록. null(이론상 없음)은 제외한다. */
    private fun fetchNumberRows(): List<List<Int>> {
        return queryFactory
            .select(*POSITION_COLUMNS.toTypedArray())
            .from(drawing)
            .fetch()
            .map { row -> POSITION_COLUMNS.mapNotNull { row.get(it) } }
    }

    override fun getTopNumbersPerPosition(): List<FrequencyResponse> {
        val position1 = getTopNumbers(drawing.one, 1)
        val position2 = getTopNumbers(drawing.two, 2)
        val position3 = getTopNumbers(drawing.three, 3)
        val position4 = getTopNumbers(drawing.four, 4)
        val position5 = getTopNumbers(drawing.five, 5)
        val position6 = getTopNumbers(drawing.six, 6)

        return (position1 + position2 + position3 + position4 + position5 + position6)
            .sortedWith(compareBy({ it.position }, { -it.frequency }))
    }

    private fun getTopNumbers(column: NumberPath<Int>, position: Int): List<FrequencyResponse> {
        return queryFactory
            .select(column, column.count())
            .from(drawing)
            .groupBy(column)
            .orderBy(column.count().desc(), column.asc()) // 동률은 번호 오름차순으로 고정
            .limit(5)
            .fetch()
            .map { row ->
                FrequencyResponse(
                    number = row.get(column) ?: 0,
                    position = position,
                    frequency = row.get(column.count()) ?: 0
                )
            }
    }

    companion object {
        private val POSITION_COLUMNS: List<NumberPath<Int>> =
            listOf(drawing.one, drawing.two, drawing.three, drawing.four, drawing.five, drawing.six)
    }
}
