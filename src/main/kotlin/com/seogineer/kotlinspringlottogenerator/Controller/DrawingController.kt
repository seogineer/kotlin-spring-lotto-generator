package com.seogineer.kotlinspringlottogenerator.Controller

import com.seogineer.kotlinspringlottogenerator.Dto.LottoNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import com.seogineer.kotlinspringlottogenerator.Service.DrawingService
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile


@RestController
class DrawingController(
    private val drawingService: DrawingService,
) {

    @GetMapping("/drawings")
    fun getDrawings(
        @RequestParam page: Int,
        @RequestParam size: Int
    ): Page<Drawing> {
        return drawingService.getDrawings(page, size)
    }

    @GetMapping("/generate")
    fun generateLottoNumbers(): LottoNumberResponse {
        return drawingService.generateLottoNumbers()
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
