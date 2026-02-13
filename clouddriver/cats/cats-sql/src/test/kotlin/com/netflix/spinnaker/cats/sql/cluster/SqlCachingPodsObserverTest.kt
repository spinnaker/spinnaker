/*
 * Copyright 2025 Harness, Inc.
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
package com.netflix.spinnaker.cats.sql.cluster

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.cluster.AccountKeyExtractor
import com.netflix.spinnaker.cats.cluster.AgentTypeKeyExtractor
import com.netflix.spinnaker.cats.cluster.CanonicalModuloShardingStrategy
import com.netflix.spinnaker.cats.cluster.JumpConsistentHashStrategy
import com.netflix.spinnaker.cats.cluster.ModuloShardingStrategy
import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.cats.cluster.RegionKeyExtractor
import com.netflix.spinnaker.cats.cluster.ShardingKeyExtractor
import com.netflix.spinnaker.cats.cluster.ShardingStrategy
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isTrue
import java.util.concurrent.Executors

/**
 * Integration tests for SqlCachingPodsObserver using Testcontainers MySQL.
 * Tests the actual sharding filter behavior with different strategies and key extractors.
 */
class SqlCachingPodsObserverTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    beforeAll {
      assumeTrue(DockerClientFactory.instance().isDockerAvailable)
    }

    after {
      cleanupReplicaTable()
    }

    context("CoreProvider bypass") {
      test("CoreProvider agents always pass through") {
        val observer = createObserver("pod-1", ModuloShardingStrategy(), AccountKeyExtractor())
        val agent = createAgent("CoreCachingAgent", CoreProvider.PROVIDER_NAME)

        expectThat(observer.filter(agent)).isTrue()
      }
    }

    context("Strategy factory selection") {
      test("createStrategy maps canonical-modulo correctly") {
        val strategy = SqlCachingPodsObserver.createStrategy("canonical-modulo")
        expectThat(strategy.name).isEqualTo("canonical-modulo")
      }

      test("createStrategy keeps modulo as default fallback") {
        val strategy = SqlCachingPodsObserver.createStrategy("unexpected-value")
        expectThat(strategy.name).isEqualTo("modulo")
      }
    }

    context("Single pod") {
      test("single pod passes all agents") {
        val observer = createObserver("only-pod", ModuloShardingStrategy(), AccountKeyExtractor())

        expectThat(observer.getPodCount()).isEqualTo(1)
        expectThat(observer.getPodIndex()).isEqualTo(0)

        val agent = createAgent("prod/us-east-1/ClusterCachingAgent", "aws")
        expectThat(observer.filter(agent)).isTrue()
      }
    }

    context("Degraded mode state handling") {
      test("heartbeat query failure clears stale pod state") {
        cleanupReplicaTable()
        val observer = createObserver("pod-state-reset", ModuloShardingStrategy(), AccountKeyExtractor())

        // Establish a valid initial state first.
        expectThat(observer.getPodCount()).isGreaterThan(0)
        expectThat(observer.getPodIndex() >= 0).isTrue()

        // Force heartbeat SQL operations to fail. This simulates transient DB/table failures.
        dslContext.execute("DROP TABLE caching_replicas")
        observer.triggerHeartbeat()

        // Regression check: failed refresh must clear old topology state to avoid stale routing.
        expectThat(observer.getPodCount()).isEqualTo(0)
        expectThat(observer.getPodIndex()).isEqualTo(-1)

        // Restore table for subsequent tests and shared cleanup hooks.
        createReplicaTable()
      }
    }

    context("Modulo strategy partitioning") {
      test("two pods partition agents correctly") {
        cleanupReplicaTable()
        val pod0Observer = createObserver("pod-0", ModuloShardingStrategy(), AccountKeyExtractor())
        val pod1Observer = createObserver("pod-1", ModuloShardingStrategy(), AccountKeyExtractor())

        // Refresh heartbeats so all observers see each other
        pod0Observer.triggerHeartbeat()
        pod1Observer.triggerHeartbeat()

        // Verify both pods see 2 pods
        expectThat(pod0Observer.getPodCount()).isEqualTo(2)
        expectThat(pod1Observer.getPodCount()).isEqualTo(2)

        // Create test agents and verify partitioning
        val agents = (0 until 20).map { i ->
          createAgent("account-$i/ClusterCachingAgent", "aws")
        }

        var pod0Count = 0
        var pod1Count = 0

        agents.forEach { agent ->
          val pod0Owns = pod0Observer.filter(agent)
          val pod1Owns = pod1Observer.filter(agent)

          // Exactly one pod should own each agent
          expectThat(pod0Owns xor pod1Owns).isTrue()

          if (pod0Owns) pod0Count++ else pod1Count++
        }

        // Both pods should have agents
        expectThat(pod0Count).isGreaterThan(0)
        expectThat(pod1Count).isGreaterThan(0)
      }
    }

    context("Jump strategy partitioning") {
      test("two pods partition agents with jump hash") {
        cleanupReplicaTable()
        val pod0Observer = createObserver("pod-a", JumpConsistentHashStrategy(), AccountKeyExtractor())
        val pod1Observer = createObserver("pod-b", JumpConsistentHashStrategy(), AccountKeyExtractor())

        // Refresh heartbeats so all observers see each other
        pod0Observer.triggerHeartbeat()
        pod1Observer.triggerHeartbeat()

        // Verify both pods see 2 pods
        expectThat(pod0Observer.getPodCount()).isEqualTo(2)
        expectThat(pod1Observer.getPodCount()).isEqualTo(2)

        // Create test agents and verify partitioning
        val agents = (0 until 20).map { i ->
          createAgent("account-$i/ClusterCachingAgent", "aws")
        }

        var pod0Count = 0
        var pod1Count = 0

        agents.forEach { agent ->
          val pod0Owns = pod0Observer.filter(agent)
          val pod1Owns = pod1Observer.filter(agent)

          // Exactly one pod should own each agent
          expectThat(pod0Owns xor pod1Owns).isTrue()

          if (pod0Owns) pod0Count++ else pod1Count++
        }

        // Both pods should have agents
        expectThat(pod0Count).isGreaterThan(0)
        expectThat(pod1Count).isGreaterThan(0)
      }
    }

    context("Key extractor behavior") {
      test("account key groups same-account agents") {
        cleanupReplicaTable()
        val observer = createObserver("test-pod", ModuloShardingStrategy(), AccountKeyExtractor())

        // Agents from same account should have same ownership
        val agent1 = createAgent("prod-aws/us-east-1/ClusterCachingAgent", "aws")
        val agent2 = createAgent("prod-aws/us-west-2/ImageCachingAgent", "aws")
        val agent3 = createAgent("prod-aws/eu-west-1/SecurityGroupCachingAgent", "aws")

        // All should have same ownership (all from "prod-aws" account)
        expectThat(observer.filter(agent1)).isEqualTo(observer.filter(agent2))
        expectThat(observer.filter(agent2)).isEqualTo(observer.filter(agent3))
      }

      test("region key can distinguish same-account different-region") {
        val extractor = RegionKeyExtractor()

        val agent1 = createAgent("prod-aws/us-east-1/ClusterCachingAgent", "aws")
        val agent2 = createAgent("prod-aws/us-west-2/ClusterCachingAgent", "aws")

        // Different regions should produce different keys
        expectThat(extractor.extractKey(agent1)).isEqualTo("prod-aws/us-east-1")
        expectThat(extractor.extractKey(agent2)).isEqualTo("prod-aws/us-west-2")
      }

      test("agent type key produces unique keys") {
        val extractor = AgentTypeKeyExtractor()

        val agent1 = createAgent("prod-aws/us-east-1/ClusterCachingAgent", "aws")
        val agent2 = createAgent("prod-aws/us-east-1/ImageCachingAgent", "aws")

        expectThat(extractor.extractKey(agent1) != extractor.extractKey(agent2)).isTrue()
      }
    }

    context("Pod discovery") {
      test("three pods discover each other correctly") {
        cleanupReplicaTable()
        val pod0Observer = createObserver("pod-alpha", ModuloShardingStrategy(), AccountKeyExtractor())
        val pod1Observer = createObserver("pod-beta", ModuloShardingStrategy(), AccountKeyExtractor())
        val pod2Observer = createObserver("pod-gamma", ModuloShardingStrategy(), AccountKeyExtractor())

        // Refresh heartbeats so all observers see each other
        pod0Observer.triggerHeartbeat()
        pod1Observer.triggerHeartbeat()
        pod2Observer.triggerHeartbeat()

        // All pods should see 3 pods
        expectThat(pod0Observer.getPodCount()).isEqualTo(3)
        expectThat(pod1Observer.getPodCount()).isEqualTo(3)
        expectThat(pod2Observer.getPodCount()).isEqualTo(3)

        // Each pod should have a unique index
        val indices = setOf(
          pod0Observer.getPodIndex(),
          pod1Observer.getPodIndex(),
          pod2Observer.getPodIndex()
        )
        expectThat(indices.size).isEqualTo(3)
      }
    }

    context("Strategy and extractor names") {
      test("names are exposed correctly") {
        cleanupReplicaTable()
        val moduloAccountObserver = createObserver("test-pod-1", ModuloShardingStrategy(), AccountKeyExtractor())

        expectThat(moduloAccountObserver.getStrategyName()).isEqualTo("modulo")
        expectThat(moduloAccountObserver.getKeyExtractorName()).isEqualTo("account")

        cleanupReplicaTable()
        val canonicalAccountObserver = createObserver("test-pod-canonical", CanonicalModuloShardingStrategy(), AccountKeyExtractor())

        expectThat(canonicalAccountObserver.getStrategyName()).isEqualTo("canonical-modulo")
        expectThat(canonicalAccountObserver.getKeyExtractorName()).isEqualTo("account")

        cleanupReplicaTable()
        val jumpRegionObserver = createObserver("test-pod-2", JumpConsistentHashStrategy(), RegionKeyExtractor())

        expectThat(jumpRegionObserver.getStrategyName()).isEqualTo("jump")
        expectThat(jumpRegionObserver.getKeyExtractorName()).isEqualTo("region")
      }
    }
  }

  private inner class Fixture {
    // Use MySQL 8 which has ARM64 support (unlike MySQL 5.7.22 used by SqlTestUtil)
    val mysql = MySQLContainer<Nothing>("mysql:8.0.37").apply {
      withDatabaseName("clouddriver")
      withUsername("root")
      withPassword("root")
      start()
    }

    val dataSource = HikariDataSource(HikariConfig().apply {
      jdbcUrl = mysql.jdbcUrl
      username = mysql.username
      password = mysql.password
      maximumPoolSize = 5
    })

    val dslContext = DSL.using(
      DefaultConfiguration()
        .set(dataSource)
        .set(SQLDialect.MYSQL)
    )

    fun createReplicaTable() {
      dslContext.execute(
        """
        CREATE TABLE IF NOT EXISTS caching_replicas (
          pod_id VARCHAR(255) PRIMARY KEY,
          last_heartbeat_time BIGINT NOT NULL
        )
        """.trimIndent()
      )
    }

    init {
      createReplicaTable()
    }

    fun cleanupReplicaTable() {
      // Some tests intentionally drop the table to validate degraded behavior.
      // Recreate first so global after-hooks can always clean deterministically.
      createReplicaTable()
      dslContext.deleteFrom(DSL.table("caching_replicas")).execute()
    }

    fun createAgent(agentType: String, providerName: String): Agent {
      val agent = mockk<Agent>()
      every { agent.agentType } returns agentType
      every { agent.providerName } returns providerName
      return agent
    }

    fun createNodeIdentity(identity: String): NodeIdentity {
      return object : NodeIdentity {
        override fun getNodeIdentity(): String = identity
      }
    }

    fun createDynamicConfigService(): DynamicConfigService {
      val service = mockk<DynamicConfigService>()
      every { service.getConfig(Long::class.java, "cache-sharding.replica-ttl-seconds", any()) } returns 60L
      every { service.getConfig(Long::class.java, "cache-sharding.heartbeat-interval-seconds", any()) } returns 30L
      every { service.getConfig(String::class.java, "cache-sharding.strategy", any()) } returns "modulo"
      every { service.getConfig(String::class.java, "cache-sharding.sharding-key", any()) } returns "account"
      return service
    }

    fun createObserver(
      nodeId: String,
      strategy: ShardingStrategy,
      keyExtractor: ShardingKeyExtractor
    ): SqlCachingPodsObserver {
      return SqlCachingPodsObserver(
        jooq = dslContext,
        nodeIdentity = createNodeIdentity(nodeId),
        tableNamespace = null,
        dynamicConfigService = createDynamicConfigService(),
        shardingStrategy = strategy,
        keyExtractor = keyExtractor,
        liveReplicasScheduler = Executors.newSingleThreadScheduledExecutor()
      )
    }
  }
}

