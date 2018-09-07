package com.netflix.spinnaker.keel.redis.spring

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
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
  fun redisPool(): Pool<Jedis> = redisServer().pool
}
