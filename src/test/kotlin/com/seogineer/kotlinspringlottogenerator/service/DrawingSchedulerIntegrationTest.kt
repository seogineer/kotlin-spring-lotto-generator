package com.seogineer.kotlinspringlottogenerator.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import com.seogineer.kotlinspringlottogenerator.entity.DrawingRepository
import com.seogineer.kotlinspringlottogenerator.support.LogCaptor
import com.seogineer.kotlinspringlottogenerator.support.atLevel
import com.seogineer.kotlinspringlottogenerator.support.messages
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.cache.interceptor.SimpleKey
import org.springframework.context.annotation.Bean
import org.springframework.data.domain.Page
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.persistence.EntityManagerFactory
import com.zaxxer.hikari.HikariDataSource
import org.hibernate.engine.spi.SessionImplementor
import org.springframework.orm.jpa.EntityManagerHolder
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource

/**
 * C1~C3, D1(통합): 스케줄러 fetchAndStoreLottoNumbers를 실제 프록시(캐시 + 트랜잭션) + H2로 실행한다.
 * - 외부 API는 @MockBean RestTemplate으로 대체한다 (실제 호출 없음).
 * - 캐시는 테스트 전용 @EnableCaching + ConcurrentMapCacheManager로 켠다.
 * - 테스트 메서드에 @Transactional을 붙이지 않는다 (트랜잭션 유무 자체가 검증 대상).
 * - 매 테스트 DB를 비우고 1회차만 넣어 둔다. 응답: srchLtEpsd=2 -> 2~4회차, 5 -> 5~6회차, 7 -> 빈 list.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@RecordApplicationEvents
@DirtiesContext
class DrawingSchedulerIntegrationTest {

    @TestConfiguration
    @EnableCaching
    class CacheTestConfig {
        @Bean
        fun cacheManager(): CacheManager =
            ConcurrentMapCacheManager("drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition")
    }

    @MockBean
    private lateinit var restTemplate: RestTemplate

    @Autowired
    private lateinit var drawingService: DrawingService

    @Autowired
    private lateinit var drawingRepository: DrawingRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    private val cacheNames = listOf("drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition")

    /** HTTP 호출(RestTemplate answer) 시점마다 관찰한 상태. */
    private data class CallObservation(
        val srchLtEpsd: Int,
        val actualTransactionActive: Boolean,
        val connectionBound: Boolean,
        val entityManagerBound: Boolean,
        val entityManagerPhysicallyConnected: Boolean,
        val activePoolConnections: Int,
        val committedRows: Int,
        val eventsSoFar: Long,
    )

    private val observations = mutableListOf<CallObservation>()

    @BeforeEach
    fun setUp() {
        drawingRepository.deleteAll()
        drawingRepository.save(drawingOf(1))
        cacheNames.forEach { cacheManager.getCache(it)!!.clear() }
        observations.clear()
    }

    // ----- 응답/데이터 헬퍼 -----

    private fun drawingOf(round: Int) = Drawing(
        round, LocalDate.of(2002, 12, 7).plusWeeks((round - 1).toLong()),
        (round + 0) % 45 + 1, (round + 7) % 45 + 1, (round + 14) % 45 + 1,
        (round + 21) % 45 + 1, (round + 28) % 45 + 1, (round + 35) % 45 + 1,
        (round + 42) % 45 + 1, BigInteger.ZERO, 0
    )

    private fun item(round: Int, sixth: Int? = null): String {
        val numbers = (0..5).map { (round + it * 7) % 45 + 1 }.toMutableList()
        if (sixth != null) numbers[5] = sixth
        val ymd = LocalDate.of(2002, 12, 7).plusWeeks((round - 1).toLong()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return """{"ltEpsd":$round,"tm1WnNo":${numbers[0]},"tm2WnNo":${numbers[1]},"tm3WnNo":${numbers[2]},""" +
            """"tm4WnNo":${numbers[3]},"tm5WnNo":${numbers[4]},"tm6WnNo":${numbers[5]},"bnsWnNo":${(round + 42) % 45 + 1},""" +
            """"ltRflYmd":"$ymd","rnk1WnNope":10,"rnk1WnAmt":2000000000}"""
    }

    private fun response(rounds: IntRange, invalidRound: Int? = null): String =
        """{"resultCode":null,"resultMessage":null,"data":{"list":[""" +
            rounds.reversed().joinToString(",") { item(it, if (it == invalidRound) 46 else null) } + "]}}"

    private val emptyResponse = """{"resultCode":null,"resultMessage":null,"data":{"list":[]}}"""

    /** 스레드에 바인딩된 EntityManager(있다면)의 Hibernate 세션이 실제 JDBC 커넥션을 잡고 있는지. */
    private fun boundEntityManagerPhysicallyConnected(): Boolean {
        val holder = TransactionSynchronizationManager.getResource(entityManagerFactory) as? EntityManagerHolder ?: return false
        return holder.entityManager.unwrap(SessionImplementor::class.java).jdbcCoordinator.logicalConnection.isPhysicallyConnected
    }

    /** 커넥션 풀(Hikari)에서 대여 중인 커넥션 수. */
    private fun activePoolConnections(): Int = dataSource.unwrap(HikariDataSource::class.java).hikariPoolMXBean.activeConnections

    /**
     * 커밋된 행 수. 호출 스레드의 트랜잭션 동기화에 커넥션이 묶이지 않도록 다른 스레드에서 autocommit 커넥션으로 읽는다
     * (같은 스레드에서 JdbcTemplate을 쓰면 NOT_SUPPORTED의 동기화 범위에 커넥션이 바인딩되어 관찰이 오염된다).
     */
    private fun committedRowsFromAnotherThread(): Int =
        CompletableFuture.supplyAsync { jdbcTemplate.queryForObject("select count(*) from drawing", Int::class.java)!! }.get()

    private fun schedulerEventCount(): Long = applicationEvents.stream(DrawingsChangedEvent::class.java).count()

    /** srchLtEpsd별 응답을 돌려주며 호출 시점의 트랜잭션/커밋/이벤트 상태를 기록한다. */
    private fun stubApi(responder: (Int) -> String) {
        given(restTemplate.getForObject(anyString(), eq(String::class.java))).willAnswer { invocation ->
            val e = Regex("srchLtEpsd=(\\d+)").find(invocation.getArgument<String>(0))!!.groupValues[1].toInt()
            observations += CallObservation(
                srchLtEpsd = e,
                actualTransactionActive = TransactionSynchronizationManager.isActualTransactionActive(),
                connectionBound = TransactionSynchronizationManager.getResource(dataSource) != null,
                entityManagerBound = TransactionSynchronizationManager.getResource(entityManagerFactory) != null,
                entityManagerPhysicallyConnected = boundEntityManagerPhysicallyConnected(),
                activePoolConnections = activePoolConnections(),
                committedRows = committedRowsFromAnotherThread(),
                eventsSoFar = schedulerEventCount(),
            )
            responder(e)
        }
    }

    private fun storedRounds(): List<Int> = drawingRepository.findAll().map { it.round }.sorted()

    private fun cache(name: String) = cacheManager.getCache(name)!!

    @Suppress("UNCHECKED_CAST")
    private fun cachedPage(key: String): Page<Drawing>? = cache("drawings").get(key)?.get() as Page<Drawing>?

    @Suppress("UNCHECKED_CAST")
    private fun cachedStats(name: String): List<FrequencyResponse>? = cache(name).get(SimpleKey.EMPTY)?.get() as List<FrequencyResponse>?

    private fun runScheduler(): List<ILoggingEvent> =
        LogCaptor.capture(DrawingService::class.java, DrawingCacheWarmer::class.java) {
            assertDoesNotThrow { drawingService.fetchAndStoreLottoNumbers() }
        }

    private fun assertSchedulerEventPublishedExactlyOnce() {
        val events = applicationEvents.stream(DrawingsChangedEvent::class.java).toList()
        assertEquals(listOf(DrawingsChangedEvent(DrawingsChangedEvent.Source.SCHEDULER)), events)
    }

    private fun assertNoGaps(rounds: List<Int>) {
        assertEquals((1..rounds.maxOrNull()!!).toList(), rounds, "회차에 공백이 있음: $rounds")
    }

    // ----- C1: HTTP 호출 중 트랜잭션 없음 + 묶음별 커밋 -----

    @Test
    fun 스케줄러는_HTTP_호출_동안_실제_트랜잭션이_없다() {
        stubApi { e -> when (e) { 2 -> response(2..4); 5 -> response(5..6); else -> emptyResponse } }

        runScheduler()

        assertEquals(listOf(2, 5, 7), observations.map { it.srchLtEpsd })
        observations.forEach { o ->
            assertFalse(o.actualTransactionActive, "srchLtEpsd=${o.srchLtEpsd} 호출 중 실제 트랜잭션이 활성화됨")
            // 트랜잭션 매니저가 잡은 JDBC ConnectionHolder도 없다
            assertFalse(o.connectionBound, "srchLtEpsd=${o.srchLtEpsd} 호출 중 JDBC ConnectionHolder가 바인딩됨")
        }
        assertEquals((1..6).toList(), storedRounds())
    }

    /**
     * 09 이슈 1 회귀 테스트: HTTP 호출 동안 DB 커넥션을 대여하지 않는다.
     * - NOT_SUPPORTED도 기본 설정(SYNCHRONIZATION_ALWAYS)에서는 트랜잭션 동기화 범위를 연다.
     * - 그 안에서 트랜잭션 없이 실행되는 리포지토리 쿼리가 있으면 공유 EntityManager가 스레드에 바인딩되어
     *   (DELAYED_ACQUISITION_AND_HOLD) 메서드가 끝날 때까지 커넥션을 쥔다.
     * - 수정: DrawingRepository.findTopByOrderByRoundDesc()에 @Transactional(readOnly = true)를 붙여 짧은 트랜잭션으로 실행한다.
     */
    @Test
    fun 스케줄러는_HTTP_호출_동안_DB_커넥션을_대여하지_않는다() {
        stubApi { e -> when (e) { 2 -> response(2..4); 5 -> response(5..6); else -> emptyResponse } }

        runScheduler()

        observations.forEach { o ->
            assertFalse(o.entityManagerBound, "srchLtEpsd=${o.srchLtEpsd} 호출 중 EntityManager가 바인딩됨")
            assertFalse(o.entityManagerPhysicallyConnected, "srchLtEpsd=${o.srchLtEpsd} 호출 중 세션이 JDBC 커넥션을 쥠")
            assertEquals(0, o.activePoolConnections, "srchLtEpsd=${o.srchLtEpsd} 호출 중 대여된 풀 커넥션이 있음")
        }
    }

    @Test
    fun 스케줄러는_묶음마다_커밋해_다음_HTTP_호출_시점에_앞_묶음이_이미_커밋되어_있다() {
        stubApi { e -> when (e) { 2 -> response(2..4); 5 -> response(5..6); else -> emptyResponse } }

        runScheduler()

        // 1번째 호출 전: 1회차만 / 2번째 호출 전: 2~4 커밋 / 3번째 호출 전: 5~6 커밋
        assertEquals(listOf(1, 4, 6), observations.map { it.committedRows })
    }

    @Test
    fun 바깥_트랜잭션_안에서_호출해도_HTTP_호출_중에는_트랜잭션이_일시_중단된다() {
        stubApi { e -> if (e == 2) response(2..4) else emptyResponse }
        var outerActive = false

        TransactionTemplate(transactionManager).executeWithoutResult {
            outerActive = TransactionSynchronizationManager.isActualTransactionActive()
            drawingService.fetchAndStoreLottoNumbers()
        }

        assertTrue(outerActive, "전제: 바깥 트랜잭션이 활성화되어 있어야 함")
        assertThat(observations).isNotEmpty
        observations.forEach { assertFalse(it.actualTransactionActive, "NOT_SUPPORTED인데 호출 중 트랜잭션이 활성화됨") }
        assertEquals((1..4).toList(), storedRounds())
    }

    // ----- C2: 중간 실패 시 앞 묶음 보존, 공백 없음 -----

    @Test
    fun 두_번째_호출이_실패하면_첫_묶음은_커밋되어_남고_공백이_없다() {
        stubApi { e -> if (e == 2) response(2..4) else throw RestClientException("503 Service Unavailable") }

        val logs = runScheduler()

        val rounds = storedRounds()
        assertEquals(listOf(1, 2, 3, 4), rounds)
        assertNoGaps(rounds)
        assertEquals(listOf(2, 5), observations.map { it.srchLtEpsd })
        assertThat(logs.messages(Level.ERROR).single()).contains("srchLtEpsd=5", "호출 횟수=2")
        assertSchedulerEventPublishedExactlyOnce()
        // 워밍업은 커밋된 데이터(4건) 기준
        assertEquals(4, cachedPage("0:5")!!.totalElements)
    }

    // ----- C3: 이벤트 1회/순서, fallbackExecution 워밍업 -----

    @Test
    fun 스케줄러_종료_후_이벤트를_1회_발행하고_트랜잭션_없이도_워머가_4개_캐시를_비우고_다시_채운다() {
        cacheNames.forEach { cache(it).put("9:9", "이전 데이터") }
        stubApi { e -> when (e) { 2 -> response(2..4); 5 -> response(5..6); else -> emptyResponse } }

        val logs = runScheduler()

        // 이벤트는 HTTP 호출/저장 동안에는 발행되지 않고, 끝난 뒤 정확히 1회
        assertEquals(listOf(0L, 0L, 0L), observations.map { it.eventsSoFar })
        assertSchedulerEventPublishedExactlyOnce()

        // fallbackExecution = true: 실제 트랜잭션이 없어도 메서드가 반환되기 전에 워밍업이 끝났다
        cacheNames.forEach { assertNull(cache(it).get("9:9"), "$it 캐시의 이전 데이터가 남아 있음") }
        val total = drawingRepository.count()
        assertEquals(6, total)
        assertEquals(total, cachedPage("0:5")!!.totalElements)
        assertEquals(listOf(6, 5, 4, 3, 2), cachedPage("0:5")!!.content.map { it.round })
        assertEquals(listOf(6), cachedPage("0:1")!!.content.map { it.round })
        assertNotNull(cachedStats("mostFrequentNumbers"))
        assertNotNull(cachedStats("topNumbersPerPosition"))
        val frequencies = cachedStats("frequenciesPerPosition")!!
        frequencies.groupBy { it.position }.values.forEach { assertEquals(total, it.sumOf { f -> f.frequency }) }

        // 로그 순서: 저장 완료(모든 묶음 커밋) -> 캐시 워밍업 완료(trigger=SCHEDULER)
        val infos = logs.messages(Level.INFO)
        val savedIndex = infos.indexOfFirst { it.contains("당첨 번호 저장 완료") && it.contains("회차 2~6") && it.contains("API 호출 3회") }
        val warmedIndex = infos.indexOfFirst { it.contains("캐시 워밍업 완료") && it.contains("trigger=SCHEDULER") }
        assertTrue(savedIndex >= 0, "저장 완료 로그 없음: $infos")
        assertTrue(warmedIndex > savedIndex, "워밍업이 저장 완료보다 먼저 실행됨: $infos")
        assertEquals(1, infos.count { it.contains("trigger=SCHEDULER") })
        assertThat(logs.atLevel(Level.WARN)).isEmpty()
    }

    @Test
    fun 새_회차가_없어도_이벤트를_1회_발행하고_워밍업한다() {
        cacheNames.forEach { cache(it).put("9:9", "이전 데이터") }
        stubApi { emptyResponse }

        val logs = runScheduler()

        assertSchedulerEventPublishedExactlyOnce()
        cacheNames.forEach { assertNull(cache(it).get("9:9")) }
        assertEquals(1, cachedPage("0:5")!!.totalElements)
        assertThat(logs.messages(Level.INFO)).anyMatch { it.contains("새로 저장한 회차 없음") && it.contains("API 호출 1회") }
        assertThat(logs.messages(Level.INFO)).anyMatch { it.contains("trigger=SCHEDULER") }
    }

    // ----- D1(통합): init 위반 묶음 -----

    @Test
    fun 두_번째_묶음에_번호_46이_섞이면_그_묶음_전체를_저장하지_않고_앞_묶음만_남긴다() {
        stubApi { e -> when (e) { 2 -> response(2..4); 5 -> response(5..6, invalidRound = 6); else -> emptyResponse } }

        val logs = runScheduler()

        val rounds = storedRounds()
        assertEquals(listOf(1, 2, 3, 4), rounds)
        // 5회차는 유효하지만 46이 있는 6회차와 같은 묶음이라 저장되지 않는다
        assertThat(rounds).doesNotContain(5, 6)
        assertNoGaps(rounds)
        // 실패한 묶음에서 멈춘다 (srchLtEpsd=7은 호출하지 않음)
        assertEquals(listOf(2, 5), observations.map { it.srchLtEpsd })

        val error = logs.atLevel(Level.ERROR).single()
        assertThat(error.formattedMessage).contains("당첨 번호 수집 실패", "srchLtEpsd=5", "호출 횟수=2", "응답 앞부분={\"resultCode\":null")
        assertEquals(IllegalArgumentException::class.java.name, error.throwableProxy!!.className)
        assertThat(logs.messages(Level.INFO)).anyMatch { it.contains("회차 2~4") && it.contains("API 호출 2회") }
        assertSchedulerEventPublishedExactlyOnce()
        assertEquals(4, cachedPage("0:5")!!.totalElements)
    }
}
