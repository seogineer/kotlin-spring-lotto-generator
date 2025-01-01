package com.seogineer.kotlinspringlottogenerator.Service

import com.seogineer.kotlinspringlottogenerator.Dto.MostFrequentNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import com.seogineer.kotlinspringlottogenerator.Entity.DrawingRepository
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@Service
class DrawingService(
    private val drawingRepository: DrawingRepository
) {
    fun getMostFrequentNumber(): MostFrequentNumberResponse {
        return drawingRepository.getMostFrequentNumber()
    }

    fun readExcelFile(file: MultipartFile) {
        try {
            file.inputStream.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet: Sheet = workbook.getSheetAt(0) // 첫 번째 시트
                val drawings = mutableListOf<Drawing>()

                for (i in 3..sheet.lastRowNum) {
                    val row: Row = sheet.getRow(i)
                    val round = row.getCell(1).numericCellValue.toInt()
                    val date = stringToLocalDate(row.getCell(2).stringCellValue)
                    val one = row.getCell(13).numericCellValue.toInt()
                    val two = row.getCell(14).numericCellValue.toInt()
                    val three = row.getCell(15).numericCellValue.toInt()
                    val four = row.getCell(16).numericCellValue.toInt()
                    val five = row.getCell(17).numericCellValue.toInt()
                    val six = row.getCell(18).numericCellValue.toInt()
                    val bonus = row.getCell(19).numericCellValue.toInt()
                    val firstWinPrize = stringToLong(row.getCell(4).stringCellValue)
                    val firstWinners = row.getCell(3).numericCellValue.toInt()
                    drawings.add(Drawing(round, date, one, two, three, four, five, six, bonus, firstWinPrize, firstWinners))
                }

                drawingRepository.saveAll(drawings)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("엑셀 파일 처리 중 오류 발생")
        }
    }

    private fun stringToLocalDate(dateStr: String): LocalDate {
        val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        return LocalDate.parse(dateStr, formatter)
    }

    private fun stringToLong(prizeStr: String): Long {
        val prize = prizeStr.replace(",", "").replace("원", "")
        return prize.toLong()
    }
}
