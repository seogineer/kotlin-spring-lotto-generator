package com.seogineer.kotlinspringlottogenerator.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.support.LogCaptor
import com.seogineer.kotlinspringlottogenerator.support.atLevel
import com.seogineer.kotlinspringlottogenerator.support.messages
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Optional

/**
 * B1~B4, C4, D1(Mockito): DrawingService의 SLF4J 로그와 실패 경로.
 * 외부 API는 RestTemplate mock으로 대체한다. 로그는 logback ListAppender로 수집하고,
 * 전체 문자열 일치 대신 핵심 값(srchLtEpsd, 호출 횟수, 회차 범위, 파일명)의 포함 여부로 검증한다.
 * 시나리오: _workspace/08_developer_followup_changes.md 5절.
 */
@ExtendWith(MockitoExtension::class)
class DrawingServiceLoggingTest {
    @Mock
    private lateinit var drawingRepository: DrawingRepository

    @Mock
    private lateinit var restTemplate: RestTemplate

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMocks
    private lateinit var drawingService: DrawingService

    // ----- 헬퍼 -----

    private fun captureLogs(block: () -> Unit): List<ILoggingEvent> =
        LogCaptor.capture(DrawingService::class.java) { assertDoesNotThrow(block) }

    private fun fixture(name: String): String = ClassLoader.getSystemResource(name).readText()

    private fun srchLtEpsdOf(url: String): Int = Regex("srchLtEpsd=(\\d+)").find(url)!!.groupValues[1].toInt()

    private fun stubApi(responder: (Int) -> String) {
        `when`(restTemplate.getForObject(anyString(), eq(String::class.java))).thenAnswer { invocation ->
            responder(srchLtEpsdOf(invocation.getArgument(0)))
        }
    }

    private fun givenLatestStoredRound(round: Int) {
        `when`(drawingRepository.findTopByOrderByRoundDesc()).thenReturn(
            Optional.of(Drawing(round, LocalDate.of(2002, 12, 7), 1, 2, 3, 4, 5, 6, 7, BigInteger.ZERO, 0))
        )
    }

