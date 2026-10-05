package com.seogineer.kotlinspringlottogenerator.service

/**
 * Drawing 데이터 변경 작업(엑셀 업로드, 스케줄러 수집)의 캐시 재워밍업 신호.
 *
 * - 엑셀 업로드: 업로드 트랜잭션 안에서 작업 시작 시 발행한다. DrawingCacheWarmer가 트랜잭션 완료 후
 *   (AFTER_COMPLETION, 커밋/롤백 모두) 캐시를 다시 비우고 채운다.
 * - 스케줄러: 실제 트랜잭션 없이(NOT_SUPPORTED) 수집하고 묶음별로 커밋한 뒤, 작업이 끝난 시점(finally)에 1회 발행한다.
 *   트랜잭션이 없으므로 리스너(fallbackExecution = true)가 발행 즉시 캐시를 다시 비우고 채운다.
 */
data class DrawingsChangedEvent(val source: Source) {
    enum class Source { EXCEL_UPLOAD, SCHEDULER }
}
