package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호1
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호10
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호2
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호3
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호4
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호5
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호6
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호7
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호8
import com.seogineer.kotlinspringlottogenerator.fixture.DrawingFixtures.Companion.당첨번호9
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.mockito.ArgumentCaptor
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockMultipartFile
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@ExtendWith(MockitoExtension::class)
class DrawingServiceTest {
    @Mock
    private lateinit var drawingRepository: DrawingRepository

    @Mock
    private lateinit var restTemplate: RestTemplate

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMocks
    private lateinit var drawingService: DrawingService

    @Test
    fun 역대_당첨_번호_조회() {
        val page = 0
        val size = 5
        val pageable: Pageable = PageRequest.of(page, size)
        val drawings = listOf(
            당첨번호1, 당첨번호2, 당첨번호3, 당첨번호4, 당첨번호5,
            당첨번호6, 당첨번호7, 당첨번호8, 당첨번호9, 당첨번호10
        )
        val drawingPage: Page<Drawing> = PageImpl(drawings, pageable, drawings.size.toLong())

        `when`(drawingRepository.getDrawings(pageable)).thenReturn(drawingPage)

        val result = drawingService.getDrawings(page, size)

        assertNotNull(result)
        assertEquals(10, result.content.size)
        assertEquals(10, result.totalElements)
        assertEquals(0, result.pageable.pageNumber)
        assertEquals(5, result.pageable.pageSize)
        assertEquals(1, result.content[0].round)
        assertEquals(LocalDate.of(2002, 12, 7), result.content[0].date)
        assertEquals(10, result.content[0].one)
        assertEquals(23, result.content[0].two)
        assertEquals(29, result.content[0].three)
        assertEquals(33, result.content[0].four)
        assertEquals(37, result.content[0].five)
        assertEquals(40, result.content[0].six)
        assertEquals(16, result.content[0].bonus)
        assertEquals(BigInteger("0"), result.content[0].firstWinPrize)
        assertEquals(0, result.content[0].firstWinners)
        verify(drawingRepository, times(1)).getDrawings(pageable)
    }

    @Test
    fun 가장_최근_회차_조회() {
        `when`(drawingRepository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(당첨번호3))

        val latestRound = drawingRepository.findTopByOrderByRoundDesc().get().round
        assertEquals(3, latestRound)
        verify(drawingRepository, times(1)).findTopByOrderByRoundDesc()
    }

    @Test
    fun DB에_저장된_최신_회차_조회() {
        `when`(drawingRepository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(당첨번호10))

        val latestStoredRound = drawingService.findLatestStoredRound()

        assertEquals(10, latestStoredRound)
        verify(drawingRepository, times(1)).findTopByOrderByRoundDesc()
    }

    @Test
    fun DB가_비어_있으면_최신_회차는_0() {
        `when`(drawingRepository.findTopByOrderByRoundDesc()).thenReturn(Optional.empty())

        val latestStoredRound = drawingService.findLatestStoredRound()

        assertEquals(0, latestStoredRound)
        verify(drawingRepository, times(1)).findTopByOrderByRoundDesc()
    }

    @Test
    fun 가장_많이_뽑힌_번호_조회() {
        val drawings = listOf(
            FrequencyResponse(1, 0, 30),
            FrequencyResponse(3, 0, 15),
            FrequencyResponse(2, 0, 10),
            FrequencyResponse(4, 0, 7),
            FrequencyResponse(5, 0, 7),
        )

        `when`(drawingRepository.getMostFrequentNumbers()).thenReturn(drawings)

        val result = drawingRepository.getMostFrequentNumbers()
        assertNotNull(result)
        assertEquals(5, result.size)
        assertEquals(1, result[0].number)
        assertEquals(30, result[0].frequency)
        assertEquals(3, result[1].number)
        assertEquals(15, result[1].frequency)
        assertEquals(2, result[2].number)
        assertEquals(10, result[2].frequency)
        assertEquals(4, result[3].number)
        assertEquals(7, result[3].frequency)
        assertEquals(5, result[4].number)
        assertEquals(7, result[4].frequency)
        val frequencies = result.map { it.frequency }
        Assertions.assertThat(frequencies).isSortedAccordingTo(Comparator.reverseOrder())
    }

