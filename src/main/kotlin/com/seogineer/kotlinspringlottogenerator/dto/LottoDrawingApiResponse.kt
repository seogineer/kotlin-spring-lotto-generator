package com.seogineer.kotlinspringlottogenerator.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigInteger

@JsonIgnoreProperties(ignoreUnknown = true)
data class LottoDrawingApiResponse(
    val resultCode: String? = null,
    val resultMessage: String? = null,
    val data: LottoDrawingApiData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LottoDrawingApiData(
    val list: List<LottoDrawingItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LottoDrawingItem(
    val ltEpsd: Int,
    val ltRflYmd: String,
    val tm1WnNo: Int,
    val tm2WnNo: Int,
    val tm3WnNo: Int,
    val tm4WnNo: Int,
    val tm5WnNo: Int,
    val tm6WnNo: Int,
    val bnsWnNo: Int,
    val rnk1WnAmt: BigInteger,
    val rnk1WnNope: Int,
)
