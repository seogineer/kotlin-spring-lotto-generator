package com.seogineer.kotlinspringlottogenerator.Entity

import com.querydsl.jpa.impl.JPAQueryFactory
import com.seogineer.kotlinspringlottogenerator.Dto.MostFrequentNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.QDrawing.drawing
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
            .fetch()

        val total: Long = queryFactory
            .select(drawing.count())
            .from(drawing)
            .fetchOne() ?: 0L

        return PageImpl(drawings, pageable, total)
    }

    override fun getMostFrequentNumber(): MostFrequentNumberResponse {
        val one = queryFactory
            .select(drawing.one)
            .from(drawing)
            .groupBy(drawing.one)
            .orderBy(drawing.one.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val two = queryFactory
            .select(drawing.two)
            .from(drawing)
            .groupBy(drawing.two)
            .orderBy(drawing.two.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val three = queryFactory
            .select(drawing.three)
            .from(drawing)
            .groupBy(drawing.three)
            .orderBy(drawing.three.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val four = queryFactory
            .select(drawing.four)
            .from(drawing)
            .groupBy(drawing.four)
            .orderBy(drawing.four.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val five = queryFactory
            .select(drawing.five)
            .from(drawing)
            .groupBy(drawing.five)
            .orderBy(drawing.five.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val six = queryFactory
            .select(drawing.six)
            .from(drawing)
            .groupBy(drawing.six)
            .orderBy(drawing.six.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val bonus = queryFactory
            .select(drawing.bonus)
            .from(drawing)
            .groupBy(drawing.bonus)
            .orderBy(drawing.bonus.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        return MostFrequentNumberResponse(one, two, three, four, five, six, bonus)
    }
}
