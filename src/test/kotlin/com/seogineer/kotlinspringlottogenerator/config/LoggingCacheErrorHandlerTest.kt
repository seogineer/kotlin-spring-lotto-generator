package com.seogineer.kotlinspringlottogenerator.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.seogineer.kotlinspringlottogenerator.support.LogCaptor
import com.seogineer.kotlinspringlottogenerator.support.atLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.cache.concurrent.ConcurrentMapCache

/**
 * A1: LoggingCacheErrorHandler 단위 테스트.
 * 4개 연산 모두 예외를 다시 던지지 않고 1줄에 캐시 이름(과 key)을 남긴다.
 * get/put은 WARN, evict/clear는 ERROR(+조치 문구). 예외는 클래스와 메시지 한 줄만 본문에 넣고 전체 스택은 DEBUG로 남긴다(L1).
 *
 * 로거 레벨은 테스트마다 명시한다. logback 기본(root DEBUG)과 Spring Boot 초기화 후(INFO)가 달라
 * 단독 실행과 전체 실행 결과가 달라지지 않게 하기 위함이다.
 */
class LoggingCacheErrorHandlerTest {

    private val handler = LoggingCacheErrorHandler()
    private val cache = ConcurrentMapCache("drawings")
    private val exception = IllegalStateException("Redis 연결 실패")

    private fun capture(level: Level, block: () -> Unit): List<ILoggingEvent> =
        LogCaptor.withLevel(LoggingCacheErrorHandler::class.java, level) {
            LogCaptor.capture(LoggingCacheErrorHandler::class.java) { assertDoesNotThrow(block) }
        }

    /** 운영 기본 레벨(INFO)에서 1줄만 남고 스택이 첨부되지 않는지 확인한다. */
    private fun singleEvent(level: Level, block: () -> Unit): ILoggingEvent {
        val events = capture(Level.INFO, block)
        assertEquals(1, events.size, "로그는 1줄이어야 함: ${events.map { it.formattedMessage }}")
        val event = events.atLevel(level).single()
        // 스택은 DEBUG로만 남기고(기본 INFO에서는 출력 안 됨), 본문에 예외 클래스와 메시지를 한 줄로 남긴다
        assertNull(event.throwableProxy)
        assertThat(event.formattedMessage).contains("error=${IllegalStateException::class.java.name}: Redis 연결 실패")
        return event
    }

    private fun singleWarn(block: () -> Unit): ILoggingEvent = singleEvent(Level.WARN, block)

    @Test
    fun 캐시_조회_오류는_삼키고_캐시_이름과_키를_WARN으로_남긴다() {
        val warn = singleWarn { handler.handleCacheGetError(exception, cache, "0:5") }

        assertThat(warn.formattedMessage).contains("조회 실패", "cache=drawings", "key=0:5")
        assertThat(warn.formattedMessage).doesNotContain(LoggingCacheErrorHandler.STALE_DATA_ACTION)
    }

    @Test
    fun 캐시_저장_오류는_삼키고_캐시_이름과_키를_WARN으로_남긴다() {
        val warn = singleWarn { handler.handleCachePutError(exception, cache, "0:1", listOf(1, 2, 3)) }

        assertThat(warn.formattedMessage).contains("저장 실패", "cache=drawings", "key=0:1")
        assertThat(warn.formattedMessage).doesNotContain(LoggingCacheErrorHandler.STALE_DATA_ACTION)
        // 캐시하려던 값은 로그에 남기지 않는다
        assertThat(warn.formattedMessage).doesNotContain("[1, 2, 3]")
    }

    @Test
    fun 저장할_값이_null이어도_예외를_던지지_않는다() {
        val warn = singleWarn { handler.handleCachePutError(exception, cache, "0:5", null) }

        assertThat(warn.formattedMessage).contains("cache=drawings", "key=0:5")
    }

    @Test
    fun 캐시_항목_삭제_오류는_삼키고_캐시_이름과_키와_조치_문구를_ERROR로_남긴다() {
        val error = singleEvent(Level.ERROR) { handler.handleCacheEvictError(exception, ConcurrentMapCache("mostFrequentNumbers"), "SimpleKey []") }

        assertThat(error.formattedMessage).contains("삭제 실패", "cache=mostFrequentNumbers", "key=SimpleKey []", LoggingCacheErrorHandler.STALE_DATA_ACTION)
    }

