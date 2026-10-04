package com.seogineer.kotlinspringlottogenerator.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Random

/** 추천 번호 생성에 쓰는 난수 생성기. 테스트에서는 시드를 고정한 Random을 직접 주입한다. */
@Configuration
class RandomConfig {

    @Bean
    fun random(): Random = Random()
}
