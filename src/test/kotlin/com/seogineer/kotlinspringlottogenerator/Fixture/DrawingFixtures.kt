package com.seogineer.kotlinspringlottogenerator.Fixture

import com.seogineer.kotlinspringlottogenerator.entity.Drawing
import java.math.BigInteger
import java.time.LocalDate

class DrawingFixtures {
    companion object {
        val 당첨번호1 = Drawing(
            1,
            LocalDate.of(2002, 12, 7),
            10,
            23,
            29,
            33,
            37,
            40,
            16,
            BigInteger("0"),
            0
        )
        val 당첨번호2 = Drawing(
            2,
            LocalDate.of(2002, 12, 14),
            9,
            13,
            21,
            25,
            32,
            42,
            2,
            BigInteger("2002006800"),
            1
        )
        val 당첨번호3 = Drawing(
            3,
            LocalDate.of(2002, 12, 21),
            11,
            16,
            19,
            21,
            27,
            31,
            30,
            BigInteger("2000000000"),
            1
        )
        val 당첨번호4 = Drawing(
            4,
            LocalDate.of(2002, 12, 28),
            14,
            27,
            30,
            31,
            40,
            42,
            2,
            BigInteger("0"),
            0
        )
        val 당첨번호5 = Drawing(
            5,
            LocalDate.of(2003, 1, 4),
            16,
            24,
            29,
            40,
            41,
            42,
            3,
            BigInteger("0"),
            0
        )
        val 당첨번호6 = Drawing(
            6,
            LocalDate.of(2003, 1, 11),
            14,
            15,
            26,
            27,
            40,
            42,
            34,
            BigInteger("6574451700"),
            1
        )
        val 당첨번호7 = Drawing(
            7,
            LocalDate.of(2003, 1, 18),
            2,
            9,
            16,
            25,
            26,
            40,
            42,
            BigInteger("0"),
            0
        )
        val 당첨번호8 = Drawing(
            8,
            LocalDate.of(2003, 1, 25),
            8,
            19,
            25,
            34,
            37,
            39,
            9,
            BigInteger("0"),
            0
        )
        val 당첨번호9 = Drawing(
            9,
            LocalDate.of(2003, 2, 1),
            2,
            4,
            16,
            17,
            36,
            39,
            14,
            BigInteger("0"),
            0
        )
        val 당첨번호10 = Drawing(
            10,
            LocalDate.of(2003, 2, 8),
            9,
            25,
            30,
            33,
            41,
            44,
            6,
            BigInteger("6430437900"),
            13
        )
    }
}
