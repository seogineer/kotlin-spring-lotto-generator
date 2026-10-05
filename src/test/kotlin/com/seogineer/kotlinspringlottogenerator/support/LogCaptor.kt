package com.seogineer.kotlinspringlottogenerator.support

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * 지정한 클래스 로거들에 logback ListAppender를 붙여 block 실행 중 로그를 수집한다 (기존 워머 로그 테스트 패턴).
 * Spring Boot 로깅 초기화가 appender를 떼어 내므로, 컨텍스트가 뜬 뒤(테스트 본문 안에서) 호출해야 한다.
 * 여러 로거에 같은 appender를 붙이므로 반환 목록은 로거 간 발생 순서를 유지한다.
 */
object LogCaptor {

    fun capture(vararg loggerClasses: Class<*>, block: () -> Unit): List<ILoggingEvent> {
        val loggers = loggerClasses.map { LoggerFactory.getLogger(it) as Logger }
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        loggers.forEach { it.addAppender(appender) }
        try {
            block()
        } finally {
            loggers.forEach { it.detachAppender(appender) }
            appender.stop()
        }
        return appender.list.toList()
    }

    /**
     * block 실행 중 로거 레벨을 고정하고 끝나면 원래 레벨(null 가능)로 되돌린다.
     * logback 기본 설정은 root DEBUG이고 Spring Boot 컨텍스트가 뜬 뒤에는 INFO라서,
     * 레벨에 따라 달라지는 로그를 검증할 때는 실행 순서에 의존하지 않도록 레벨을 명시한다.
     */
    fun <T> withLevel(loggerClass: Class<*>, level: Level, block: () -> T): T {
        val logger = LoggerFactory.getLogger(loggerClass) as Logger
        val previous = logger.level
        logger.level = level
        try {
            return block()
        } finally {
            logger.level = previous
        }
    }
}

fun List<ILoggingEvent>.atLevel(level: Level): List<ILoggingEvent> = filter { it.level == level }

fun List<ILoggingEvent>.messages(level: Level): List<String> = atLevel(level).map { it.formattedMessage }
