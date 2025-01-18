package com.seogineer.kotlinspringlottogenerator.Config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig {
//    @Bean
//    fun corsConfigurer(): WebMvcConfigurer {
//        val serverIp: String = when (System.getProperty("spring.profiles.active")) {
//            "prod" -> "https://seogineer.github.io"
//            else -> "http://localhost:3000"
//        }
//
//        return object : WebMvcConfigurer {
//            override fun addCorsMappings(registry: CorsRegistry) {
//                registry.addMapping("/**")
//                    .allowedOrigins(serverIp)
//                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
//                    .allowedHeaders("*")
//                    .allowCredentials(true)
//            }
//        }
//    }
}
