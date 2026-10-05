package com.seogineer.kotlinspringlottogenerator.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface DrawingRepository : JpaRepository<Drawing, Int>, DrawingRepositoryCustom {
    /**
     * 짧은 읽기 트랜잭션으로 실행한다. 트랜잭션 없이(예: 스케줄러의 NOT_SUPPORTED 범위) 호출되면
     * 공유 EntityManager가 스레드에 바인딩되어 범위가 끝날 때까지 DB 커넥션을 쥐므로 명시한다.
     */
    @Transactional(readOnly = true)
    fun findTopByOrderByRoundDesc(): Optional<Drawing>
}
