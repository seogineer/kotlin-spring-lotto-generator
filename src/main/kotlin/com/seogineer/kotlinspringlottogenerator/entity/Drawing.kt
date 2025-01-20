package com.seogineer.kotlinspringlottogenerator.entity

import java.io.Serializable
import java.math.BigInteger
import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "drawing")
class Drawing (
    @Id
    val round: Int,
    val date: LocalDate,
    val one: Int,
    val two: Int,
    val three: Int,
    val four: Int,
    val five: Int,
    val six: Int,
    val bonus: Int,
    val firstWinPrize: BigInteger,
    val firstWinners: Int,
) : Serializable {
    init {
        require(round > 0) {
            "회차는 항상 0 보다 커야 합니다"
        }
        require(one in 1..45 && two in 1..45 && three in 1..45 && four in 1..45 && five in 1..45 && six in 1..45) {
            "입력할 수 있는 숫자는 1~45 사이의 숫자입니다"
        }
        require(bonus in 1..45) {
            "입력할 수 있는 숫자는 1~45 사이의 숫자입니다"
        }
        require(firstWinPrize >= BigInteger.ZERO) {
            "상금은 음수가 될 수 없습니다"
        }
        require(firstWinners >= 0) {
            "1등 당첨자의 수는 음수가 될 수 없습니다"
        }
    }
}
