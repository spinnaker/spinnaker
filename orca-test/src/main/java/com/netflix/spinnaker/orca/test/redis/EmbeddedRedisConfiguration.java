package com.netflix.spinnaker.orca.test.redis;

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

@Configuration
public class EmbeddedRedisConfiguration {
  @Bean(destroyMethod = "destroy") public EmbeddedRedis redisServer() {
    EmbeddedRedis redis = EmbeddedRedis.embed();
    try (Jedis jedis = redis.getJedis()) {
      jedis.flushAll();
    }
    return redis;
  }

  @Bean public Pool<Jedis> jedisPool() {
    return redisServer().getPool();
  }

  @Bean public RedisClientDelegate redisClientDelegate(Pool<Jedis> jedisPool) {
    return new JedisClientDelegate("primaryDefault", jedisPool);
  }
}
