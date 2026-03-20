/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.cats.sql.cache

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.provider.DefaultProviderRegistry
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.cats.test.TestAgent
import com.netflix.spinnaker.cats.test.TestProvider
import com.netflix.spinnaker.cats.cluster.NoopShardingFilter
import com.netflix.spinnaker.cats.cluster.ShardingFilter
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS
import com.netflix.spinnaker.config.SqlConstraints
import com.netflix.spinnaker.config.SqlConstraintsInitializer
import com.netflix.spinnaker.config.SqlConstraintsProperties
import com.netflix.spinnaker.config.SqlUnknownAgentCleanupProperties
import com.netflix.spinnaker.cats.sql.cluster.SqlCachingPodsObserver
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import org.jooq.SQLDialect
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.ObjectProvider
import org.testcontainers.DockerClientFactory
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

/**
 * Tests for SqlUnknownAgentCleanupAgent.
 *
 * These tests verify that the cleanup agent correctly identifies and removes cache records
 * for agents that are no longer configured, while respecting sharding boundaries and
 * various safety guards.
 *
 * Test data setup:
 * - "test" account: agent type "test/TestAgent", data in cats_v1_instances table
 * - "prod" account: agent type "prod/TestAgent", data in cats_v1_instances table
 * - "staging" account: agent type "staging/TestAgent" (used in some sharding tests)
 */
class SqlUnknownAgentCleanupAgentTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    beforeAll {
      assumeTrue(DockerClientFactory.instance().isDockerAvailable)
    }

    after {
      SqlTestUtil.cleanupDb(dslContext)
    }

    // Basic cleanup behavior - when all agents are registered, nothing should be deleted
    context("test and prod accounts exist") {
      deriveFixture {
        fixture.providerAgents.addAll(
          listOf(
            testCachingAgent(),
            prodCachingAgent()
          )
        )
        seedDatabase(includeTestAccount = true, includeProdAccount = true)
        fixture
      }

      test("nothing happens") {
        expect {
          that(selectAllResources()).describedAs("initial resources").hasSize(2)
          that(selectAllRels()).describedAs("initial relationships").hasSize(2)
        }

        subject().run()

        expect {
          that(selectAllResources()).describedAs("modified resources").hasSize(2)
          that(selectAllRels()).describedAs("modified relationships").hasSize(2)
        }
      }

      context("test account is removed") {
        modifyFixture {
          fixture.providerAgents.removeIf { it.scope == "test" }
        }

        before { subject().run() }

        test("relationships referencing old data are deleted") {
          expectThat(selectAllResources())
            .hasSize(1)[0].isEqualTo("aws:instances:prod:us-east-1:i-abcd1234")
        }

        test("resources referencing old data are deleted") {
          expectThat(selectAllRels())
            .hasSize(1)[0].isEqualTo("aws:serverGroups:myapp-prod:prod:us-east-1:myapp-prod-v000")
        }
      }
    }
    context("add caching agent for uninstantiated custom resource definition") {
      deriveFixture {
        fixture.providerAgents.add(unregisteredCustomResourceCachingAgent())
        fixture
      }

      test("error is not thrown when table does not exist for type for which agent is authoritative") {
        assertDoesNotThrow {
          subject().run()
        }
      }
    }

    // Sharding-aware cleanup: ensures each pod only cleans up records for agents it handles.
    // This prevents Pod A from deleting records that Pod B is responsible for.
    context("sharding-aware cleanup") {
      deriveFixture {
        fixture.providerAgents.add(prodCachingAgent())
        seedDatabase(includeTestAccount = true, includeProdAccount = true)
        fixture
      }

      test("preserves other pods' data when sharding filter denies unknown agent") {
        // Simulate a pod that only handles "prod" accounts - it should NOT delete "test" data
        // even though "test/TestAgent" is not in the registry (it belongs to another pod)
        val shardingFilter = object : ShardingFilter {
          override fun filter(agent: Agent): Boolean = agent.agentType.startsWith("prod/")
        }

        subject(shardingFilter = shardingFilter).run()

        // Both records preserved: prod (known agent) and test (not our responsibility)
        expectThat(selectAllResources()).hasSize(2)
        expectThat(selectAllRels()).hasSize(2)
      }

      test("deletes unknown rows for handled accounts") {
        // Simulate a pod that handles "prod" and "staging" accounts.
        // "staging/TestAgent" is unknown (not in registry) AND this pod handles it -> should delete
        val shardingFilter = object : ShardingFilter {
          override fun filter(agent: Agent): Boolean =
            agent.agentType.startsWith("prod/") || agent.agentType.startsWith("staging/")
        }

        // Add only staging data (prod+test already exist from deriveFixture)
        val now = System.currentTimeMillis()
        dslContext.insertInto(table(defaultSqlNames().resourceTableName("instances")))
          .columns(field("id"), field("agent"), field("application"), field("body_hash"), field("body"), field("last_updated"))
          .values("aws:instances:staging:us-east-1:i-abcd1234", "staging/TestAgent", "myapp", "", "", now)
          .execute()
        dslContext.insertInto(table(defaultSqlNames().relTableName("instances")))
          .columns(field("uuid"), field("id"), field("rel_id"), field("rel_agent"), field("rel_type"), field("last_updated"))
          .values(
            ULID().nextULID(),
            "aws:instances:staging:us-east-1:i-abcd1234",
            "aws:serverGroups:myapp-staging:staging:us-east-1:myapp-staging-v000",
            "serverGroups:staging/TestAgent",
            "serverGroups",
            now
          )
          .execute()

        subject(shardingFilter = shardingFilter).run()

        // prod remains (known agent), test remains (not handled by this pod's filter),
        // staging was deleted (unknown AND handled by this pod)
        expectThat(selectAllResources()).hasSize(2)
        expectThat(selectAllRels()).hasSize(2)
      }
    }

    // Safety guard: SqlCachingPodsObserver needs time to discover peer pods via heartbeat.
    // Until the topology is known (podIndex >= 0, podCount > 0), we must not clean up.
    context("safety: sharding state unknown") {
      deriveFixture {
        fixture.providerAgents.add(prodCachingAgent())
        seedDatabase(includeTestAccount = true, includeProdAccount = true)
        fixture
      }

      test("skips run when sql sharding state is not established") {
        // Simulate a freshly-started pod where heartbeat hasn't completed yet
        val observer = mockk<SqlCachingPodsObserver>()
        every { observer.filter(any()) } returns true
        every { observer.getPodIndex() } returns -1  // Not yet determined
        every { observer.getPodCount() } returns 0   // No peers discovered

        subject(shardingFilter = observer).run()

        // All records preserved - cleanup was skipped entirely
        expectThat(selectAllResources()).hasSize(2)
        expectThat(selectAllRels()).hasSize(2)
      }
    }

    // Safety guard: If no agents are registered at all (e.g., during startup race),
    // don't delete anything - we'd incorrectly think ALL records are orphaned.
    context("empty registry") {
      deriveFixture {
        seedDatabase(includeTestAccount = true, includeProdAccount = true)
        // Note: no agents added to providerAgents
        fixture
      }

      test("skips cleanup when no agents are registered") {
        subject().run()

        // All records preserved - cleanup was skipped
        expectThat(selectAllResources()).hasSize(2)
        expectThat(selectAllRels()).hasSize(2)
      }
    }

    // Safety guard: Detect misconfiguration where cache-sharding.enabled=true but
    // the SqlCachingPodsObserver bean wasn't created (fell back to NoopShardingFilter).
    // NoopShardingFilter allows all agents, so we'd delete records belonging to other pods.
    context("safety: sharding enabled but noop filter") {
      deriveFixture {
        fixture.providerAgents.add(prodCachingAgent())
        seedDatabase(includeTestAccount = true, includeProdAccount = true)
        fixture
      }

      test("skips run when sharding is enabled but noop filter is used") {
        subject(shardingFilter = NoopShardingFilter(), shardingEnabled = true).run()

        // All records preserved - cleanup was skipped due to misconfiguration detection
        expectThat(selectAllResources()).hasSize(2)
        expectThat(selectAllRels()).hasSize(2)
      }
    }

    // Configuration options: age guard, dry-run mode, and data type exclusions
    context("age guard, dry-run, exclusions") {
      deriveFixture {
        fixture.providerAgents.add(prodCachingAgent())
        seedDatabase(includeTestAccount = true, includeProdAccount = true)
        fixture
      }

      test("skips recent records when minRecordAgeSeconds is set") {
        // minRecordAgeSeconds=300 means records must be >5 minutes old to be deleted.
        // We set test/TestAgent's last_updated to 600 seconds ago (older than cutoff).
        // prod/TestAgent remains at current time (too recent, but also known agent).
        val props = defaultCleanupProperties().apply { minRecordAgeSeconds = 300 }
        dslContext.update(table(defaultSqlNames().resourceTableName("instances")))
          .set(field("last_updated"), System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(600))
          .where(field("agent").eq("test/TestAgent"))
          .execute()

        subject(cleanupProperties = props).run()

        // test/TestAgent record deleted (unknown + old enough); prod/TestAgent preserved (known)
        expectThat(selectAllResources()).hasSize(1)[0].isEqualTo("aws:instances:prod:us-east-1:i-abcd1234")
      }

      test("does not delete when dryRun is enabled") {
        // dryRun=true logs what would be deleted but doesn't actually delete
        val props = defaultCleanupProperties().apply { dryRun = true }

        subject(cleanupProperties = props).run()

        // All records preserved - deletion was simulated only
        expectThat(selectAllResources()).hasSize(2)
        expectThat(selectAllRels()).hasSize(2)
      }

      test("skips excluded data types") {
        // excludedDataTypes allows operators to protect specific cache types from cleanup
        val props = defaultCleanupProperties().apply {
          excludedDataTypes = listOf("instances")
        }

        subject(cleanupProperties = props).run()

        // All records preserved - "instances" data type was excluded
        expectThat(selectAllResources()).hasSize(2)
        expectThat(selectAllRels()).hasSize(2)
      }
    }

    // Operational behavior: batching, null timestamp handling, and property wiring
    context("batching, null timestamps, and wiring") {
      deriveFixture {
        fixture.providerAgents.add(prodCachingAgent())
        seedDatabase(includeTestAccount = true, includeProdAccount = true)
        fixture
      }

      test("uses delete batch size when cleaning multiple unknown rows") {
        // deleteBatchSize controls how many records are deleted per SQL statement.
        // Add additional unknown rows to verify batching works correctly.
        val now = System.currentTimeMillis()
        val resourceTable = defaultSqlNames().resourceTableName("instances")
        val relTable = defaultSqlNames().relTableName("instances")

        listOf("i-extra1", "i-extra2").forEach { suffix ->
          dslContext.insertInto(table(resourceTable))
            .columns(
              field("id"), field("agent"), field("application"), field("body_hash"), field("body"), field("last_updated")
            )
            .values(
              "aws:instances:test:us-east-1:$suffix",
              "test/TestAgent",
              "myapp",
              "",
              "",
              now
            )
            .execute()

          dslContext.insertInto(table(relTable))
            .columns(
              field("uuid"),
              field("id"),
              field("rel_id"),
              field("rel_agent"),
              field("rel_type"),
              field("last_updated")
            )
            .values(
              ULID().nextULID(),
              "aws:instances:test:us-east-1:$suffix",
              "aws:serverGroups:myapp-test:test:us-east-1:myapp-test-v000",
              "serverGroups:test/TestAgent",
              "serverGroups",
              now
            )
            .execute()
        }

        val props = defaultCleanupProperties().apply {
          deleteBatchSize = 1
          minRecordAgeSeconds = 0
        }

        subject(cleanupProperties = props).run()

        expectThat(selectAllResources()).hasSize(1)[0].isEqualTo("aws:instances:prod:us-east-1:i-abcd1234")
        expectThat(selectAllRels()).hasSize(1)[0].isEqualTo("aws:serverGroups:myapp-prod:prod:us-east-1:myapp-prod-v000")
      }

      test("handles null last_updated on relationships when pruning unknown agents") {
        // When minRecordAgeSeconds > 0, records with NULL last_updated are skipped
        // to avoid accidentally deleting newly-inserted data that hasn't been timestamped.
        // However, the RESOURCE table record (which has a valid timestamp) should still be cleaned.
        dslContext.update(table(defaultSqlNames().relTableName("instances")))
          .set(field("last_updated"), null as Long?)
          .where(field("rel_agent").eq("serverGroups:test/TestAgent"))
          .execute()

        // Make the resource old enough to be eligible for cleanup (older than minRecordAgeSeconds)
        dslContext.update(table(defaultSqlNames().resourceTableName("instances")))
          .set(field("last_updated"), System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(600))
          .where(field("agent").eq("test/TestAgent"))
          .execute()

        val props = defaultCleanupProperties().apply { minRecordAgeSeconds = 300 }

        subject(cleanupProperties = props).run()

        // Resource cleaned (has old timestamp); relationship with null timestamp skipped gracefully
        expectThat(selectAllResources()).hasSize(1)[0].isEqualTo("aws:instances:prod:us-east-1:i-abcd1234")
        // Relationship with null timestamp was skipped, but prod's relationship remains
        expectThat(selectAllRels()).hasSize(2)
      }

      test("poll and timeout use property overrides") {
        val props = defaultCleanupProperties().apply {
          pollIntervalSeconds = 7
          timeoutSeconds = 9
        }

        val agent = subject(cleanupProperties = props)

        expectThat(agent.getPollIntervalMillis()).isEqualTo(TimeUnit.SECONDS.toMillis(7))
        expectThat(agent.getTimeoutMillis()).isEqualTo(TimeUnit.SECONDS.toMillis(9))
      }
    }
  }

  fun defaultSqlNames() : SqlNames =
    SqlNames(sqlConstraints = SqlConstraintsInitializer.getDefaultSqlConstraints(SQLDialect.MYSQL))


  private inner class Fixture {
    val testDatabase = SqlTestUtil.initTcMysqlDatabase()
    val dslContext = testDatabase.context

    val providerAgents: MutableList<TestAgent> = mutableListOf()
    val providerRegistry: ProviderRegistry = DefaultProviderRegistry(
      listOf(TestProvider(providerAgents as Collection<CachingAgent>)),
      InMemoryNamedCacheFactory()
    )
    val registry = NoopRegistry()

    fun defaultCleanupProperties(): SqlUnknownAgentCleanupProperties =
      SqlUnknownAgentCleanupProperties().apply { minRecordAgeSeconds = 0 }

    fun subject(
      shardingFilter: ShardingFilter = AllowAllShardingFilter(),
      cleanupProperties: SqlUnknownAgentCleanupProperties = defaultCleanupProperties(),
      shardingEnabled: Boolean = false
    ): SqlUnknownAgentCleanupAgent =
      SqlUnknownAgentCleanupAgent(
        StaticObjectProvider(providerRegistry),
        dslContext,
        registry,
        defaultSqlNames(),
        cleanupProperties,
        shardingFilter,
        shardingEnabled
      )

    /**
     * Seeds the test database with cache records for specified accounts.
     *
     * Creates records in cats_v1_instances (resources) and cats_v1_instances_rel (relationships).
     * Each account gets one instance record and one relationship to a server group.
     *
     * Agent type format: "{account}/TestAgent" (e.g., "prod/TestAgent")
     * This matches the format used by real caching agents and is used by the
     * sharding filter to hash-partition work across pods.
     */
    fun seedDatabase(includeTestAccount: Boolean, includeProdAccount: Boolean, includeStagingAccount: Boolean = false) {
      defaultSqlNames().run {
        val resource = resourceTableName("instances")
        val rel = relTableName("instances")
        dslContext.execute("CREATE TABLE IF NOT EXISTS $resource LIKE cats_v1_resource_template")
        dslContext.execute("CREATE TABLE IF NOT EXISTS $rel LIKE cats_v1_rel_template")
      }

      dslContext.insertInto(table("cats_v1_instances"))
        .columns(
          field("id"), field("agent"), field("application"), field("body_hash"), field("body"), field("last_updated")
        )
        .let {
          if (includeProdAccount) {
            it
              .values(
                "aws:instances:prod:us-east-1:i-abcd1234",
                "prod/TestAgent",
                "myapp",
                "",
                "",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .let {
          if (includeTestAccount) {
            it
              .values(
                "aws:instances:test:us-east-1:i-abcd1234",
                "test/TestAgent",
                "myapp",
                "",
                "",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .let {
          if (includeStagingAccount) {
            it
              .values(
                "aws:instances:staging:us-east-1:i-abcd1234",
                "staging/TestAgent",
                "myapp",
                "",
                "",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .execute()

      dslContext.insertInto(table("cats_v1_instances_rel"))
        .columns(
          field("uuid"),
          field("id"),
          field("rel_id"),
          field("rel_agent"),
          field("rel_type"),
          field("last_updated")
        )
        .let {
          if (includeProdAccount) {
            it
              .values(
                ULID().nextULID(),
                "aws:instances:prod:us-east-1:i-abcd1234",
                "aws:serverGroups:myapp-prod:prod:us-east-1:myapp-prod-v000",
                "serverGroups:prod/TestAgent",
                "serverGroups",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .let {
          if (includeTestAccount) {
            it
              .values(
                ULID().nextULID(),
                "aws:instances:test:us-east-1:i-abcd1234",
                "aws:serverGroups:myapp-test:test:us-east-1:myapp-test-v000",
                "serverGroups:test/TestAgent",
                "serverGroups",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .let {
          if (includeStagingAccount) {
            it
              .values(
                ULID().nextULID(),
                "aws:instances:staging:us-east-1:i-abcd1234",
                "aws:serverGroups:myapp-staging:staging:us-east-1:myapp-staging-v000",
                "serverGroups:staging/TestAgent",
                "serverGroups",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .execute()
    }

    fun testCachingAgent(): TestAgent =
      TestAgent().also {
        it.scope = "test"
        it.types = setOf(INSTANCES.ns, SERVER_GROUPS.ns)
        it.authoritative = setOf(INSTANCES.ns)
        it.results = mapOf(
          INSTANCES.ns to listOf(
            DefaultCacheData(
              "aws:instances:test:us-east-1:i-abcd1234",
              mapOf(),
              mapOf(
                SERVER_GROUPS.ns to listOf(
                  "aws:serverGroups:myapp-test:test:us-east-1:myapp-test-v000"
                )
              )
            )
          )
        )
      }

    fun prodCachingAgent(): TestAgent =
      TestAgent().also {
        it.scope = "prod"
        it.types = setOf(INSTANCES.ns, SERVER_GROUPS.ns)
        it.authoritative = setOf(INSTANCES.ns)
        it.results = mapOf(
          INSTANCES.ns to listOf(
            DefaultCacheData(
              "aws:instances:prod:us-east-1:i-abcd1234",
              mapOf(),
              mapOf(
                SERVER_GROUPS.ns to listOf(
                  "aws:serverGroups:myapp-prod:prod:us-east-1:myapp-prod-v000"
                )
              )
            )
          )
        )
      }

    fun unregisteredCustomResourceCachingAgent(): TestAgent =
      TestAgent().also {
        it.scope = "unregisteredCustomResources"
        it.types = setOf("cloud.google.com.BackendConfig")
        it.authoritative = setOf("cloud.google.com.BackendConfig")
      }

    fun selectAllResources(): List<String> =
      dslContext.select(field("id"))
        .from(table(defaultSqlNames().resourceTableName("instances")))
        .fetch(0, String::class.java)

    fun selectAllRels(): List<String> =
      dslContext.select(field("rel_id"))
        .from(table(defaultSqlNames().relTableName("instances")))
        .fetch(0, String::class.java)
  }

  /** Simple ObjectProvider wrapper for test injection */
  private inner class StaticObjectProvider(val obj: ProviderRegistry) : ObjectProvider<ProviderRegistry> {
    override fun getIfUnique(): ProviderRegistry = obj
    override fun getObject(vararg args: Any?): ProviderRegistry = obj
    override fun getObject(): ProviderRegistry = obj
    override fun getIfAvailable(): ProviderRegistry = obj
  }

  /** Test sharding filter that allows all agents (simulates non-sharded deployment) */
  private class AllowAllShardingFilter : ShardingFilter {
    override fun filter(agent: Agent): Boolean = true
  }
}
