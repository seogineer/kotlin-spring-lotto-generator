package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.config.RandomConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.test.util.ReflectionTestUtils
import ch.qos.logback.classic.Level
import com.seogineer.kotlinspringlottogenerator.support.LogCaptor
import com.seogineer.kotlinspringlottogenerator.support.messages
import java.util.Random

/**
 * G7: lotto.recommend.weight-exponent 프로퍼티 바인딩과 기동 시 검증.
 * 전체 컨텍스트 대신 ApplicationContextRunner로 LottoNumberGeneratorService와 RandomConfig만 띄운다 (DB/외부 API 없음).
 */
class LottoNumberGeneratorServiceConfigTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(RandomConfig::class.java)
        .withBean(DrawingService::class.java, { mock(DrawingService::class.java) })
        .withBean(LottoNumberGeneratorService::class.java)

    private fun exponentOf(service: LottoNumberGeneratorService) =
        ReflectionTestUtils.getField(service, "weightExponent") as Double

    @Test
    fun 프로퍼티가_없으면_기본_지수_0_5로_기동한다() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(exponentOf(context.getBean(LottoNumberGeneratorService::class.java))).isEqualTo(0.5)
        }
    }

    @Test
    fun 프로퍼티로_지정한_지수가_반영된다() {
        contextRunner.withPropertyValues("lotto.recommend.weight-exponent=0").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(exponentOf(context.getBean(LottoNumberGeneratorService::class.java))).isEqualTo(0.0)
        }
        contextRunner.withPropertyValues("lotto.recommend.weight-exponent=2.5").run { context ->
            assertThat(exponentOf(context.getBean(LottoNumberGeneratorService::class.java))).isEqualTo(2.5)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["NaN", "Infinity", "-Infinity"])
    fun 지수가_NaN이거나_무한대면_기동이_실패한다(value: String) {
        contextRunner.withPropertyValues("lotto.recommend.weight-exponent=$value").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasRootCauseInstanceOf(IllegalArgumentException::class.java)
            assertThat(context.startupFailure).hasStackTraceContaining("weight-exponent")
        }
    }

    @Test
    fun RandomConfig의_java_util_Random_빈이_추천_서비스에_주입된다() {
        contextRunner.run { context ->
            val random = context.getBean(Random::class.java)
            val service = context.getBean(LottoNumberGeneratorService::class.java)
            assertThat(ReflectionTestUtils.getField(service, "random")).isSameAs(random)
        }
    }

    // ----- 08 E: 음수는 WARN 후 기동, 적용된 지수는 기동 시 INFO 1회 -----

    private fun captureStartupLogs(vararg properties: String, assertions: (org.springframework.boot.test.context.assertj.AssertableApplicationContext) -> Unit) =
        LogCaptor.capture(LottoNumberGeneratorService::class.java) {
            contextRunner.withPropertyValues(*properties).run { context -> assertions(context) }
        }

    @Test
    fun 지수가_음수면_WARN을_남기고_기동한다() {
        val logs = captureStartupLogs("lotto.recommend.weight-exponent=-1") { context ->
            assertThat(context).hasNotFailed()
            assertThat(exponentOf(context.getBean(LottoNumberGeneratorService::class.java))).isEqualTo(-1.0)
        }

        val warn = logs.messages(Level.WARN).single()
        assertThat(warn).contains("weight-exponent", "음수", "-1.0")
        assertThat(logs.messages(Level.INFO).single()).contains("weight-exponent=-1.0")
    }

    @Test
    fun 기본_지수로_기동하면_INFO를_1회_남기고_WARN은_없다() {
        val logs = captureStartupLogs { context -> assertThat(context).hasNotFailed() }

        assertThat(logs.messages(Level.INFO).single()).contains("weight-exponent=0.5")
        assertThat(logs.messages(Level.WARN)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "2.5"])
    fun 지수가_0_이상이면_WARN_없이_적용된_값을_INFO로_남긴다(value: String) {
        val logs = captureStartupLogs("lotto.recommend.weight-exponent=$value") { context -> assertThat(context).hasNotFailed() }

        assertThat(logs.messages(Level.WARN)).isEmpty()
        assertThat(logs.messages(Level.INFO).single()).contains("weight-exponent=${value.toDouble()}")
    }

    @ParameterizedTest
    @ValueSource(strings = ["NaN", "Infinity", "-Infinity"])
    fun 지수가_NaN이거나_무한대면_적용_INFO_없이_기동이_실패한다(value: String) {
        val logs = captureStartupLogs("lotto.recommend.weight-exponent=$value") { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasRootCauseInstanceOf(IllegalArgumentException::class.java)
        }

        // 유한성 검사가 먼저라 음수 WARN(-Infinity)이나 적용 INFO가 남지 않는다
        assertThat(logs.messages(Level.INFO)).isEmpty()
        assertThat(logs.messages(Level.WARN)).isEmpty()
    }
}
