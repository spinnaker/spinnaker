package com.netflix.spinnaker.gate.config

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Component
class RedisTestConfig {

  EmbeddedRedis redis

  int port

  @PostConstruct
  void startRedis() {
    redis = EmbeddedRedis.embed()
    port = redis.port
    System.setProperty("redis.connection", "redis://localhost:${port}")
  }

  @PreDestroy
  void stopRedis() {
    redis?.destroy()
  }
}
