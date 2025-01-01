package com.seogineer.kotlinspringlottogenerator.Config

import com.seogineer.kotlinspringlottogenerator.Entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.Service.ExcelReaderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DataInitializer {

    @Autowired
    private lateinit var excelReaderService: ExcelReaderService

    @Autowired
    private lateinit var drawingRepository: DrawingRepository

    @Bean
    fun initDatabase() {
        val filePath = "/home/ubuntu/excel.xlsx"
        val drawings = excelReaderService.readExcelFile(filePath)
        drawingRepository.saveAll(drawings)
    }
}