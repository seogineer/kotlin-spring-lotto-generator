package com.seogineer.kotlinspringlottogenerator.Controller

import com.seogineer.kotlinspringlottogenerator.Service.ExcelReaderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile


@RestController
@RequestMapping("/excel")
class ExcelReaderController(
    private val excelService: ExcelReaderService
) {

    @PostMapping("/upload")
    fun uploadFile(@RequestParam("file") file: MultipartFile): ResponseEntity<String> {
        return try {
            excelService.readExcelFile(file)
            ResponseEntity.ok("파일 업로드 및 저장 성공!")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body("파일 처리 실패: " + e.message)
        }
    }
}
