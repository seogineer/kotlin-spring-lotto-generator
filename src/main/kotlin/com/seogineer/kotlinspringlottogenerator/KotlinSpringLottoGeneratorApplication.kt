package com.seogineer.kotlinspringlottogenerator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
class KotlinSpringLottoGeneratorApplication

fun main(args: Array<String>) {
    runApplication<KotlinSpringLottoGeneratorApplication>(*args)
}
