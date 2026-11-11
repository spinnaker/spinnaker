/*
 * Copyright 2026 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.clouddriver.sql

import com.netflix.spinnaker.config.SqlTaskLeaseProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.jooq.impl.DSL.field
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNull

class SqlTaskLeaseManagerTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after { SqlTestUtil.cleanupDb(database.context) }

    test("registers the instance with epoch 0 and renews without bumping the epoch") {
      manager.registerOrHeartbeat()
      expectThat(manager.epoch).isEqualTo(0L)

      val expiresBefore = leaseExpiresAt()
      Thread.sleep(5)
      manager.registerOrHeartbeat()
      expectThat(manager.epoch).isEqualTo(0L)
      expectThat(leaseExpiresAt()).isGreaterThan(expiresBefore)
    }

    test("re-registering after the row was reaped bumps the epoch") {
      manager.registerOrHeartbeat()
      expectThat(manager.epoch).isEqualTo(0L)

      // The reaper declares the instance dead.
      database.context
        .update(clouddriverInstancesTable)
        .set(field("state"), INSTANCE_DEAD)
        .where(field("instance_id").eq(manager.instanceId))
        .execute()

      manager.registerOrHeartbeat()
      expectThat(manager.epoch).isEqualTo(1L)
      expectThat(instanceState()).isEqualTo(INSTANCE_ACTIVE)
    }

    test("mayWrite fences a leased task once the epoch is superseded") {
      manager.registerOrHeartbeat()
      expectThat(manager.mayWrite(manager.instanceId, 0L)).isEqualTo(true)
      // Legacy tasks without a lease are never fenced.
      expectThat(manager.mayWrite(null, null)).isEqualTo(true)

      database.context
        .update(clouddriverInstancesTable)
        .set(field("state"), INSTANCE_DEAD)
        .where(field("instance_id").eq(manager.instanceId))
        .execute()
      manager.registerOrHeartbeat()

      expectThat(manager.mayWrite(manager.instanceId, 0L)).isEqualTo(false)
      expectThat(manager.mayWrite(manager.instanceId, 1L)).isEqualTo(true)
    }
  }

  inner class Fixture {
    val database = SqlTestUtil.initTcMysqlDatabase()!!
    val properties =
      SqlTaskLeaseProperties().apply {
        heartbeatIntervalMs = 20_000
        leaseTtlMs = 120_000
        shutdownWaitSeconds = 0
      }
    val manager = SqlTaskLeaseManager(database.context, mockk(relaxed = true), properties)

    fun leaseExpiresAt(): Long =
      database.context
        .select(field("lease_expires_at"))
        .from(clouddriverInstancesTable)
        .where(field("instance_id").eq(manager.instanceId))
        .fetchOne(field("lease_expires_at"), Long::class.javaObjectType)!!

    fun instanceState(): String =
      database.context
        .select(field("state"))
        .from(clouddriverInstancesTable)
        .where(field("instance_id").eq(manager.instanceId))
        .fetchOne(field("state"), String::class.java)!!
  }
}
