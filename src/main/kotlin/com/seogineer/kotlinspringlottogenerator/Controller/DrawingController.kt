package com.seogineer.kotlinspringlottogenerator.Controller

import com.seogineer.kotlinspringlottogenerator.Dto.MostFrequentNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import com.seogineer.kotlinspringlottogenerator.Service.DrawingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DrawingController(private val drawingService: DrawingService) {
    @GetMapping("/getAll")
    fun getMemberByName(): List<Drawing> {
        return drawingService.getAll()
    }

    @GetMapping("/getMostFrequentNumber")
    fun getMostFrequentNumber(): MostFrequentNumberResponse {
        return drawingService.getMostFrequentNumber()
    }
}
