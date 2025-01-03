package com.seogineer.kotlinspringlottogenerator.Entity

import com.seogineer.kotlinspringlottogenerator.Dto.LottoNumberResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface DrawingRepositoryCustom {
    fun getDrawings(pageable: Pageable): Page<Drawing>
    fun generateLottoNumbers(): LottoNumberResponse
}
