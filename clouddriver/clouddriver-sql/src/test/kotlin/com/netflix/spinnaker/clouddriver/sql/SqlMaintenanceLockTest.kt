/*
 * Copyright 2026 DoorDash, Inc.
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
package com.netflix.spinnaker.clouddriver.sql

import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class SqlMaintenanceLockTest {

  private class MutableClock(var now: Long) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
  }

  private fun node(id: String) = object : NodeIdentity {
    override fun getNodeIdentity() = id
  }

  private lateinit var database: com.netflix.spinnaker.kork.sql.test.SqlTestUtil.TestDatabase
  private val clock = MutableClock(1_000_000L)

  private lateinit var a: SqlMaintenanceLock
  private lateinit var b: SqlMaintenanceLock

  @BeforeEach
  fun setUp() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable)
    database = SqlTestUtil.initTcMysqlDatabase()!!
    a = SqlMaintenanceLock(database.context, node("node-a"), null, clock)
    b = SqlMaintenanceLock(database.context, node("node-b"), null, clock)
  }

  @AfterEach
  fun tearDown() {
    if (this::database.isInitialized) {
      SqlTestUtil.cleanupDb(database.context)
    }
  }

  private fun owner(agent: String): String? =
    database.context.select(field("owner_id")).from(table("cats_agent_locks"))
      .where(field("agent_name").eq(agent)).fetchOne()?.get(0)?.toString()

  private fun expiry(agent: String): Long? =
    database.context.select(field("lock_expiry")).from(table("cats_agent_locks"))
      .where(field("agent_name").eq(agent)).fetchOne()?.get(0)?.toString()?.toLong()

  @Test
  fun `acquire succeeds and contender is rejected`() {
    expectThat(a.tryAcquire("Agent", 10_000)).isTrue()
    expectThat(b.tryAcquire("Agent", 10_000)).isFalse()
    expectThat(owner("Agent")).isEqualTo("node-a")
  }

  @Test
  fun `independent agents do not contend`() {
    expectThat(a.tryAcquire("Agent1", 10_000)).isTrue()
    expectThat(b.tryAcquire("Agent2", 10_000)).isTrue()
  }

  @Test
  fun `another owner acquires after expiry`() {
    expectThat(a.tryAcquire("Agent", 10_000)).isTrue()
    clock.now += 10_001
    expectThat(b.tryAcquire("Agent", 10_000)).isTrue()
    expectThat(owner("Agent")).isEqualTo("node-b")
  }

  @Test
  fun `hold extends only the owners lock`() {
    expectThat(a.tryAcquire("Agent", 10_000)).isTrue()

    b.hold("Agent", clock.now + 999_999)
    expectThat(expiry("Agent")).isEqualTo(1_010_000L)

    a.hold("Agent", clock.now + 60_000)
    expectThat(expiry("Agent")).isEqualTo(1_060_000L)

    // The extended lock keeps contenders out past the original ttl.
    clock.now += 30_000
    expectThat(b.tryAcquire("Agent", 10_000)).isFalse()
  }

  @Test
  fun `release is owner checked`() {
    expectThat(a.tryAcquire("Agent", 10_000)).isTrue()

    b.release("Agent")
    expectThat(owner("Agent")).isEqualTo("node-a")

    a.release("Agent")
    expectThat(owner("Agent")).isEqualTo(null)
    expectThat(b.tryAcquire("Agent", 10_000)).isTrue()
  }

  @Test
  fun `namespaced table is created and used`() {
    val na = SqlMaintenanceLock(database.context, node("node-a"), "ns1", clock)
    val nb = SqlMaintenanceLock(database.context, node("node-b"), "ns1", clock)

    expectThat(na.tryAcquire("Agent", 10_000)).isTrue()
    expectThat(nb.tryAcquire("Agent", 10_000)).isFalse()
    expectThat(database.context.fetchCount(table("cats_agent_locks_ns1"))).isEqualTo(1)
    // base table untouched
    expectThat(database.context.fetchCount(table("cats_agent_locks"))).isEqualTo(0)

    na.release("Agent")
    expectThat(nb.tryAcquire("Agent", 10_000)).isTrue()
    database.context.execute("DROP TABLE cats_agent_locks_ns1")
  }
}
