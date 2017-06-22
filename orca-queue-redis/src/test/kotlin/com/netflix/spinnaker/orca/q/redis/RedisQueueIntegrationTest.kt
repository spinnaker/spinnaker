/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.q.redis

import com.netflix.spinnaker.config.RedisQueueConfiguration
import com.netflix.spinnaker.orca.q.QueueIntegrationTest
import com.netflix.spinnaker.orca.q.memory.InMemoryQueue
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.junit.runner.RunWith
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

/**
 * This just runs [QueueIntegrationTest] with a [RedisQueue] instead of an
 * [InMemoryQueue].
 */
@RunWith(SpringJUnit4ClassRunner::class)
@ContextConfiguration(classes = arrayOf(
  EmbeddedRedisConfiguration::class,
  RedisQueueConfiguration::class,
  RedisQueuePoolFixery::class
))
class RedisQueueIntegrationTest : QueueIntegrationTest()



@Configuration
open class RedisQueuePoolFixery {
  @Bean(name = arrayOf("queueJedisPool")) open fun redisQueue(pool: Pool<Jedis>) = pool
}
