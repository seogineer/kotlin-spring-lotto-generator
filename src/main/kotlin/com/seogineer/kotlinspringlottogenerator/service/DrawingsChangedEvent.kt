package com.seogineer.kotlinspringlottogenerator.service

/**
 * Drawing 데이터 변경 작업(엑셀 업로드, 스케줄러 수집)이 시작됐음을 알리는 이벤트.
 * 해당 트랜잭션이 끝난 뒤(커밋/롤백 모두) DrawingCacheWarmer가 캐시를 다시 채운다.
 */
data class DrawingsChangedEvent(val source: Source) {
    enum class Source { EXCEL_UPLOAD, SCHEDULER }
}
