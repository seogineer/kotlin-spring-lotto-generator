package com.seogineer.kotlinspringlottogenerator.config

import org.springframework.cache.Cache
import java.util.concurrent.Callable

/**
 * Redis 장애를 흉내 내는 캐시: get/put/evict/clear가 모두 예외를 던진다.
 * 이름(getName)과 getNativeCache는 오류 처리기가 로그에 쓰므로 정상 동작한다.
 * invalidate()/evictIfPresent()는 재정의하지 않는다. 기본 구현이 clear()/evict()를 호출하므로
 * allEntries + beforeInvocation evict 경로에서도 예외가 난다 (조용히 성공하는 스텁이 되지 않도록).
 */
class BrokenCache(private val name: String) : Cache {
    var operations = 0
        private set

    private fun fail(op: String): Nothing {
        operations++
        throw IllegalStateException("Redis 연결 실패 (테스트, $op)")
    }

    override fun getName(): String = name

    override fun getNativeCache(): Any = this

    override fun get(key: Any): Cache.ValueWrapper? = fail("get")

    override fun <T : Any?> get(key: Any, type: Class<T>?): T? = fail("get")

    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T? = fail("get")

    override fun put(key: Any, value: Any?) = fail("put")

    override fun evict(key: Any) = fail("evict")

    override fun clear() = fail("clear")

    companion object {
        val CACHE_NAMES = listOf("drawings", "mostFrequentNumbers", "topNumbersPerPosition", "frequenciesPerPosition")
    }
}
