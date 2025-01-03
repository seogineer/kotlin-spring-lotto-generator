package com.seogineer.kotlinspringlottogenerator.Entity

import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.impl.JPAQueryFactory
import com.seogineer.kotlinspringlottogenerator.Dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.Dto.LottoNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.QDrawing.drawing
import lombok.RequiredArgsConstructor
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.*


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

    override fun generateLottoNumbers(): LottoNumberResponse {
        // 각 자리별 번호 출현 빈도수 계산
        val frequencies: MutableList<FrequencyResponse> = ArrayList()

        // 각 컬럼별로 빈도수 계산
        frequencies.addAll(getFrequencyForPosition(drawing.one, 1))
        frequencies.addAll(getFrequencyForPosition(drawing.two, 2))
        frequencies.addAll(getFrequencyForPosition(drawing.three, 3))
        frequencies.addAll(getFrequencyForPosition(drawing.four, 4))
        frequencies.addAll(getFrequencyForPosition(drawing.five, 5))
        frequencies.addAll(getFrequencyForPosition(drawing.six, 6))

        // 각 자리별로 가장 많이 나온 상위 5개 번호 선택
        val topNumbersByPosition: MutableMap<Int, List<Int>> = HashMap()
        for (position in 1..6) {
            val topNumbers = frequencies
                .filter { it.position == position }
                .sortedByDescending { it.frequency }
                .take(5)
                .map { it.number }
            topNumbersByPosition[position] = topNumbers
        }

        // 중복되지 않는 번호 조합 생성
        val validCombinations = generateValidCombinations(topNumbersByPosition)

        if (validCombinations.isEmpty()) {
            return LottoNumberResponse(0, 0, 0, 0, 0, 0)
        }

        // 랜덤하게 하나의 조합 선택
        val randomIndex: Int = Random().nextInt(validCombinations.size)
        val selectedNumbers = validCombinations[randomIndex]
        return LottoNumberResponse(
            selectedNumbers[0],
            selectedNumbers[1],
            selectedNumbers[2],
            selectedNumbers[3],
            selectedNumbers[4],
            selectedNumbers[5]
        )
    }

    private fun getFrequencyForPosition(column: NumberPath<Int>, position: Int): List<FrequencyResponse> {
        return queryFactory
            .select(Projections.constructor(FrequencyResponse::class.java,
                column,
                Expressions.constant(position),
                column.count()))
            .from(drawing)
            .groupBy(column)
            .fetch()
    }

    private fun generateValidCombinations(topNumbersByPosition: Map<Int, List<Int>>): List<List<Int>> {
        val validCombinations: MutableList<List<Int>> = ArrayList()
        for (n1 in topNumbersByPosition[1]!!) {
            for (n2 in topNumbersByPosition[2]!!) {
                if (n2 == n1) continue
                for (n3 in topNumbersByPosition[3]!!) {
                    if (n3 == n1 || n3 == n2) continue
                    for (n4 in topNumbersByPosition[4]!!) {
                        if (n4 == n1 || n4 == n2 || n4 == n3) continue
                        for (n5 in topNumbersByPosition[5]!!) {
                            if (n5 == n1 || n5 == n2 || n5 == n3 || n5 == n4) continue
                            for (n6 in topNumbersByPosition[6]!!) {
                                if (n6 == n1 || n6 == n2 || n6 == n3 || n6 == n4 || n6 == n5) continue
                                validCombinations.add(arrayListOf(n1, n2, n3, n4, n5, n6))
                            }
                        }
                    }
                }
            }
        }
        return validCombinations
    }
}