    @Test
    fun 자리별_가장_많이_뽑힌_번호_조회() {
        val drawings = listOf(
            FrequencyResponse(1, 1, 30),
            FrequencyResponse(3, 1, 15),
            FrequencyResponse(2, 1, 10),
            FrequencyResponse(4, 1, 7),
            FrequencyResponse(5, 1, 7),
            FrequencyResponse(1, 2, 30),
            FrequencyResponse(3, 2, 15),
            FrequencyResponse(2, 2, 10),
            FrequencyResponse(4, 2, 7),
            FrequencyResponse(5, 2, 7),
            FrequencyResponse(1, 3, 30),
            FrequencyResponse(3, 3, 15),
            FrequencyResponse(2, 3, 10),
            FrequencyResponse(4, 3, 7),
            FrequencyResponse(5, 3, 7),
            FrequencyResponse(1, 4, 30),
            FrequencyResponse(3, 4, 15),
            FrequencyResponse(2, 4, 10),
            FrequencyResponse(4, 4, 7),
            FrequencyResponse(5, 4, 7),
            FrequencyResponse(1, 5, 30),
            FrequencyResponse(3, 5, 15),
            FrequencyResponse(2, 5, 10),
            FrequencyResponse(4, 5, 7),
            FrequencyResponse(5, 5, 7),
            FrequencyResponse(1, 6, 30),
            FrequencyResponse(3, 6, 15),
            FrequencyResponse(2, 6, 10),
            FrequencyResponse(4, 6, 7),
            FrequencyResponse(5, 6, 7),
        )

        `when`(drawingRepository.getTopNumbersPerPosition()).thenReturn(drawings)

        val result = drawingRepository.getTopNumbersPerPosition()

        assertNotNull(result)
        assertEquals(30, result.size)

        val groupedByPosition = result.groupBy { it.position }
        groupedByPosition.forEach { (_, frequencies) ->
            assertEquals(5, frequencies.size)
            val sortedFrequencies = frequencies.sortedByDescending { it.frequency }
            assertEquals(sortedFrequencies, frequencies)
        }

        val sortedResult = result.sortedWith(compareBy({ it.position }, { -it.frequency }))
        assertEquals(sortedResult, result)
    }

    // ---------------------------------------------------------------------
    // fetchAndStoreLottoNumbers() — 외부 API는 RestTemplate mock으로 대체한다 (실제 호출 없음)
    // ---------------------------------------------------------------------

    private fun fixture(name: String): String = ClassLoader.getSystemResource(name).readText()

    private fun srchLtEpsdOf(url: String): Int =
        Regex("srchLtEpsd=(\\d+)").find(url)!!.groupValues[1].toInt()

    /** restTemplate.getForObject(url, String::class.java)를 요청 회차(srchLtEpsd) 기반 응답으로 스텁한다. */
    private fun stubApi(responder: (Int) -> String) {
        `when`(restTemplate.getForObject(anyString(), eq(String::class.java))).thenAnswer { invocation ->
            responder(srchLtEpsdOf(invocation.getArgument(0)))
        }
    }

    private fun givenLatestStoredRound(round: Int?) {
        val latest = round?.let { Optional.of(drawingOf(it)) } ?: Optional.empty()
        `when`(drawingRepository.findTopByOrderByRoundDesc()).thenReturn(latest)
    }

    private fun drawingOf(round: Int) = Drawing(
        round, LocalDate.of(2002, 12, 7).plusWeeks((round - 1).toLong()),
        1, 2, 3, 4, 5, 6, 7, BigInteger.ZERO, 0
    )

