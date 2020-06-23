/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.echo.telemetry

import com.netflix.spinnaker.echo.config.TelemetryConfig.TelemetryConfigProps
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisException
import redis.clients.jedis.util.Pool
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class InstanceIdSupplierTest {
  @Test
  fun `returns the configured value with no redis backend`() {
    val instanceIdSupplier = InstanceIdSupplier(withInstanceId("my-id"), null)

    expectThat(instanceIdSupplier.uniqueId).isEqualTo("my-id")
  }

  @Test
  fun `returns the configured value when no current value in redis`() {
    EmbeddedRedis.embed().use { embeddedRedis ->
      val instanceIdSupplier = InstanceIdSupplier(withInstanceId("my-id"), getRedis(embeddedRedis))
      expectThat(instanceIdSupplier.uniqueId).isEqualTo("my-id")
    }
  }

  @Test
  fun `returns the value in redis if it exists`() {
    EmbeddedRedis.embed().use { embeddedRedis ->
      val redisSelector = getRedis(embeddedRedis)

      val instanceIdSupplier = InstanceIdSupplier(withInstanceId("my-id"), redisSelector)
      expectThat(instanceIdSupplier.uniqueId).isEqualTo("my-id")

      val otherInstanceIdSupplier = InstanceIdSupplier(withInstanceId("my-new-id"), redisSelector)
      expectThat(otherInstanceIdSupplier.uniqueId).isEqualTo("my-id")
    }
  }

  @Test
  fun `returns the configured value if the selector is unable to find the configured redis`() {
    val instanceIdSupplier = InstanceIdSupplier(withInstanceId("my-id"), getMissingRedis())
    expectThat(instanceIdSupplier.uniqueId).isEqualTo("my-id")
  }

  @Test
  fun `returns the configured value if a redis exception occurs`() {
    val instanceIdSupplier = InstanceIdSupplier(withInstanceId("my-id"), getBadRedis())
    expectThat(instanceIdSupplier.uniqueId).isEqualTo("my-id")
  }

  private fun withInstanceId(id: String): TelemetryConfigProps {
    val result = TelemetryConfigProps()
    result.instanceId = id
    return result
  }

  private fun getRedis(embeddedRedis: EmbeddedRedis): RedisClientSelector {
    val redisClientDelegate = JedisClientDelegate("primaryDefault", embeddedRedis.pool)
    return RedisClientSelector(listOf(redisClientDelegate))
  }

  private fun getMissingRedis(): RedisClientSelector {
    return RedisClientSelector(listOf())
  }

  private fun getBadRedis(): RedisClientSelector {
    val jedis = mockk<Jedis>()
    every { jedis.setnx(any<String>(), any<String>()) } throws JedisException("An error occurred")

    val pool = mockk<Pool<Jedis>>()
    every { pool.resource } returns jedis

    val delegate = JedisClientDelegate("primaryDefault", pool)

    return RedisClientSelector(listOf(delegate))
  }
}
