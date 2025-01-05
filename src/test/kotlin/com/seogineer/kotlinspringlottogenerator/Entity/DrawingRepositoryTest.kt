package com.seogineer.kotlinspringlottogenerator.Entity

import com.seogineer.kotlinspringlottogenerator.Config.QuerydslConfig
import com.seogineer.kotlinspringlottogenerator.Dto.LottoNumberResponse
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호1
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호2
import com.seogineer.kotlinspringlottogenerator.Fixture.DrawingFixtures.Companion.당첨번호3
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
    }

    @Test
    fun 역대_당첨_번호_조회() {
        val pageable: Pageable = PageRequest.of(0, 5)

        val drawings: Page<Drawing> = drawingRepository.getDrawings(pageable)

        assertEquals(3, drawings.content.size)
        assertEquals(3, drawings.totalElements)
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
}
