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
import org.slf4j.LoggerFactory
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
import org.springframework.web.client.RestClientResponseException
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
            log.error("엑셀 파일 처리 실패 (file={})", sanitizeForLog(fileName, MAX_LOG_FILENAME_LENGTH), e)
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

    /**
     * 누락된 최신 회차를 수집해 저장한다.
     *
     * - HTTP 수집은 트랜잭션 밖(NOT_SUPPORTED)에서 수행한다. 최대 50회 호출 동안 DB 커넥션을 잡지 않는다.
     * - 저장은 묶음마다 `drawingRepository.saveAll`(SimpleJpaRepository의 @Transactional, 별도 빈 프록시)로
     *   짧은 트랜잭션에서 커밋된다. 중간에 실패하면 앞 묶음만 남고 오름차순으로 이어진 앞부분만 저장된다.
     * - 캐시: beforeInvocation evict -> 묶음별 저장 커밋 -> finally에서 DrawingsChangedEvent 발행.
     *   실제 트랜잭션이 없으므로 리스너(fallbackExecution = true)가 즉시 재evict와 워밍업을 수행한다.
     * - 예외는 밖으로 던지지 않는다.
     */
    @CacheEvict(value = ["drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition"], allEntries = true, beforeInvocation = true)
    @Scheduled(cron = "0 0 12 ? * MON", zone = "Asia/Seoul")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun fetchAndStoreLottoNumbers() {
        val objectMapper = ObjectMapper().registerKotlinModule()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        var requestedRound: Int? = null
        var responseBody: String? = null
        var calls = 0
        var firstSavedRound: Int? = null
        var lastSavedRound: Int? = null
        var reachedLimit = false
        try {
            var lastRound = findLatestStoredRound()
            // 응답은 srchLtEpsd 주변 약 10개 회차(최신 회차에서 잘림). 새 회차가 없을 때까지 반복 호출해 누락분을 따라잡는다.
            for (attempt in 1..MAX_FETCH_ITERATIONS) {
                requestedRound = lastRound + 1
                responseBody = null
                val apiUrl = "$LOTTO_API_URL?srchDir=center&srchLtEpsd=$requestedRound"
                calls++
                responseBody = restTemplate.getForObject(apiUrl, String::class.java)
                val apiResponse = objectMapper.readValue(responseBody, LottoDrawingApiResponse::class.java)
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
                drawingRepository.saveAll(newDrawings) // 묶음 단위 트랜잭션 (커밋까지 여기서 끝남)
                if (firstSavedRound == null) firstSavedRound = newDrawings.first().round
                lastSavedRound = newDrawings.last().round
                lastRound = newDrawings.last().round
                if (attempt == MAX_FETCH_ITERATIONS) {
                    reachedLimit = true
                }
            }
            if (reachedLimit) {
                log.warn("당첨 번호 수집 반복 상한({}회)에 도달했습니다. 남은 회차는 다음 실행에서 이어서 수집합니다 (마지막 저장 회차={})",
                    MAX_FETCH_ITERATIONS, lastSavedRound)
            }
            logSavedRange(firstSavedRound, lastSavedRound, calls)
        } catch (e: Exception) {
            val body = e.responseBodyOrNull() ?: responseBody
            log.error("당첨 번호 수집 실패 (srchLtEpsd={}, 호출 횟수={}, 응답 앞부분={})",
                requestedRound, calls, body?.let { sanitizeForLog(it, MAX_LOG_BODY_LENGTH) }, e)
            logSavedRange(firstSavedRound, lastSavedRound, calls)
        } finally {
            eventPublisher.publishEvent(DrawingsChangedEvent(DrawingsChangedEvent.Source.SCHEDULER))
        }
    }

    private fun logSavedRange(firstSavedRound: Int?, lastSavedRound: Int?, calls: Int) {
        if (firstSavedRound != null) {
            log.info("당첨 번호 저장 완료 (회차 {}~{}, API 호출 {}회)", firstSavedRound, lastSavedRound, calls)
        } else {
            log.info("새로 저장한 회차 없음 (API 호출 {}회)", calls)
        }
    }

    private fun Exception.responseBodyOrNull(): String? =
        (this as? RestClientResponseException)?.responseBodyAsString?.takeIf { it.isNotEmpty() }

    /** 외부 입력을 로그에 남길 때 줄바꿈을 제거하고 길이를 제한한다 (로그 위조 방지). */
    private fun sanitizeForLog(value: String, maxLength: Int): String =
        value.take(maxLength).replace(Regex("[\\r\\n\\t]"), " ")

    companion object {
        private const val LOTTO_API_URL = "https://www.dhlottery.co.kr/lt645/selectPstLt645InfoNew.do"
        private const val MAX_FETCH_ITERATIONS = 50
        private const val MAX_LOG_BODY_LENGTH = 300
        private const val MAX_LOG_FILENAME_LENGTH = 200
        private val log = LoggerFactory.getLogger(DrawingService::class.java)
    }
}
