package com.seogineer.kotlinspringlottogenerator.controller

import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.dto.LottoNumberResponse
import com.seogineer.kotlinspringlottogenerator.dto.UploadResponse
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.service.DrawingService
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile


@RestController
class DrawingController(
    private val drawingService: DrawingService,
) {

    @GetMapping("/drawings")
    fun getDrawings(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "5") size: Int
    ): Page<Drawing> {
        return drawingService.getDrawings(page, size)
    }

    @GetMapping("/drawings/generate")
    fun generateLottoNumbers(): LottoNumberResponse {
        return drawingService.generateLottoNumbers()
    }

    @GetMapping("/drawings/frequent")
    fun getMostFrequentNumbers(): List<FrequencyResponse> {
        return drawingService.getMostFrequentNumbers()
    }

    @GetMapping("/drawings/position/frequent")
    fun getTopNumbersPerPosition(): List<FrequencyResponse> {
        return drawingService.getTopNumbersPerPosition()
    }

    @PostMapping("/drawings/upload")
    fun uploadFile(@RequestParam("file") file: MultipartFile): ResponseEntity<UploadResponse> {
        return try {
            drawingService.readExcelFile(file)
            ResponseEntity.ok(UploadResponse(message = "파일 업로드 및 저장 성공!"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(UploadResponse(message = "파일 처리 실패: ${e.message}"))
        }
    }
}
