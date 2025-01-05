package com.seogineer.kotlinspringlottogenerator.Entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.DateTimeException
import java.time.LocalDate

class DrawingTest {
    @Test
    fun 생성() {
        // Given
        val round = 1
        val date = LocalDate.of(2024, 1, 5)
        val one = 3
        val two = 11
        val three = 16
        val four = 21
        val five = 35
        val six = 42
        val bonus = 7
        val firstWinPrize = BigInteger("2000000000")
        val firstWinners = 5

        // When
        val drawing = Drawing(
            round = round,
            date = date,
            one = one,
            two = two,
            three = three,
            four = four,
            five = five,
            six = six,
            bonus = bonus,
            firstWinPrize = firstWinPrize,
            firstWinners = firstWinners
        )

        // Then
        assertEquals(round, drawing.round)
        assertEquals(date, drawing.date)
        assertEquals(one, drawing.one)
        assertEquals(two, drawing.two)
        assertEquals(three, drawing.three)
        assertEquals(four, drawing.four)
        assertEquals(five, drawing.five)
        assertEquals(six, drawing.six)
        assertEquals(bonus, drawing.bonus)
        assertEquals(firstWinPrize, drawing.firstWinPrize)
        assertEquals(firstWinners, drawing.firstWinners)
    }

    @Test
    fun 회차_유효성_검사() {
        assertThrows(IllegalArgumentException::class.java) {
            Drawing(
                round = -1,  // Invalid round
                date = LocalDate.of(2024, 1, 5),
                one = 3,
                two = 11,
                three = 16,
                four = 21,
                five = 35,
                six = 42,
                bonus = 7,
                firstWinPrize = BigInteger("2000000000"),
                firstWinners = 5
            )
        }
    }

    @Test
    fun 날짜_월_유효성_검사() {
        assertThrows(DateTimeException::class.java) {
            Drawing(
                round = 1,
                date = LocalDate.of(2024, 13, 1),
                one = 3,
                two = 11,
                three = 16,
                four = 21,
                five = 35,
                six = 42,
                bonus = 7,
                firstWinPrize = -BigInteger.ONE,
                firstWinners = 5
            )
        }
    }

    @Test
    fun 날짜_일_유효성_검사_() {
        assertThrows(DateTimeException::class.java) {
            Drawing(
                round = 1,
                date = LocalDate.of(2024, 12, 32),
                one = 3,
                two = 11,
                three = 16,
                four = 21,
                five = 35,
                six = 42,
                bonus = 7,
                firstWinPrize = -BigInteger.ONE,
                firstWinners = 5
            )
        }
    }

    @Test
    fun 일등_상금_유효성_검사() {
        assertThrows(IllegalArgumentException::class.java) {
            Drawing(
                round = 1,
                date = LocalDate.of(2024, 1, 5),
                one = 3,
                two = 11,
                three = 16,
                four = 21,
                five = 35,
                six = 42,
                bonus = 7,
                firstWinPrize = -BigInteger.ONE,
                firstWinners = 5
            )
        }
    }

    @Test
    fun 번호_유효성_검사() {
        assertThrows(IllegalArgumentException::class.java) {
            Drawing(
                round = 1,
                date = LocalDate.of(2024, 1, 5),
                one = 46,   // 46 입력
                two = 11,
                three = 16,
                four = 21,
                five = 35,
                six = 42,
                bonus = 7,
                firstWinPrize = BigInteger("2000000000"),
                firstWinners = 5
            )
        }
    }

    @Test
    fun 보너스_번호_유효성_검사() {
        assertThrows(IllegalArgumentException::class.java) {
            Drawing(
                round = 1,
                date = LocalDate.of(2024, 1, 5),
                one = 1,
                two = 11,
                three = 16,
                four = 21,
                five = 35,
                six = 42,
                bonus = 0,  // 0 입력
                firstWinPrize = BigInteger("2000000000"),
                firstWinners = 5
            )
        }
    }
}
