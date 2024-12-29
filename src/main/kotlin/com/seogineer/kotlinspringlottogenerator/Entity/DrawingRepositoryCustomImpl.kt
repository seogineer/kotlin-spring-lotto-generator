package com.seogineer.kotlinspringlottogenerator.Entity

import com.querydsl.jpa.impl.JPAQueryFactory
import com.seogineer.kotlinspringlottogenerator.Dto.MostFrequentNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.QDrawing.drawing
import lombok.RequiredArgsConstructor

@RequiredArgsConstructor
class DrawingRepositoryCustomImpl(private val jpaQueryFactory: JPAQueryFactory) : DrawingRepositoryCustom {

    override fun getMostFrequentNumber(): MostFrequentNumberResponse {
        val one = jpaQueryFactory
            .select(drawing.one)
            .from(drawing)
            .groupBy(drawing.one)
            .orderBy(drawing.one.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val two = jpaQueryFactory
            .select(drawing.two)
            .from(drawing)
            .groupBy(drawing.two)
            .orderBy(drawing.two.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val three = jpaQueryFactory
            .select(drawing.three)
            .from(drawing)
            .groupBy(drawing.three)
            .orderBy(drawing.three.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val four = jpaQueryFactory
            .select(drawing.four)
            .from(drawing)
            .groupBy(drawing.four)
            .orderBy(drawing.four.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val five = jpaQueryFactory
            .select(drawing.five)
            .from(drawing)
            .groupBy(drawing.five)
            .orderBy(drawing.five.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val six = jpaQueryFactory
            .select(drawing.six)
            .from(drawing)
            .groupBy(drawing.six)
            .orderBy(drawing.six.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        val bonus = jpaQueryFactory
            .select(drawing.bonus)
            .from(drawing)
            .groupBy(drawing.bonus)
            .orderBy(drawing.bonus.count().desc())
            .limit(1)
            .fetchOne() ?: 0

        return MostFrequentNumberResponse(one, two, three, four, five, six, bonus)
    }
}
