package com.seogineer.kotlinspringlottogenerator.Controller

import com.seogineer.kotlinspringlottogenerator.Dto.LottoNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.io.File

@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DrawingControllerTest {
    @LocalServerPort
    var port = 0

    @BeforeEach
    fun setUp() {
        RestAssured.port = port
    }

    @Test
    fun 엑셀_업로드() {
        val response = RestAssured
            .given()
                .multiPart("file", File("./excel.xlsx"))
                .accept(ContentType.JSON)
            .`when`()
                .post("/upload")
            .then()
                .statusCode(HttpStatus.OK.value())
            .extract()

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value())
    }

    @Test
    fun 역대_당첨_번호_조회() {
        RestAssured
            .given()
                .multiPart("file", File("./excel.xlsx"))
                .accept(ContentType.JSON)
            .`when`()
                .post("/upload")
            .then()
                .statusCode(HttpStatus.OK.value())
            .extract()

        val response = RestAssured
            .given().log().all()
                .accept(MediaType.APPLICATION_JSON_VALUE)
            .`when`()
                .get("/drawings?page=0&size=5")
            .then().log().all()
            .extract()

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value())
        assertThat(response.jsonPath().getList("content", Drawing::class.java)).hasSize(5)
    }

    @Test
    fun 추천_번호_생성() {
        RestAssured
            .given()
            .multiPart("file", File("./excel.xlsx"))
            .accept(ContentType.JSON)
            .`when`()
            .post("/upload")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()

        val response = RestAssured
            .given().log().all()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .`when`()
            .get("/generate")
            .then().log().all()
            .extract()

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value())
        val recommededNumbers = response.jsonPath().getObject("", LottoNumberResponse::class.java)
        assertThat(recommededNumbers.one).isBetween(1, 45)
        assertThat(recommededNumbers.two).isBetween(1, 45)
        assertThat(recommededNumbers.three).isBetween(1, 45)
        assertThat(recommededNumbers.four).isBetween(1, 45)
        assertThat(recommededNumbers.five).isBetween(1, 45)
        assertThat(recommededNumbers.six).isBetween(1, 45)
    }
}
