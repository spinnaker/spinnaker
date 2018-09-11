package com.netflix.spinnaker.keel.redis.spring

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

@Configuration
class EmbeddedRedisConfiguration {
  @Bean(destroyMethod = "destroy")
  fun redisServer(): EmbeddedRedis =
    EmbeddedRedis.embed().apply {
      jedis.use { jedis -> jedis.flushAll() }
    }

  @Bean
  fun redisPool(redisServer: EmbeddedRedis): Pool<Jedis> {
    log.info("[redisPool] Using embedded Redis server on port {}", redisServer.port)
    return redisServer.pool
  }

  @Bean
  fun queueRedisPool(
    redisServer: EmbeddedRedis
  ): Pool<Jedis> {
    log.info("[queueRedisPool] using embedded Redis server on port {}", redisServer.port)
    return redisServer.pool
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
