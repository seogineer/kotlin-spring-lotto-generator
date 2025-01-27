package com.seogineer.kotlinspringlottogenerator.dto

import java.math.BigInteger

data class LatestNumberResponse (
    val totSellamnt: BigInteger,
    val returnValue: String,
    val drwNo: Int,
    val drwNoDate: String,
    val drwtNo1: Int,
    val drwtNo2: Int,
    val drwtNo3: Int,
    val drwtNo4: Int,
    val drwtNo5: Int,
    val drwtNo6: Int,
    val bnusNo: Int,
    val firstWinamnt: BigInteger,
    val firstPrzwnerCo: Int
)