    @Test
    fun 캐시_전체_삭제_오류는_삼키고_캐시_이름과_조치_문구를_ERROR로_남긴다() {
        val error = singleEvent(Level.ERROR) { handler.handleCacheClearError(exception, ConcurrentMapCache("frequenciesPerPosition")) }

        assertThat(error.formattedMessage).contains("전체 삭제 실패", "cache=frequenciesPerPosition", LoggingCacheErrorHandler.STALE_DATA_ACTION)
        assertThat(error.formattedMessage).doesNotContain("key=")
    }

    @Test
    fun 조치_문구는_재시작_또는_캐시_키_삭제를_안내한다() {
        assertThat(LoggingCacheErrorHandler.STALE_DATA_ACTION).contains("이전 캐시 데이터", "재시작", "캐시 키 삭제")
    }

    // T5: 스택은 DEBUG에서만

    private val allHandlers: List<Pair<String, () -> Unit>> = listOf(
        "get" to { handler.handleCacheGetError(exception, cache, "0:5") },
        "put" to { handler.handleCachePutError(exception, cache, "0:5", "v") },
        "evict" to { handler.handleCacheEvictError(exception, cache, "0:5") },
        "clear" to { handler.handleCacheClearError(exception, cache) },
    )

    @Test
    fun INFO_레벨에서는_어떤_연산의_로그에도_스택이_첨부되지_않는다() {
        allHandlers.forEach { (op, call) ->
            val events = capture(Level.INFO, call)

            assertEquals(1, events.size, "$op: ${events.map { it.formattedMessage }}")
            events.forEach { assertNull(it.throwableProxy, "$op: 스택이 첨부되면 안 됨") }
            assertThat(events.atLevel(Level.DEBUG)).isEmpty()
        }
    }

    @Test
    fun DEBUG_레벨에서는_상세_스택이_DEBUG_이벤트에만_첨부된다() {
        allHandlers.forEach { (op, call) ->
            val events = capture(Level.DEBUG, call)

            assertEquals(2, events.size, "$op: ${events.map { it.formattedMessage }}")
            val debug = events.atLevel(Level.DEBUG).single()
            assertThat(debug.formattedMessage).contains("상세 스택")
            assertEquals(IllegalStateException::class.java.name, debug.throwableProxy!!.className, op)
            assertEquals("Redis 연결 실패", debug.throwableProxy!!.message, op)
            // 본문(WARN/ERROR)에는 여전히 스택이 없다
            val main = events.single { it.level != Level.DEBUG }
            assertThat(main.level).isIn(Level.WARN, Level.ERROR)
            assertNull(main.throwableProxy, op)
        }
    }

    @Test
    fun 예외_메시지의_개행은_본문에서_공백_한_칸으로_바뀐다() {
        val multiline = IllegalStateException("첫째 줄\r\n둘째 줄\n셋째 줄")

        val warn = capture(Level.INFO) { handler.handleCacheGetError(multiline, cache, "0:5") }.single()

        assertThat(warn.formattedMessage).contains("error=${IllegalStateException::class.java.name}: 첫째 줄 둘째 줄 셋째 줄")
        assertThat(warn.formattedMessage).doesNotContain("\n", "\r")
    }

    @Test
    fun 예외_메시지가_null이어도_예외_없이_클래스_이름을_남긴다() {
        val warn = capture(Level.INFO) { handler.handleCacheEvictError(IllegalStateException(), cache, "k") }.single()

        assertThat(warn.formattedMessage).contains("error=${IllegalStateException::class.java.name}")
    }

    // T6: 실패 누적 카운터

    @Test
    fun 실패_카운터는_0에서_시작해_네_가지_오류_처리마다_1씩_증가한다() {
        assertEquals(0L, handler.failureCount())

        LogCaptor.withLevel(LoggingCacheErrorHandler::class.java, Level.OFF) {
            allHandlers.forEachIndexed { index, (op, call) ->
                call()
                assertEquals(index + 1L, handler.failureCount(), op)
            }
            handler.handleCacheGetError(exception, cache, "again")
        }

        assertEquals(5L, handler.failureCount())
    }

    @Test
    fun 실패_카운터는_인스턴스마다_독립적이다() {
        LogCaptor.withLevel(LoggingCacheErrorHandler::class.java, Level.OFF) {
            handler.handleCacheClearError(exception, cache)
        }

        assertEquals(1L, handler.failureCount())
        assertEquals(0L, LoggingCacheErrorHandler().failureCount())
    }
}
