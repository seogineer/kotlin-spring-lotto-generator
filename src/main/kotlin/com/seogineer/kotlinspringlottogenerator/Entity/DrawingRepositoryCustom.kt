package com.seogineer.kotlinspringlottogenerator.Entity

import com.seogineer.kotlinspringlottogenerator.Dto.MostFrequentNumberResponse

interface DrawingRepositoryCustom {
    fun getMostFrequentNumber(): MostFrequentNumberResponse
}
