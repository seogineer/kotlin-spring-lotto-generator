package com.seogineer.kotlinspringlottogenerator.dto

import java.io.Serializable


data class FrequencyResponse(
    val number: Int,
    val position: Int,
    val frequency: Long
) : Serializable
