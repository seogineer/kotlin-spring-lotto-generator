package com.seogineer.kotlinspringlottogenerator.Controller

import com.seogineer.kotlinspringlottogenerator.Dto.MostFrequentNumberResponse
import com.seogineer.kotlinspringlottogenerator.Service.DrawingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DrawingController(private val drawingService: DrawingService) {

    @GetMapping("/getMostFrequentNumber")
    fun getMostFrequentNumber(): MostFrequentNumberResponse {
        return drawingService.getMostFrequentNumber()
    }
}
