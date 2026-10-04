package com.seogineer.kotlinspringlottogenerator.fixture

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

        // 당첨번호11~13: getMostFrequentNumbers 동률 정렬 검증용 (단독 적재해서 사용)
        // 빈도: 7·12·20 = 3회, 3·33·41 = 2회, 38·44·45 = 1회
        // 16 이상/미만 번호를 섞어 HashMap 순회 순서(20,7,12 / 33,3,41)가 오름차순과 다르게 했다
        val 당첨번호11 = Drawing(
            11,
            LocalDate.of(2003, 2, 15),
            7,
            12,
            20,
            33,
            41,
            45,
            1,
            BigInteger("0"),
            0
        )
        val 당첨번호12 = Drawing(
            12,
            LocalDate.of(2003, 2, 22),
            3,
            7,
            12,
            20,
            33,
            44,
            2,
            BigInteger("0"),
            0
        )
        val 당첨번호13 = Drawing(
            13,
            LocalDate.of(2003, 3, 1),
            3,
            7,
            12,
            20,
            38,
            41,
            4,
            BigInteger("0"),
            0
        )

        // 당첨번호14~21: getTopNumbersPerPosition / getFrequenciesPerPosition 5위 경계 동률 검증용 (단독 적재해서 사용)
        // 자리별로 7개 번호가 나오며, 가장 큰 번호만 2회(14·21회차 중복), 나머지 6개는 1회씩이다.
        // 큰 번호부터 적재해 삽입 순서가 번호 오름차순과 반대가 되게 했다.
        // 1번 자리: 7(2회), 1~6(1회) -> 상위 5개는 7, 1, 2, 3, 4 (5·6은 동률이지만 번호가 커서 제외)
        val 당첨번호14 = Drawing(
            14,
            LocalDate.of(2003, 3, 8),
            7,
            16,
            23,
            30,
            37,
            44,
            45,
            BigInteger("0"),
            0
        )
        val 당첨번호15 = Drawing(
            15,
            LocalDate.of(2003, 3, 15),
            6,
            15,
            22,
            29,
            36,
            43,
            1,
            BigInteger("0"),
            0
        )
        val 당첨번호16 = Drawing(
            16,
            LocalDate.of(2003, 3, 22),
            5,
            14,
            21,
            28,
            35,
            42,
            1,
            BigInteger("0"),
            0
        )
        val 당첨번호17 = Drawing(
            17,
            LocalDate.of(2003, 3, 29),
            4,
            13,
            20,
            27,
            34,
            41,
            1,
            BigInteger("0"),
            0
        )
        val 당첨번호18 = Drawing(
            18,
            LocalDate.of(2003, 4, 5),
            3,
            12,
            19,
            26,
            33,
            40,
            1,
            BigInteger("0"),
            0
        )
        val 당첨번호19 = Drawing(
            19,
            LocalDate.of(2003, 4, 12),
            2,
            11,
            18,
            25,
            32,
            39,
            1,
            BigInteger("0"),
            0
        )
        val 당첨번호20 = Drawing(
            20,
            LocalDate.of(2003, 4, 19),
            1,
            10,
            17,
            24,
            31,
            38,
            45,
            BigInteger("0"),
            0
        )
        val 당첨번호21 = Drawing(
            21,
            LocalDate.of(2003, 4, 26),
            7,
            16,
            23,
            30,
            37,
            44,
            45,
            BigInteger("0"),
            0
        )
    }
}
