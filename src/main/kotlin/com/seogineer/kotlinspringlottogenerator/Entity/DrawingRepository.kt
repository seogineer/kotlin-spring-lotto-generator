package com.seogineer.kotlinspringlottogenerator.Entity

import org.springframework.data.jpa.repository.JpaRepository

interface DrawingRepository : JpaRepository<Drawing, Long>, DrawingRepositoryCustom
