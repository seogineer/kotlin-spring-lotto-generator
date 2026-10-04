package com.seogineer.kotlinspringlottogenerator.service

import com.seogineer.kotlinspringlottogenerator.dto.FrequencyResponse
import com.seogineer.kotlinspringlottogenerator.dto.LottoNumberResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Random
import kotlin.math.pow

/**
 * 추천 번호 생성.
 *
 * 1. 가중 무작위(기본, 거절 샘플링): 6개 자리를 각각 자리별로 관측된 번호의 빈도 분포(가중치 frequency^exponent)에서
 *    독립적으로 뽑고, 결과가 1~45 범위의 엄격한 오름차순이 아니면 버리고 다시 뽑는다 (최대 MAX_ATTEMPTS회).
 * 2. 폴백: 데이터가 없거나 재시도 상한까지 실패하면 기존 방식(자리별 상위 5개로 만든 오름차순 조합 중 무작위 1개,
 *    조합이 없으면 LottoNumberResponse(0,0,0,0,0,0))을 사용한다.
 *
 * 통계는 캐시되는 DrawingService 메서드를 프록시를 통해 호출하므로 캐시가 채워져 있으면 쿼리가 발생하지 않는다.
 */
@Service
class LottoNumberGeneratorService(
    private val drawingService: DrawingService,
    private val random: Random,
    @Value("\${lotto.recommend.weight-exponent:0.5}") private val weightExponent: Double,
) {

    init {
        require(weightExponent.isFinite()) { "lotto.recommend.weight-exponent는 유한한 실수여야 합니다: $weightExponent" }
    }

    fun generateLottoNumbers(): LottoNumberResponse {
        val weighted = generateWeighted(toCandidatesByPosition(drawingService.getFrequenciesPerPosition()))
        if (weighted != null) {
            return toResponse(weighted)
        }
        return generateFromTopCombinations()
    }

    /** 자리 -> (번호, 가중치) 목록. 빈도 내림차순, 동률 번호 오름차순으로 정렬해 순회 순서를 결정적으로 만든다. */
    private fun toCandidatesByPosition(frequencies: List<FrequencyResponse>): Map<Int, List<Pair<Int, Double>>> {
        return (1..POSITIONS).associateWith { position ->
            frequencies
                .filter { it.position == position && it.frequency > 0 }
                .sortedWith(compareByDescending<FrequencyResponse> { it.frequency }.thenBy { it.number })
                .map { it.number to it.frequency.toDouble().pow(weightExponent) }
                .filter { it.second.isFinite() && it.second > 0.0 }
        }
    }

    /**
     * 거절 샘플링: 6개 자리를 각자의 빈도 분포에서 독립적으로 뽑고,
     * 결과가 1~45 범위의 엄격한 오름차순(서로 다름 포함)이 아니면 통째로 버리고 다시 뽑는다.
     * 받아들여진 결과의 분포는 "자리별 독립 분포를 오름차순 조건으로 제한한 분포"와 같다.
     */
    private fun generateWeighted(candidatesByPosition: Map<Int, List<Pair<Int, Double>>>): List<Int>? {
        val samplers = (1..POSITIONS).map { position ->
            WeightedSampler.of(candidatesByPosition.getValue(position)) ?: return null
        }
        repeat(MAX_ATTEMPTS) {
            val picked = samplers.map { it.sample(random) }
            if (isStrictlyAscendingInRange(picked)) {
                return picked
            }
        }
        return null
    }

    private fun isStrictlyAscendingInRange(numbers: List<Int>): Boolean =
        numbers.all { it in MIN_NUMBER..MAX_NUMBER } && numbers.zipWithNext().all { (a, b) -> a < b }

    /** 누적 가중치 배열 기반 샘플러. 한 번 만들어 재시도 동안 재사용한다. */
    private class WeightedSampler(private val numbers: IntArray, private val cumulative: DoubleArray) {
        fun sample(random: Random): Int {
            val target = random.nextDouble() * cumulative.last()
            val index = cumulative.indexOfFirst { target < it }
            return numbers[if (index >= 0) index else numbers.size - 1] // 부동소수점 오차 대비
        }

        companion object {
            fun of(candidates: List<Pair<Int, Double>>): WeightedSampler? {
                if (candidates.isEmpty()) return null
                val cumulative = DoubleArray(candidates.size)
                var total = 0.0
                candidates.forEachIndexed { i, (_, weight) ->
                    total += weight
                    cumulative[i] = total
                }
                if (!total.isFinite() || total <= 0.0) return null
                return WeightedSampler(candidates.map { it.first }.toIntArray(), cumulative)
            }
        }
    }

    // ----- 폴백: 기존 자리별 상위 5개 조합 방식 -----

    private fun generateFromTopCombinations(): LottoNumberResponse {
        val topNumbersByPosition = toTopNumbersByPosition(drawingService.getTopNumbersPerPosition())
        val validCombinations = generateValidCombinations(topNumbersByPosition)

        if (validCombinations.isEmpty()) {
            return LottoNumberResponse(0, 0, 0, 0, 0, 0)
        }

        return toResponse(validCombinations[random.nextInt(validCombinations.size)])
    }

    private fun toTopNumbersByPosition(topNumbers: List<FrequencyResponse>): Map<Int, List<Int>> {
        return (1..POSITIONS).associateWith { position ->
            topNumbers
                .filter { it.position == position }
                .sortedByDescending { it.frequency }
                .take(TOP_N)
                .map { it.number }
        }
    }

    private fun generateValidCombinations(topNumbersByPosition: Map<Int, List<Int>>): List<List<Int>> {
        val validCombinations: MutableList<List<Int>> = ArrayList()
        fun pick(position: Int, chosen: List<Int>) {
            if (position > POSITIONS) {
                validCombinations.add(chosen)
                return
            }
            for (number in topNumbersByPosition.getOrDefault(position, emptyList())) {
                // 가중 경로와 같은 규칙: 이전 자리보다 큰 번호만 (결과는 서로 다르고 엄격한 오름차순)
                if (chosen.isNotEmpty() && number <= chosen.last()) continue
                pick(position + 1, chosen + number)
            }
        }
        pick(1, emptyList())
        return validCombinations
    }

    private fun toResponse(numbers: List<Int>): LottoNumberResponse =
        LottoNumberResponse(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5])

    companion object {
        private const val POSITIONS = 6
        private const val TOP_N = 5
        const val MAX_ATTEMPTS = 1_000
        private const val MIN_NUMBER = 1
        private const val MAX_NUMBER = 45
    }
}
