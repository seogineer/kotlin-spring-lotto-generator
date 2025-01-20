package com.seogineer.kotlinspringlottogenerator.dto

import java.io.Serializable

data class LottoNumberResponse (
    val one: Int,
    val two: Int,
    val three: Int,
    val four: Int,
    val five: Int,
    val six: Int,
) : Serializable
