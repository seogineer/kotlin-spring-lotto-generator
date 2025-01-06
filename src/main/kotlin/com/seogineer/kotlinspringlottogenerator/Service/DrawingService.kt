package com.seogineer.kotlinspringlottogenerator.Service

import com.seogineer.kotlinspringlottogenerator.Dto.LottoNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import com.seogineer.kotlinspringlottogenerator.Entity.DrawingRepository
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.json.JSONObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@Service
class DrawingService(
    private val drawingRepository: DrawingRepository
) {

    fun getDrawings(page: Int, size: Int): Page<Drawing> {
        val pageable: Pageable = PageRequest.of(page, size)
        return drawingRepository.getDrawings(pageable)
    }

    fun generateLottoNumbers(): LottoNumberResponse {
        return drawingRepository.generateLottoNumbers()
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
                    val firstWinPrize = stringToBigInteger(row.getCell(4).stringCellValue)
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

    private fun stringToBigInteger(prizeStr: String): BigInteger {
        val prize = prizeStr.replace(",", "").replace("원", "")
        return prize.toBigInteger()
    }

    fun findLatestRound(): Int {
        return drawingRepository.findTopByOrderByRoundDesc().get().round + 1
    }

    @Scheduled(cron = "0 0 12 ? * MON", zone = "Asia/Seoul")
    fun fetchAndStoreLottoNumbers() {
        val latestDrawNo = findLatestRound()
        val apiUrl = "https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo=${latestDrawNo}"
        val restTemplate = RestTemplate()
        try {
            val response = restTemplate.getForObject(apiUrl, String::class.java)
            val jsonObject = JSONObject(response)
            if (jsonObject.getString("returnValue").equals("success")) {
                val round: Int = jsonObject.getInt("drwNo")
                val date: LocalDate = LocalDate.parse(
                    jsonObject.getString("drwNoDate"), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val one: Int = jsonObject.getInt("drwtNo1")
                val two: Int = jsonObject.getInt("drwtNo2")
                val three: Int = jsonObject.getInt("drwtNo3")
                val four: Int = jsonObject.getInt("drwtNo4")
                val five: Int = jsonObject.getInt("drwtNo5")
                val six: Int = jsonObject.getInt("drwtNo6")
                val bonus: Int = jsonObject.getInt("bnusNo")
                val firstWinPrize = BigInteger(jsonObject.getInt("firstWinamnt").toString())
                val firstWinners: Int = jsonObject.getInt("firstPrzwnerCo")
                drawingRepository.save(
                    Drawing(round, date, one, two, three, four, five, six, bonus, firstWinPrize, firstWinners))
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
}
