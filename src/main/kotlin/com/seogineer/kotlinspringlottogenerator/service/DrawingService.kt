package com.seogineer.kotlinspringlottogenerator.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.dto.LatestNumberResponse
import com.seogineer.kotlinspringlottogenerator.dto.LottoNumberResponse
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@Service
@Transactional(readOnly = true)
class DrawingService(
    private val drawingRepository: DrawingRepository
) {

    @Cacheable(value = ["drawings"], key = "#page")
    fun getDrawings(page: Int, size: Int): Page<Drawing> {
        val pageable: Pageable = PageRequest.of(page, size)
        return drawingRepository.getDrawings(pageable)
    }

    fun generateLottoNumbers(): LottoNumberResponse {
        return drawingRepository.generateLottoNumbers()
    }

    @Cacheable(value = ["mostFrequentNumbers"])
    fun getMostFrequentNumbers(): List<FrequencyResponse> {
        return drawingRepository.getMostFrequentNumbers()
    }

    @Cacheable(value = ["topNumbersPerPosition"])
    fun getTopNumbersPerPosition(): List<FrequencyResponse> {
        return drawingRepository.getTopNumbersPerPosition()
    }

    @CacheEvict(value = ["drawings", "mostFrequentNumbers", "topNumbersPerPosition"], allEntries = true)
    @Transactional
    fun readExcelFile(file: MultipartFile) {
        val fileName = file.originalFilename ?: ""
        if (!fileName.endsWith(".xlsx")) {
            throw IllegalArgumentException("지원하지 않는 파일 형식입니다. 엑셀 파일(.xlsx)만 업로드할 수 있습니다.")
        }

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

    @CacheEvict(value = ["drawings", "mostFrequentNumbers", "topNumbersPerPosition"], allEntries = true)
//    @Scheduled(cron = "0 0 12 ? * MON", zone = "Asia/Seoul")
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @Transactional
    fun fetchAndStoreLottoNumbers() {
        val latestDrawNo = findLatestRound()
        val apiUrl = "https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo=${latestDrawNo}"
        val restTemplate = RestTemplate()
        try {
            val response = restTemplate.getForObject(apiUrl, String::class.java)
            val latestNumberResponse = ObjectMapper()
                                        .registerKotlinModule()
                                        .readValue(response, LatestNumberResponse::class.java)
            if (latestNumberResponse.returnValue == "success") {
                val drawing = Drawing(
                    round = latestNumberResponse.drwNo,
                    date = LocalDate.parse(
                        latestNumberResponse.drwNoDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    one = latestNumberResponse.drwtNo1,
                    two = latestNumberResponse.drwtNo2,
                    three = latestNumberResponse.drwtNo3,
                    four = latestNumberResponse.drwtNo4,
                    five = latestNumberResponse.drwtNo5,
                    six = latestNumberResponse.drwtNo6,
                    bonus = latestNumberResponse.bnusNo,
                    firstWinPrize = latestNumberResponse.firstWinamnt,
                    firstWinners = latestNumberResponse.firstPrzwnerCo
                )
                drawingRepository.save(drawing)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
