package com.seogineer.kotlinspringlottogenerator.entity

import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.dto.LottoNumberResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface DrawingRepositoryCustom {
    fun getDrawings(pageable: Pageable): Page<Drawing>
    fun generateLottoNumbers(): LottoNumberResponse
    fun getMostFrequentNumbers(): List<FrequencyResponse>
    fun getTopNumbersPerPosition(): List<FrequencyResponse>
}
