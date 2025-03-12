/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.api.test

import com.netflix.spinnaker.gate.Main
import dev.minutest.TestContextBuilder
import dev.minutest.TestDescriptor
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.TestContextManager
import org.springframework.test.context.TestPropertySource
import org.springframework.context.annotation.Bean
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisConnectionFactory
import org.springframework.test.context.ContextConfiguration
import redis.clients.jedis.JedisPool

@SpringBootTest(classes = [Main::class])
@ContextConfiguration(classes = [GateFixtureConfiguration::class])
@TestPropertySource(properties = ["spring.config.location=classpath:gate-test-app.yml"])
abstract class GateFixture

/**
 * DSL for constructing a GateFixture within a Minutest suite.
 */
inline fun <PF, reified F> TestContextBuilder<PF, F>.gateFixture(
  crossinline factory: (Unit).(testDescriptor: TestDescriptor) -> F
) {
  fixture { testDescriptor ->
    factory(testDescriptor).also {
      TestContextManager(F::class.java).prepareTestInstance(it)
    }
  }
}

@TestConfiguration
internal open class GateFixtureConfiguration {
  @Bean(destroyMethod = "destroy")
  fun embeddedRedis(): EmbeddedRedis {
    return EmbeddedRedis.embed().also { redis -> redis.jedis.connect() }.also { redis -> redis.jedis.ping() }
  }

  @Bean
  @Primary
  @SpringSessionRedisConnectionFactory
  fun jedisConnectionFactory(embeddedRedis: EmbeddedRedis): JedisConnectionFactory {
    return JedisConnectionFactory(RedisStandaloneConfiguration(embeddedRedis.host, embeddedRedis.port))
  }

  @Bean
  @Primary
  fun jedis(embeddedRedis: EmbeddedRedis): JedisPool {
    return embeddedRedis.getPool();
  }
}
