package com.netflix.spinnaker.gate.config

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisConnectionFactory
import redis.clients.jedis.JedisPool

@Configuration
class RedisTestConfig {

  @Bean(destroyMethod = "destroy")
  EmbeddedRedis embeddedRedis() {
    EmbeddedRedis redis = EmbeddedRedis.embed()
    redis.jedis.withCloseable { jedis ->
      jedis.connect()
      jedis.ping()
    }
    return redis
  }

  @Bean
  @Primary
  @SpringSessionRedisConnectionFactory
  JedisConnectionFactory jedisConnectionFactory(EmbeddedRedis embeddedRedis) {
    return new JedisConnectionFactory(new RedisStandaloneConfiguration(embeddedRedis.host, embeddedRedis.port))
  }

  @Bean
  @Primary
  JedisPool jedis(EmbeddedRedis embeddedRedis) {
    return embeddedRedis.getPool();
  }
}