    /** 도메인 유효(1~45, 상호 중복 없음) 합성 항목. sixth를 지정하면 6번째 번호를 덮어쓴다 (init 위반 재현용). */
    private fun syntheticItem(round: Int, sixth: Int? = null): String {
        val numbers = (0..5).map { (round + it * 7) % 45 + 1 }.toMutableList()
        if (sixth != null) numbers[5] = sixth
        val ymd = LocalDate.of(2002, 12, 7).plusWeeks((round - 1).toLong()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return """{"ltEpsd":$round,"tm1WnNo":${numbers[0]},"tm2WnNo":${numbers[1]},"tm3WnNo":${numbers[2]},""" +
            """"tm4WnNo":${numbers[3]},"tm5WnNo":${numbers[4]},"tm6WnNo":${numbers[5]},"bnsWnNo":${(round + 45) % 45 + 1},""" +
            """"ltRflYmd":"$ymd","rnk1WnNope":10,"rnk1WnAmt":2000000000}"""
    }

    private fun syntheticResponse(rounds: Iterable<Int>, invalidRound: Int? = null): String =
        """{"resultCode":null,"resultMessage":null,"data":{"list":[""" +
            rounds.sortedDescending().joinToString(",") { syntheticItem(it, if (it == invalidRound) 46 else null) } + "]}}"

    private val emptyResponse get() = fixture("lotto-api-response-empty.json")

    private fun failureLog(events: List<ILoggingEvent>): ILoggingEvent {
        val error = events.atLevel(Level.ERROR).single()
        assertThat(error.formattedMessage).contains("당첨 번호 수집 실패")
        // 예외는 포맷 인자가 아니라 throwable로 전달되어 스택이 남는다
        assertNotNull(error.throwableProxy, "ERROR 로그에 예외 스택이 없음")
        return error
    }

    /** "응답 앞부분=" 뒤 값 (메시지 끝의 ")" 제외). */
    private fun bodyPart(message: String): String = message.substringAfter("응답 앞부분=").removeSuffix(")")

    private fun verifySchedulerEventPublishedOnce() {
        verify(eventPublisher, times(1)).publishEvent(DrawingsChangedEvent(DrawingsChangedEvent.Source.SCHEDULER))
        verifyNoMoreInteractions(eventPublisher)
    }

    @Suppress("UNCHECKED_CAST")
    private fun captureSavedBatches(times: Int): List<List<Int>> {
        val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<Drawing>>
        verify(drawingRepository, times(times)).saveAll(captor.capture())
        return captor.allValues.map { batch -> batch.map { it.round } }
    }

    // ----- B1: 스케줄러 실패 로그 -----

    @Test
    fun 스케줄러_HTML_응답이면_ERROR에_srchLtEpsd와_응답_앞부분을_남긴다() {
        givenLatestStoredRound(1242)
        stubApi { "<!DOCTYPE html><html><head><title>동행복권</title></head><body>점검 중</body></html>" }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        val error = failureLog(events)
        assertThat(error.formattedMessage).contains("srchLtEpsd=1243", "호출 횟수=1", "응답 앞부분=<!DOCTYPE html>")
        // 실패 뒤에도 그때까지 저장한 범위(없음)를 INFO로 남긴다
        assertThat(events.messages(Level.INFO)).anyMatch { it.contains("새로 저장한 회차 없음") && it.contains("API 호출 1회") }
        verifySchedulerEventPublishedOnce()
    }

    @Test
    fun 스케줄러_HTTP_오류면_예외에_담긴_응답_본문_앞부분을_남긴다() {
        givenLatestStoredRound(1242)
        val body = "서비스 점검 중입니다 (503)"
        stubApi {
            throw HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY,
                body.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8
            )
        }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        val error = failureLog(events)
        assertThat(error.formattedMessage).contains("srchLtEpsd=1243", "호출 횟수=1")
        assertEquals(body, bodyPart(error.formattedMessage))
        assertEquals(HttpServerErrorException.ServiceUnavailable::class.java.name, error.throwableProxy.className)
        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
    }

    @Test
    fun 스케줄러_응답_본문은_300자에서_잘린다() {
        givenLatestStoredRound(1242)
        stubApi { "x".repeat(400) + "TAIL" } // JSON 파싱 실패 -> 마지막 응답 본문을 남긴다

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        val part = bodyPart(failureLog(events).formattedMessage)
        assertEquals(300, part.length)
        assertEquals("x".repeat(300), part)
        assertThat(part).doesNotContain("TAIL")
    }

    @Test
    fun 스케줄러_응답_본문의_줄바꿈과_탭은_공백으로_바뀐다() {
        givenLatestStoredRound(1242)
        stubApi { "<html>\r\n<body>\n점검\t중</body>\n가짜 로그 줄 ERROR</html>" }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        val message = failureLog(events).formattedMessage
        assertThat(message).doesNotContain("\r", "\n", "\t")
        assertThat(bodyPart(message)).isEqualTo("<html>  <body> 점검 중</body> 가짜 로그 줄 ERROR</html>")
    }

    @Test
    fun 스케줄러_로그에는_API_URL을_남기지_않는다() {
        givenLatestStoredRound(1242)
        stubApi { "<!DOCTYPE html>" }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        events.forEach { event ->
            assertThat(event.formattedMessage).doesNotContain("https://", "http://", "dhlottery", "srchDir=")
        }
        assertThat(failureLog(events).formattedMessage).contains("srchLtEpsd=1243")
    }

    @Test
    fun 스케줄러_두_번째_호출이_실패하면_요청_회차와_호출_횟수와_앞_묶음_저장_범위를_남긴다() {
        givenLatestStoredRound(1000)
        stubApi { e ->
            when (e) {
                1001 -> syntheticResponse(1001..1005)
                else -> throw HttpServerErrorException(HttpStatus.BAD_GATEWAY)
            }
        }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        val error = failureLog(events)
        assertThat(error.formattedMessage).contains("srchLtEpsd=1006", "호출 횟수=2")
        // 본문이 없는 HTTP 오류는 이번 호출의 응답이 없으므로 이전 응답 본문을 섞지 않는다
        assertEquals("null", bodyPart(error.formattedMessage))
        assertThat(events.messages(Level.INFO)).anyMatch { it.contains("회차 1001~1005") && it.contains("API 호출 2회") }
        assertEquals(listOf((1001..1005).toList()), captureSavedBatches(1))
        verifySchedulerEventPublishedOnce()
    }

