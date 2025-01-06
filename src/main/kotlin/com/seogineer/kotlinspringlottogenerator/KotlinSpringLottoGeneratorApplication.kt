package com.seogineer.kotlinspringlottogenerator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class KotlinSpringLottoGeneratorApplication

fun main(args: Array<String>) {
    runApplication<KotlinSpringLottoGeneratorApplication>(*args)
}