    /** 도메인 유효(1~45, 상호 중복 없음) 번호를 가진 합성 항목. 날짜는 1회차(2002-12-07)부터 주 단위. */
    private fun syntheticItem(round: Int): String {
        val numbers = (0..5).map { (round + it * 7) % 45 + 1 }
        val ymd = LocalDate.of(2002, 12, 7).plusWeeks((round - 1).toLong())
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return """{"gmSqNo":5133,"ltEpsd":$round,"tm1WnNo":${numbers[0]},"tm2WnNo":${numbers[1]},""" +
            """"tm3WnNo":${numbers[2]},"tm4WnNo":${numbers[3]},"tm5WnNo":${numbers[4]},"tm6WnNo":${numbers[5]},""" +
            """"bnsWnNo":${(round + 45) % 45 + 1},"ltRflYmd":"$ymd","rnk1WnNope":10,"rnk1WnAmt":2000000000,""" +
            """"rnk2WnAmt":50000000,"excelRnk":"1등"}"""
    }

    /** 회차 목록을 내림차순 응답 JSON으로 만든다 (실제 API와 동일 순서). */
    private fun syntheticResponse(rounds: Iterable<Int>): String =
        """{"resultCode":null,"resultMessage":null,"data":{"list":[""" +
            rounds.sortedDescending().joinToString(",") { syntheticItem(it) } + "]}}"

    /**
     * 실측한 엔드포인트 윈도우 동작 모사: srchLtEpsd=e 주변 약 10건, 최신 회차(latest)에서 잘림,
     * 미발표 회차(e > latest)는 빈 list. (e=1 -> 1..10, e=1000 -> 995..1004)
     */
    private fun windowResponse(e: Int, latest: Int): String {
        if (e > latest) return fixture("lotto-api-response-empty.json")
        val start = maxOf(1, minOf(e - 5, latest - 9))
        val end = minOf(latest, start + 9)
        return syntheticResponse(start..end)
    }

    @Suppress("UNCHECKED_CAST")
    private fun saveAllCaptor(): ArgumentCaptor<Iterable<Drawing>> =
        ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<Drawing>>

    private fun captureRequestedRounds(times: Int): List<Int> {
        val urlCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(restTemplate, times(times)).getForObject(urlCaptor.capture(), eq(String::class.java))
        return urlCaptor.allValues.map { srchLtEpsdOf(it) }
    }

    private fun captureSavedBatches(times: Int): List<List<Drawing>> {
        val captor = saveAllCaptor()
        verify(drawingRepository, times(times)).saveAll(captor.capture())
        return captor.allValues.map { it.toList() }
    }

    @Test
    fun 스케줄러_DB가_비어_있으면_1회차부터_조회해_저장한다() {
        givenLatestStoredRound(null)
        stubApi { e -> windowResponse(e, latest = 10) }

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        val requested = captureRequestedRounds(2)
        assertEquals(listOf(1, 11), requested)
        val batches = captureSavedBatches(1)
        assertEquals((1..10).toList(), batches[0].map { it.round })
        assertEquals(LocalDate.of(2002, 12, 7), batches[0][0].date)
    }

