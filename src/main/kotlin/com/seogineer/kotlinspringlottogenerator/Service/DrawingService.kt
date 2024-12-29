package com.seogineer.kotlinspringlottogenerator.Service

import com.seogineer.kotlinspringlottogenerator.Dto.MostFrequentNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.DrawingRepository
import org.springframework.stereotype.Service


@Service
class DrawingService(
    private val drawingRepository: DrawingRepository
) {
    fun getMostFrequentNumber(): MostFrequentNumberResponse {
        return drawingRepository.getMostFrequentNumber()
    }
}
