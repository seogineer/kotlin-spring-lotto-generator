package com.seogineer.kotlinspringlottogenerator.Controller

import com.seogineer.kotlinspringlottogenerator.Dto.LottoNumberResponse
import com.seogineer.kotlinspringlottogenerator.Entity.Drawing
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.builder.RequestSpecBuilder
import io.restassured.filter.Filter
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.restassured3.RestAssuredRestDocumentation.document
import org.springframework.restdocs.restassured3.RestAssuredRestDocumentation.documentationConfiguration
import java.io.File


@ExtendWith(RestDocumentationExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DrawingControllerTest {

    private lateinit var spec: RequestSpecification

    @LocalServerPort
    var port = 0

    @BeforeEach
    fun setUp(restDocumentation: RestDocumentationContextProvider) {
        RestAssured.port = port

        val restAssuredConfig: Filter = documentationConfiguration(restDocumentation)
            .operationPreprocessors()
            .withRequestDefaults(prettyPrint())
            .withResponseDefaults(prettyPrint())
        spec = RequestSpecBuilder().addFilter(restAssuredConfig).build()
    }

    @Test
    fun 엑셀_업로드() {
        val response = given(spec)
            .filter(
                document(
                    "upload-default-data-using-excel",
                    requestParts(
                        partWithName("file").description("업로드할 Excel 파일")
                    ),
                    responseFields(
                        fieldWithPath("message").description("업로드 성공 또는 실패 메시지")
                    )
                )
            )
            .multiPart("file", File(ClassLoader.getSystemResource("excel.xlsx").file))
            .accept(ContentType.JSON)
            .`when`()
            .post("/drawings/upload")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value())
    }

    @Test
    fun 역대_당첨_번호_조회() {
        given()
            .multiPart("file", File(ClassLoader.getSystemResource("excel.xlsx").file))
            .accept(ContentType.JSON)
            .`when`()
            .post("/drawings/upload")

        val response = given(spec).log().all()
            .filter(
                document(
                    "get-drawings",
                    requestParameters(
                        parameterWithName("page").description("페이지"),
                        parameterWithName("size").description("크기"),
                    ),
                    responseFields(
                        fieldWithPath("content").description("Drawing 목록").type(JsonFieldType.ARRAY),
                        fieldWithPath("content[].round").description("회차").type(JsonFieldType.NUMBER),
                        fieldWithPath("content[].date").description("추첨일").type(JsonFieldType.STRING),
                        fieldWithPath("content[].one").description("첫번째 당첨 번호").type(JsonFieldType.NUMBER),
                        fieldWithPath("content[].two").description("두번째 당첨 번호").type(JsonFieldType.NUMBER),
                        fieldWithPath("content[].three").description("세번째 당첨 번호").type(JsonFieldType.NUMBER),
                        fieldWithPath("content[].four").description("네번째 당첨 번호").type(JsonFieldType.NUMBER),
                        fieldWithPath("content[].five").description("다섯번째 당첨 번호").type(JsonFieldType.NUMBER),
                        fieldWithPath("content[].six").description("여섯번째 당첨 번호").type(JsonFieldType.NUMBER),
                        fieldWithPath("content[].bonus").description("보너스 당첨 번호").type(JsonFieldType.NUMBER),
                        fieldWithPath("content[].firstWinPrize").description("1등 당첨 상금").type(JsonFieldType.NUMBER),
                        fieldWithPath("content[].firstWinners").description("1등 당첨자 수").type(JsonFieldType.NUMBER),
                        fieldWithPath("pageable").description("페이징 관련 요청 정보").type(JsonFieldType.OBJECT),
                        fieldWithPath("pageable.sort.unsorted").description("데이터가 정렬되지 않았는지 여부")
                            .type(JsonFieldType.BOOLEAN),
                        fieldWithPath("pageable.sort.sorted").description("데이터가 정렬되었는지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("pageable.sort.empty").description("정렬 조건이 비었는지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("pageable.pageNumber").description("현재 페이지 번호(0부터 시작)")
                            .type(JsonFieldType.NUMBER),
                        fieldWithPath("pageable.pageSize").description("한 페이지에 포함될 데이터 개수").type(JsonFieldType.NUMBER),
                        fieldWithPath("pageable.offset").description("현재 페이지의 데이터 시작 위치").type(JsonFieldType.NUMBER),
                        fieldWithPath("pageable.paged").description("페이징이 적용되었는지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("pageable.unpaged").description("페이징이 적용되지 않았는지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("sort.sorted").description("데이터가 정렬되었는지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("sort.unsorted").description("데이터가 정렬되지 않았는지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("sort.empty").description("정렬 조건이 비었는지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("first").description("현재 페이지가 첫번째 페이지인지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("empty").description("현재 페이지가 비어있는지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("last").description("현재 페이지가 마지막 페이지인지 여부").type(JsonFieldType.BOOLEAN),
                        fieldWithPath("numberOfElements").description("페이지 정보").type(JsonFieldType.NUMBER),
                        fieldWithPath("totalPages").description("총 페이지 수").type(JsonFieldType.NUMBER),
                        fieldWithPath("totalElements").description("총 요소 수").type(JsonFieldType.NUMBER),
                        fieldWithPath("size").description("페이지 크기").type(JsonFieldType.NUMBER),
                        fieldWithPath("number").description("현재 페이지 번호").type(JsonFieldType.NUMBER)
                    )
                )
            )
            .`when`()
            .get("/drawings?page=0&size=5")
            .then().log().all()
            .extract()

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value())
        assertThat(response.jsonPath().getList("content", Drawing::class.java)).hasSize(5)
    }

    @Test
    fun 추천_번호_생성() {
        given()
            .multiPart("file", File(ClassLoader.getSystemResource("excel.xlsx").file))
            .accept(ContentType.JSON)
            .`when`()
            .post("/drawings/upload")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()

        val response = given(spec).log().all()
            .filter(
                document(
                    "generate-recommend-number",
                    responseFields(
                        fieldWithPath("one").description("당첨번호 첫번째 자리").type(JsonFieldType.NUMBER),
                        fieldWithPath("two").description("당첨번호 두번째 자리").type(JsonFieldType.NUMBER),
                        fieldWithPath("three").description("당첨번호 세번째 자리").type(JsonFieldType.NUMBER),
                        fieldWithPath("four").description("당첨번호 네번째 자리").type(JsonFieldType.NUMBER),
                        fieldWithPath("five").description("당첨번호 다섯번째 자리").type(JsonFieldType.NUMBER),
                        fieldWithPath("six").description("당첨번호 여섯번째 자리").type(JsonFieldType.NUMBER),
                    )
                )
            )
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .`when`()
            .get("/drawings/generate")
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
