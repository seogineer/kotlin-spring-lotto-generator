package com.seogineer.kotlinspringlottogenerator.Entity

import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "DRAWING")
data class Drawing (
    @Id
    val round: Int,
    val date: LocalDate,
    val one: Int,
    val two: Int,
    val three: Int,
    val four: Int,
    val five: Int,
    val six: Int,
    val bonus: Int,
    val firstWinPrize: Long,
    val firstWinners: Int,
)
