/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.sql.health

import com.netflix.spectator.api.Registry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * Continuously verifies connectivity to the database.
 *
 * The provider will poll for connectivity regularly, and requires consecutive [healthyThreshold] to become
 * healthy, and similarly consecutive [unhealthyThreshold] to become unhealthy.
 */
class SqlHealthProvider(
  private val jooq: DSLContext,
  private val registry: Registry,
  private val readOnly: Boolean,
  private val unhealthyThreshold: Int = 2,
  private val healthyThreshold: Int = 10
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @Suppress("VariableNaming")
  internal val _enabled = AtomicBoolean(false)
  private val _healthException: AtomicReference<Exception> = AtomicReference()

  private val healthyCounter = AtomicInteger(0)
  private val unhealthyCounter = AtomicInteger(0)

  private val invocationId = registry.createId("sql.healthProvider.invocations")

  /**
   * Returns the enabled state of the health provider.
   */
  val enabled: Boolean
    get() = _enabled.get()

  /**
   * Returns the latest exception, if any, that was raised as part of the health provider's check.
   */
  val healthException: Exception?
    get() = _healthException.get()

  /**
   * Perform a single connectivity check.
   *
   * If the application is connected to a read-only replica, this check will be a SELECT. If connected to
   * a writer instance a DELETE will be performed against an empty healthcheck table.
   */
  @Scheduled(fixedDelay = 1_000)
  fun performCheck() {
    try {
      // THIS IS VERY ADVANCED
      if (readOnly) {
        jooq.select().from(DSL.table("healthcheck")).limit(1)
      } else {
        jooq.delete(DSL.table("healthcheck")).execute()
      }

      if (!_enabled.get()) {
        if (healthyCounter.incrementAndGet() >= healthyThreshold) {
          _enabled.set(true)
          _healthException.set(null)
          log.info("Enabling after $healthyThreshold healthy cycles")
        }
      }
      unhealthyCounter.set(0)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
      _healthException.set(e)
      healthyCounter.set(0)
      unhealthyCounter.incrementAndGet().also { unhealthyCount ->
        log.error("Encountered exception, $unhealthyCount/$unhealthyThreshold failures", e)
        if (unhealthyCount >= unhealthyThreshold && _enabled.get()) {
          log.warn("Encountered exception, disabling after $unhealthyCount failures")
          _enabled.set(false)
        }
      }
    } finally {
      registry.counter(invocationId.withTag("status", if (enabled) "enabled" else "disabled")).increment()
    }
  }
}
