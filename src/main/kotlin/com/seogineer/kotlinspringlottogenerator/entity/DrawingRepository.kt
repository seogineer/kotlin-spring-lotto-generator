package com.seogineer.kotlinspringlottogenerator.entity

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface DrawingRepository : JpaRepository<Drawing, Long>, DrawingRepositoryCustom {
    fun findTopByOrderByRoundDesc(): Optional<Drawing>
}