    // ----- B2: 성공 INFO -----

    @Test
    fun 스케줄러_성공하면_저장_회차_범위와_호출_횟수를_INFO로_남긴다() {
        givenLatestStoredRound(1242)
        stubApi { e -> if (e == 1243) fixture("lotto-api-response-1243.json") else emptyResponse }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        val info = events.messages(Level.INFO).single()
        assertThat(info).contains("당첨 번호 저장 완료", "회차 1243~1244", "API 호출 2회")
        assertThat(events.atLevel(Level.WARN)).isEmpty()
        assertThat(events.atLevel(Level.ERROR)).isEmpty()
    }

    @Test
    fun 스케줄러_새_회차가_없으면_새로_저장한_회차_없음을_INFO로_남긴다() {
        givenLatestStoredRound(1244)
        stubApi { fixture("lotto-api-response-1243.json") }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        val info = events.messages(Level.INFO).single()
        assertThat(info).contains("새로 저장한 회차 없음", "API 호출 1회")
        assertThat(events.atLevel(Level.WARN)).isEmpty()
        assertThat(events.atLevel(Level.ERROR)).isEmpty()
    }

    // ----- B3: 반복 상한 WARN -----

    @Test
    fun 스케줄러_50번째_호출에도_새_회차를_저장하면_반복_상한_WARN을_남긴다() {
        givenLatestStoredRound(1000)
        stubApi { e -> syntheticResponse(e until e + 10) } // 항상 새 회차 10건

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        val warn = events.messages(Level.WARN).single()
        assertThat(warn).contains("반복 상한(50회)", "마지막 저장 회차=1500")
        assertThat(events.messages(Level.INFO)).anyMatch { it.contains("회차 1001~1500") && it.contains("API 호출 50회") }
        verify(restTemplate, times(50)).getForObject(anyString(), eq(String::class.java))
    }

    @Test
    fun 스케줄러_50번째_호출이_빈_응답이면_반복_상한_WARN을_남기지_않는다() {
        givenLatestStoredRound(1000)
        // 49번 호출로 1001~1490을 저장하고 50번째(1491) 호출에서 새 회차가 없어 끝난다
        stubApi { e -> if (e <= 1490) syntheticResponse(e until e + 10) else emptyResponse }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        assertThat(events.atLevel(Level.WARN)).isEmpty()
        assertThat(events.messages(Level.INFO)).anyMatch { it.contains("회차 1001~1490") && it.contains("API 호출 50회") }
        verify(restTemplate, times(50)).getForObject(anyString(), eq(String::class.java))
    }

    @Test
    fun 스케줄러_49번째_호출에서_끝나면_반복_상한_WARN을_남기지_않는다() {
        givenLatestStoredRound(1000)
        stubApi { e -> if (e <= 1480) syntheticResponse(e until e + 10) else emptyResponse }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        assertThat(events.atLevel(Level.WARN)).isEmpty()
        assertThat(events.messages(Level.INFO)).anyMatch { it.contains("회차 1001~1480") && it.contains("API 호출 49회") }
        verify(restTemplate, times(49)).getForObject(anyString(), eq(String::class.java))
    }

    // ----- B4: 엑셀 실패 ERROR -----

    @Test
    fun 엑셀_파싱에_실패하면_파일명과_예외_스택을_ERROR로_남긴다() {
        val file = MockMultipartFile("file", "broken.xlsx", "application/octet-stream", "엑셀 아님".toByteArray())

        val events = LogCaptor.capture(DrawingService::class.java) {
            val e = assertThrows<RuntimeException> { drawingService.readExcelFile(file) }
            assertEquals("엑셀 파일 처리 중 오류 발생", e.message)
        }

        val error = events.atLevel(Level.ERROR).single()
        assertThat(error.formattedMessage).contains("엑셀 파일 처리 실패", "file=broken.xlsx")
        assertNotNull(error.throwableProxy)
        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
    }

