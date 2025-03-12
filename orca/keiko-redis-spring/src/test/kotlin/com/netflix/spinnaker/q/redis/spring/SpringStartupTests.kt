package com.netflix.spinnaker.q.redis.spring

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.QueueConfiguration
import com.netflix.spinnaker.config.RedisQueueConfiguration
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.redis.RedisQueue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.junit.jupiter.SpringExtension
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import redis.clients.jedis.JedisPool

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [QueueConfiguration::class, RedisQueueConfiguration::class, TestConfiguration::class],
  webEnvironment = NONE
)
internal class SpringStartupTests {

  @Autowired
  lateinit var queue: Queue

  @Test
  fun `starts up successfully`() {
    Assertions.assertThat(queue).isNotNull.isInstanceOf(RedisQueue::class.java)
  }
}
@Configuration
internal class TestConfiguration {
  @Bean
  fun registry(): Registry = NoopRegistry()

  @Bean
  fun queueRedisPool(): JedisPool = EmbeddedRedis.embed().pool
}