    @Test
    fun 스케줄러_DB_최신_이후_회차만_오름차순으로_저장하고_빈_list에서_종료한다() {
        givenLatestStoredRound(1242)
        stubApi { e ->
            when (e) {
                1243 -> fixture("lotto-api-response-1243.json") // 실제 응답 발췌: 1244, 1243, 1242
                else -> fixture("lotto-api-response-empty.json") // 1245(미발표) 실제 응답
            }
        }

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        assertEquals(listOf(1243, 1245), captureRequestedRounds(2))
        val saved = captureSavedBatches(1)[0]
        assertEquals(listOf(1243, 1244), saved.map { it.round })

        val r1243 = saved[0]
        assertEquals(LocalDate.of(2026, 9, 26), r1243.date)
        assertEquals(listOf(9, 18, 24, 38, 43, 44), listOf(r1243.one, r1243.two, r1243.three, r1243.four, r1243.five, r1243.six))
        assertEquals(35, r1243.bonus)
        assertEquals(BigInteger("2592525282"), r1243.firstWinPrize)
        assertEquals(12, r1243.firstWinners)

        val r1244 = saved[1]
        assertEquals(LocalDate.of(2026, 10, 3), r1244.date)
        assertEquals(listOf(1, 13, 18, 26, 34, 38), listOf(r1244.one, r1244.two, r1244.three, r1244.four, r1244.five, r1244.six))
        assertEquals(25, r1244.bonus)
        assertEquals(BigInteger("1604686625"), r1244.firstWinPrize)
        assertEquals(18, r1244.firstWinners)
        verifySchedulerEventPublishedOnce()
    }

    @Test
    fun 스케줄러_여러_번_호출해_최신_회차까지_중복_없이_따라잡는다() {
        givenLatestStoredRound(1000)
        stubApi { e -> windowResponse(e, latest = 1012) }

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        // 1001 -> 996..1005 / 1006 -> 1001..1010 / 1011 -> 1003..1012(최신에서 잘림) / 1013 -> 빈 list
        assertEquals(listOf(1001, 1006, 1011, 1013), captureRequestedRounds(4))
        val batches = captureSavedBatches(3).map { batch -> batch.map { it.round } }
        assertEquals(listOf((1001..1005).toList(), (1006..1010).toList(), (1011..1012).toList()), batches)
        val allSaved = batches.flatten()
        assertEquals((1001..1012).toList(), allSaved)
        assertEquals(allSaved.size, allSaved.toSet().size)
    }

    @Test
    fun 스케줄러_응답_최신_회차가_DB_최신과_같으면_저장하지_않는다() {
        givenLatestStoredRound(1244)
        stubApi { fixture("lotto-api-response-1243.json") } // 최신 1244까지만 포함

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        assertEquals(listOf(1245), captureRequestedRounds(1))
        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
    }

    @Test
    fun 스케줄러_빈_list_응답이면_저장하지_않는다() {
        givenLatestStoredRound(1244)
        stubApi { fixture("lotto-api-response-empty.json") }

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        assertEquals(listOf(1245), captureRequestedRounds(1))
        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
        verifySchedulerEventPublishedOnce()
    }

    @Test
    fun 스케줄러_data가_null이면_저장하지_않는다() {
        givenLatestStoredRound(1244)
        stubApi { """{"resultCode":null,"resultMessage":null,"data":null}""" }

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        assertEquals(listOf(1245), captureRequestedRounds(1))
        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
    }

    @Test
    fun 스케줄러_HTML_응답이면_예외를_삼키고_저장하지_않는다() {
        givenLatestStoredRound(1242)
        stubApi { "<!DOCTYPE html><html><head><title>동행복권</title></head><body>점검 중</body></html>" }

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        captureRequestedRounds(1)
        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
        verifySchedulerEventPublishedOnce()
    }

    @Test
    fun 스케줄러_RestClientException이_발생하면_예외를_삼키고_저장하지_않는다() {
        givenLatestStoredRound(1242)
        stubApi { throw ResourceAccessException("Read timed out") }

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        captureRequestedRounds(1)
        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
        verifySchedulerEventPublishedOnce()
    }

    @Test
    fun 스케줄러_계속_새_회차가_오더라도_호출은_50회를_넘지_않는다() {
        givenLatestStoredRound(1000)
        stubApi { e -> syntheticResponse(e until e + 10) } // 최신 상한 없이 항상 새 회차 10건

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        val requested = captureRequestedRounds(50)
        assertEquals((0 until 50).map { 1001 + it * 10 }, requested)
        val batches = captureSavedBatches(50)
        assertEquals((1001..1500).toList(), batches.flatten().map { it.round })
        verifyNoMoreInteractions(restTemplate)
    }

