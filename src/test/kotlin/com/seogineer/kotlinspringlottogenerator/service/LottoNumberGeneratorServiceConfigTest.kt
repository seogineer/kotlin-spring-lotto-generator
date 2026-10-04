package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.config.RandomConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.test.util.ReflectionTestUtils
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
}