    @Test
    fun 엑셀_파일명의_줄바꿈은_공백으로_바뀐다() {
        val file = MockMultipartFile("file", "bad\r\nINFO 가짜.xlsx", "application/octet-stream", "엑셀 아님".toByteArray())

        val events = LogCaptor.capture(DrawingService::class.java) {
            assertThrows<RuntimeException> { drawingService.readExcelFile(file) }
        }

        val message = events.atLevel(Level.ERROR).single().formattedMessage
        assertThat(message).doesNotContain("\r", "\n")
        assertThat(message).contains("file=bad  INFO 가짜.xlsx")
    }

    @Test
    fun 엑셀_파일명은_200자에서_잘린다() {
        val longName = "a".repeat(300) + ".xlsx"
        val file = MockMultipartFile("file", longName, "application/octet-stream", "엑셀 아님".toByteArray())

        val events = LogCaptor.capture(DrawingService::class.java) {
            assertThrows<RuntimeException> { drawingService.readExcelFile(file) }
        }

        val loggedName = events.atLevel(Level.ERROR).single().formattedMessage.substringAfter("file=").removeSuffix(")")
        assertEquals("a".repeat(200), loggedName)
    }

    @Test
    fun 엑셀_확장자_오류는_ERROR_로그_없이_IllegalArgumentException을_던진다() {
        val file = MockMultipartFile("file", "invalid.txt", "text/plain", "invalid".toByteArray())

        val events = LogCaptor.capture(DrawingService::class.java) {
            assertThrows<IllegalArgumentException> { drawingService.readExcelFile(file) }
        }

        assertThat(events.atLevel(Level.ERROR)).isEmpty()
    }

    // ----- C4: DB 최신 회차 조회 실패 -----

    @Test
    fun 스케줄러_DB_최신_회차_조회가_실패해도_예외를_던지지_않고_이벤트를_1회_발행한다() {
        `when`(drawingRepository.findTopByOrderByRoundDesc()).thenThrow(DataAccessResourceFailureException("DB 연결 실패"))

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        val error = failureLog(events)
        assertThat(error.formattedMessage).contains("srchLtEpsd=null", "호출 횟수=0")
        assertEquals(DataAccessResourceFailureException::class.java.name, error.throwableProxy.className)
        verifyNoInteractions(restTemplate)
        verify(drawingRepository, never()).saveAll(anyIterable<Drawing>())
        verifySchedulerEventPublishedOnce()
    }

    // ----- D1(Mockito): init 위반 묶음 -----

    @Test
    fun 스케줄러_두_번째_묶음에_번호_46이_있으면_그_묶음은_저장하지_않고_앞_묶음만_남긴다() {
        givenLatestStoredRound(1000)
        stubApi { e ->
            when (e) {
                1001 -> syntheticResponse(1001..1005)
                1006 -> syntheticResponse(1006..1010, invalidRound = 1008)
                else -> emptyResponse
            }
        }

        val events = captureLogs { drawingService.fetchAndStoreLottoNumbers() }

        // 1006~1010 묶음은 1008의 init 위반으로 통째로 저장되지 않는다 (유효한 1006, 1007도 저장 안 됨)
        assertEquals(listOf((1001..1005).toList()), captureSavedBatches(1))
        verify(restTemplate, times(2)).getForObject(anyString(), eq(String::class.java))
        val error = failureLog(events)
        assertThat(error.formattedMessage).contains("srchLtEpsd=1006", "호출 횟수=2")
        assertThat(bodyPart(error.formattedMessage)).startsWith("""{"resultCode":null""").contains("\"ltEpsd\":1010")
        assertEquals(IllegalArgumentException::class.java.name, error.throwableProxy.className)
        assertThat(events.messages(Level.INFO)).anyMatch { it.contains("회차 1001~1005") && it.contains("API 호출 2회") }
        verifySchedulerEventPublishedOnce()
    }
}
