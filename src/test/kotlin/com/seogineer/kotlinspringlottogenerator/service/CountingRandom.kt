package com.seogineer.kotlinspringlottogenerator.service

import java.util.Random

/**
 * 시드 고정 + 호출 횟수 계측용 Random.
 * LottoNumberGeneratorService는 가중 경로에서 nextDouble(), 폴백에서 nextInt(bound)만 쓴다.
 * nextDouble()은 내부에서 next(bits)를 두 번 부르므로 next가 아니라 공개 메서드를 직접 센다.
 */
class CountingRandom(seed: Long) : Random(seed) {
    var nextDoubleCount = 0
        private set
    var nextIntCount = 0
        private set

    override fun nextDouble(): Double {
        nextDoubleCount++
        return super.nextDouble()
    }

    override fun nextInt(bound: Int): Int {
        nextIntCount++
        return super.nextInt(bound)
    }

    fun resetCounts() {
        nextDoubleCount = 0
        nextIntCount = 0
    }
}
