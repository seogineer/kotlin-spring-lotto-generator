package com.seogineer.kotlinspringlottogenerator.Controller

import com.seogineer.kotlinspringlottogenerator.Dto.MostFrequentNumberResponse
import com.seogineer.kotlinspringlottogenerator.Service.DrawingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/drawing")
class DrawingController(
    private val drawingService: DrawingService,
) {

    @GetMapping("/getMostFrequentNumber")
    fun getMostFrequentNumber(): MostFrequentNumberResponse {
        return drawingService.getMostFrequentNumber()
    }

    @PostMapping("/upload")
    fun uploadFile(@RequestParam("file") file: MultipartFile): ResponseEntity<String> {
        return try {
            drawingService.readExcelFile(file)
            ResponseEntity.ok("파일 업로드 및 저장 성공!")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body("파일 처리 실패: " + e.message)
        }
    }
}
