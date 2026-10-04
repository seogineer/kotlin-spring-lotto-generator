package com.seogineer.kotlinspringlottogenerator.dto

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

/**
 * DrawingService.fetchAndStoreLottoNumbers()가 사용하는 것과 동일한 ObjectMapper 설정
 * (ObjectMapper().registerKotlinModule())으로 selectPstLt645InfoNew.do 응답 역직렬화를 검증한다.
 * 외부 API는 호출하지 않는다. 샘플 JSON은 DTO 필드명 기반의 합성 데이터이며 알 수 없는 필드를 섞어 둔다.
 */
class LottoDrawingApiResponseTest {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private fun parse(json: String): LottoDrawingApiResponse =
        objectMapper.readValue(json, LottoDrawingApiResponse::class.java)

    private fun item(round: Int, ymd: String, numbers: List<Int>, bonus: Int, prize: String, winners: Int) = """
        {
          "ltEpsd": $round,
          "ltRflYmd": "$ymd",
          "tm1WnNo": ${numbers[0]}, "tm2WnNo": ${numbers[1]}, "tm3WnNo": ${numbers[2]},
          "tm4WnNo": ${numbers[3]}, "tm5WnNo": ${numbers[4]}, "tm6WnNo": ${numbers[5]},
          "bnsWnNo": $bonus,
          "rnk1WnAmt": $prize,
          "rnk1WnNope": $winners,
          "rnk2WnAmt": 50000000,
          "unknownField": "무시되어야 함"
        }
    """.trimIndent()

    @Test
    fun 정상_응답_역직렬화_시_list_항목이_모두_매핑된다() {
        val json = """
            {
              "resultCode": "0000",
              "resultMessage": "SUCCESS",
              "extra": {"foo": "bar"},
              "data": {
                "total": 3,
                "list": [
                  ${item(1244, "20261003", listOf(3, 11, 19, 27, 35, 43), 7, "2876543210", 9)},
                  ${item(1243, "20260926", listOf(1, 8, 15, 22, 29, 45), 40, "1987654321", 12)},
                  ${item(1242, "20260919", listOf(2, 9, 16, 23, 30, 44), 5, "3123456789", 8)}
                ]
              }
            }
        """.trimIndent()

        val response = parse(json)

        assertEquals("0000", response.resultCode)
        assertEquals("SUCCESS", response.resultMessage)
        val list = response.data!!.list
        assertEquals(3, list.size)
        assertThat(list.map { it.ltEpsd }).containsExactly(1244, 1243, 1242)

        val first = list[0]
        assertEquals(1244, first.ltEpsd)
        assertEquals("20261003", first.ltRflYmd)
        assertThat(listOf(first.tm1WnNo, first.tm2WnNo, first.tm3WnNo, first.tm4WnNo, first.tm5WnNo, first.tm6WnNo))
            .containsExactly(3, 11, 19, 27, 35, 43)
        assertEquals(7, first.bnsWnNo)
        assertEquals(BigInteger("2876543210"), first.rnk1WnAmt)
        assertEquals(9, first.rnk1WnNope)
    }

    @Test
    fun Int_범위를_넘는_1등_당첨금도_BigInteger로_파싱된다() {
        val json = """
            {"data": {"list": [${item(1, "20021207", listOf(10, 23, 29, 33, 37, 40), 16, "40722959400", 0)}]}}
        """.trimIndent()

        val response = parse(json)

        assertEquals(BigInteger("40722959400"), response.data!!.list[0].rnk1WnAmt)
    }

    @Test
    fun data가_null이면_빈_목록으로_처리된다() {
        val response = parse("""{"resultCode": "0000", "data": null}""")

        assertNull(response.data)
        assertTrue(response.data?.list.orEmpty().isEmpty())
    }

    @Test
    fun data_필드가_없으면_null로_처리된다() {
        val response = parse("""{"resultCode": "0000"}""")

        assertNull(response.data)
        assertTrue(response.data?.list.orEmpty().isEmpty())
    }

    @Test
    fun list가_비어_있으면_빈_목록으로_파싱된다() {
        val response = parse("""{"data": {"list": []}}""")

        assertNotNull(response.data)
        assertTrue(response.data!!.list.isEmpty())
    }

    @Test
    fun list_필드가_없으면_기본값인_빈_목록이_된다() {
        val response = parse("""{"data": {"total": 0}}""")

        assertTrue(response.data!!.list.isEmpty())
    }

    @Test
    fun HTML_응답은_JsonProcessingException을_던진다() {
        val html = """
            <!DOCTYPE html>
            <html><head><title>동행복권</title></head><body>점검 중입니다.</body></html>
        """.trimIndent()

        assertThrows<JsonProcessingException> { parse(html) }
    }

    @Test
    fun 필수_필드가_누락된_항목이_있으면_전체_역직렬화가_실패한다() {
        // ltEpsd 누락: 서비스에서는 catch로 삼켜져 해당 실행의 모든 회차가 저장되지 않는다
        val json = """
            {"data": {"list": [
              ${item(1243, "20260926", listOf(1, 8, 15, 22, 29, 45), 40, "1987654321", 12)},
              {"ltRflYmd": "20260919", "tm1WnNo": 2}
            ]}}
        """.trimIndent()

        assertThrows<JsonProcessingException> { parse(json) }
    }
}
