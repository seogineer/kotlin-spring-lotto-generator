package com.seogineer.kotlinspringlottogenerator.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.dto.LottoDrawingApiResponse
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@Service
@Transactional(readOnly = true)
class DrawingService(
    private val drawingRepository: DrawingRepository,
    private val restTemplate: RestTemplate,
    private val eventPublisher: ApplicationEventPublisher,
) {

    // 범위를 벗어난 요청(size > 20 또는 page > 300)은 캐시하지 않고 조회만 한다 (캐시 키 무제한 증가 방지)
    @Cacheable(value = ["drawings"], key = "#page + ':' + #size", condition = "#size <= 20 && #page <= 300")
    fun getDrawings(page: Int, size: Int): Page<Drawing> {
        val pageable: Pageable = PageRequest.of(page, size)
        return drawingRepository.getDrawings(pageable)
    }

    @Cacheable(value = ["mostFrequentNumbers"])
    fun getMostFrequentNumbers(): List<FrequencyResponse> {
        return drawingRepository.getMostFrequentNumbers()
    }

    @Cacheable(value = ["topNumbersPerPosition"])
    fun getTopNumbersPerPosition(): List<FrequencyResponse> {
        return drawingRepository.getTopNumbersPerPosition()
    }

    /** 자리별로 관측된 모든 번호의 빈도 (가중 무작위 추천용). */
    @Cacheable(value = ["frequenciesPerPosition"])
    fun getFrequenciesPerPosition(): List<FrequencyResponse> {
        return drawingRepository.getFrequenciesPerPosition()
    }

    /** 캐시 워밍업 직전 전체 무효화용. DrawingCacheWarmer가 프록시를 통해 호출한다. 캐시만 비우므로 DB 트랜잭션을 열지 않는다. */
    @CacheEvict(value = ["drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition"], allEntries = true)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun evictAllCaches() {
    }

    // beforeInvocation = true: 캐시/트랜잭션 프록시 중첩 순서와 무관하게 evict가 워밍업(트랜잭션 완료 후)보다 먼저 일어나도록 한다
    @CacheEvict(value = ["drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition"], allEntries = true, beforeInvocation = true)
    @Transactional
    fun readExcelFile(file: MultipartFile) {
        eventPublisher.publishEvent(DrawingsChangedEvent(DrawingsChangedEvent.Source.EXCEL_UPLOAD))
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

    /** DB에 저장된 최신 회차. DB가 비어 있으면 0. */
    fun findLatestStoredRound(): Int {
        return drawingRepository.findTopByOrderByRoundDesc().map { it.round }.orElse(0)
    }

    @CacheEvict(value = ["drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition"], allEntries = true, beforeInvocation = true)
    @Scheduled(cron = "0 0 12 ? * MON", zone = "Asia/Seoul")
    @Transactional
    fun fetchAndStoreLottoNumbers() {
        eventPublisher.publishEvent(DrawingsChangedEvent(DrawingsChangedEvent.Source.SCHEDULER))
        var lastRound = findLatestStoredRound()
        val objectMapper = ObjectMapper().registerKotlinModule()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        try {
            // 응답은 srchLtEpsd 주변 약 10개 회차(최신 회차에서 잘림). 새 회차가 없을 때까지 반복 호출해 누락분을 따라잡는다.
            for (attempt in 1..MAX_FETCH_ITERATIONS) {
                val apiUrl = "$LOTTO_API_URL?srchDir=center&srchLtEpsd=${lastRound + 1}"
                val response = restTemplate.getForObject(apiUrl, String::class.java)
                val apiResponse = objectMapper.readValue(response, LottoDrawingApiResponse::class.java)
                val baseRound = lastRound
                val newDrawings = apiResponse.data?.list.orEmpty()
                    .filter { it.ltEpsd > baseRound }
                    .sortedBy { it.ltEpsd }
                    .map {
                        Drawing(
                            round = it.ltEpsd,
                            date = LocalDate.parse(it.ltRflYmd, dateFormatter),
                            one = it.tm1WnNo,
                            two = it.tm2WnNo,
                            three = it.tm3WnNo,
                            four = it.tm4WnNo,
                            five = it.tm5WnNo,
                            six = it.tm6WnNo,
                            bonus = it.bnsWnNo,
                            firstWinPrize = it.rnk1WnAmt,
                            firstWinners = it.rnk1WnNope
                        )
                    }
                if (newDrawings.isEmpty()) {
                    break
                }
                drawingRepository.saveAll(newDrawings)
                lastRound = newDrawings.last().round
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val LOTTO_API_URL = "https://www.dhlottery.co.kr/lt645/selectPstLt645InfoNew.do"
        private const val MAX_FETCH_ITERATIONS = 50
    }
}