    @Test
    fun 스케줄러_두_번째_호출이_실패하면_첫_묶음만_저장한다() {
        givenLatestStoredRound(1000)
        stubApi { e ->
            when (e) {
                1001 -> windowResponse(e, latest = 1012) // 996..1005
                else -> throw RestClientException("503 Service Unavailable")
            }
        }

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        assertEquals(listOf(1001, 1006), captureRequestedRounds(2))
        val batches = captureSavedBatches(1)
        assertEquals((1001..1005).toList(), batches[0].map { it.round })
        verifySchedulerEventPublishedOnce()
    }

    // ---------------------------------------------------------------------
    // DrawingsChangedEvent 발행 — 캐시 워밍업(DrawingCacheWarmer) 트리거
    // ---------------------------------------------------------------------

    private fun verifySchedulerEventPublishedOnce() {
        verify(eventPublisher, times(1)).publishEvent(DrawingsChangedEvent(DrawingsChangedEvent.Source.SCHEDULER))
        verifyNoMoreInteractions(eventPublisher)
    }

    private fun verifyExcelUploadEventPublishedOnce() {
        verify(eventPublisher, times(1)).publishEvent(DrawingsChangedEvent(DrawingsChangedEvent.Source.EXCEL_UPLOAD))
        verifyNoMoreInteractions(eventPublisher)
    }

    @Test
    fun 엑셀_업로드_성공하면_EXCEL_UPLOAD_이벤트를_1회_발행한다() {
        val file = MockMultipartFile(
            "file", "excel.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ClassLoader.getSystemResource("excel.xlsx").readBytes()
        )

        assertDoesNotThrow { drawingService.readExcelFile(file) }

        val saved = captureSavedBatches(1)[0]
        Assertions.assertThat(saved).isNotEmpty
        verifyExcelUploadEventPublishedOnce()
    }

    @Test
    fun 엑셀_업로드_확장자가_xlsx가_아니어서_실패해도_EXCEL_UPLOAD_이벤트를_1회_발행한다() {
        val file = MockMultipartFile(
            "file", "invalid.txt", "text/plain",
            ClassLoader.getSystemResource("invalid.txt").readBytes()
        )

        assertThrows<IllegalArgumentException> { drawingService.readExcelFile(file) }

        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
        verifyExcelUploadEventPublishedOnce()
    }

    @Test
    fun 엑셀_업로드_파일_파싱에_실패해도_EXCEL_UPLOAD_이벤트를_1회_발행한다() {
        val file = MockMultipartFile("file", "broken.xlsx", "application/octet-stream", "엑셀 아님".toByteArray())

        assertThrows<RuntimeException> { drawingService.readExcelFile(file) }

        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
        verifyExcelUploadEventPublishedOnce()
    }

    @Test
    fun 스케줄러_새_회차가_없어도_SCHEDULER_이벤트를_1회_발행한다() {
        givenLatestStoredRound(1244)
        stubApi { fixture("lotto-api-response-1243.json") } // 최신 1244까지만 포함 -> 새 회차 없음

        assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }

        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
        verifySchedulerEventPublishedOnce()
    }

    @Test
    fun 스케줄러_DB_조회_전에_SCHEDULER_이벤트를_먼저_발행한다() {
        givenLatestStoredRound(1244)
        stubApi { fixture("lotto-api-response-empty.json") }

        drawingService.fetchAndStoreLottoNumbers()

        val inOrder = inOrder(eventPublisher, drawingRepository, restTemplate)
        inOrder.verify(eventPublisher).publishEvent(DrawingsChangedEvent(DrawingsChangedEvent.Source.SCHEDULER))
        inOrder.verify(drawingRepository).findTopByOrderByRoundDesc()
        inOrder.verify(restTemplate).getForObject(anyString(), eq(String::class.java))
    }
}
