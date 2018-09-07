package com.netflix.spinnaker.keel.redis

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.RuleEngineApp
import com.netflix.spinnaker.keel.registry.PluginRepository
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.junit4.SpringRunner
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import strikt.api.expect
import strikt.assertions.isA

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [RuleEngineApp::class],
  webEnvironment = RANDOM_PORT,
  properties = ["redis.connection=redis://localhost:6379"]
)
internal class SpringStartupTests {

  @Autowired
  lateinit var pluginRepository: PluginRepository

  @Test
  fun `uses RedisPluginRepository`() {
    expect(pluginRepository).isA<RedisPluginRepository>()
  }
}

@Configuration
class MockEurekaConfig {
  @MockBean
  lateinit var eurekaClient: EurekaClient

  @Bean
  fun currentInstance(): InstanceInfo = InstanceInfo.Builder
    .newBuilder()
    .run {
      setAppName("keel")
      setASGName("keel-local")
      build()
    }
}

@Configuration
class EmbeddedRedisConfiguration {
  @Bean(destroyMethod = "destroy")
  fun redisServer(): EmbeddedRedis {
    val redis = EmbeddedRedis.embed()
    redis.jedis.use { jedis -> jedis.flushAll() }
    return redis
  }

  @Bean
  fun redisPool(): Pool<Jedis> = redisServer().pool
//
//  @Bean
//  fun redisClientDelegate(jedisPool: Pool<Jedis>): RedisClientDelegate {
//    return JedisClientDelegate("primaryDefault", jedisPool)
//  }
}
