package com.seogineer.kotlinspringlottogenerator.Fixture

import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import java.math.BigInteger
import java.time.LocalDate

class DrawingFixtures {
    companion object {
        val 당첨번호1 = Drawing(
            1,
            LocalDate.of(2024, 9, 1),
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            BigInteger("2000000000"),
            1
        )
        val 당첨번호2 = Drawing(
            2,
            LocalDate.of(2024, 10, 16),
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            BigInteger("1000000000"),
            5
        )
        val 당첨번호3 = Drawing(
            3,
            LocalDate.of(2024, 12, 28),
            15,
            16,
            17,
            18,
            19,
            20,
            21,
            BigInteger("10000000000"),
            2
        )
    }
}
