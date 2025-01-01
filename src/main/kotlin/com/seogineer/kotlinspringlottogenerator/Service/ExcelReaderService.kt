package com.seogineer.kotlinspringlottogenerator.Service

import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ExcelReaderService {
    fun readExcelFile(filePath: String): List<Drawing> {
        val drawings = mutableListOf<Drawing>()

        val fis = FileInputStream(File(filePath))
        val workbook = XSSFWorkbook(fis)
        val sheet = workbook.getSheetAt(0)
        for (row in sheet) {
            if (row.rowNum < 3) continue // 헤더 스킵

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
        workbook.close()
        return drawings
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
