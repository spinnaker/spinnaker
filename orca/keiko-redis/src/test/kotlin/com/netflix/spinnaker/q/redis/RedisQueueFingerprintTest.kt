/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.q.redis

import com.google.common.hash.Hashing
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.EnumTestMessage
import com.netflix.spinnaker.q.EnumTestMessage.ExecutionType.PIPELINE
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Clock
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.util.Pool
import tools.jackson.databind.json.JsonMapper

class RedisQueueFingerprintTest {
  private val historicalFingerprint = Hashing.murmur3_128().hashString(
    """v2:{"ackTimeoutMs":null,"executionType":"PIPELINE","kind":"enumTest"}""",
    UTF_8
  ).toString()

  @Test
  fun `Redis and Redis Cluster preserve Jackson 2 enum fingerprints`() {
    val redis = mock<Jedis>()
    val pool = mock<Pool<Jedis>>()
    whenever(pool.resource).thenReturn(redis)
    whenever(redis.scriptLoad(any<String>())).thenReturn("script")
    val cluster = mock<JedisCluster>()
    whenever(cluster.scriptLoad(any<String>(), any<String>())).thenReturn("script")
    val mapper = JsonMapper.builder().build()
    val queues = listOf(
      RedisQueue(
        queueName = "fingerprint",
        pool = pool,
        clock = Clock.systemUTC(),
        mapper = mapper,
        serializationMigrator = Optional.empty(),
        deadMessageHandlers = emptyList(),
        publisher = mock()
      ),
      RedisClusterQueue(
        queueName = "fingerprint",
        jedisCluster = cluster,
        clock = Clock.systemUTC(),
        mapper = mapper,
        serializationMigrator = Optional.empty(),
        deadMessageHandlers = emptyList(),
        publisher = mock()
      )
    )

    queues.forEach { queue ->
      with(queue) {
        val message = EnumTestMessage(PIPELINE)
        assertThat(message.fingerprint().latest).isEqualTo(historicalFingerprint)
        message.setAttribute(AttemptsAttribute(2))
        assertThat(message.fingerprint().latest).isEqualTo(historicalFingerprint)
      }
    }
  }
}
