/*
 * Copyright 2021 OpsMx
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
 *
 */

package com.netflix.spinnaker.cats.sql.cluster

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.cluster.AccountKeyExtractor
import com.netflix.spinnaker.cats.cluster.AgentTypeKeyExtractor
import com.netflix.spinnaker.cats.cluster.CanonicalModuloShardingStrategy
import com.netflix.spinnaker.cats.cluster.JumpConsistentHashStrategy
import com.netflix.spinnaker.cats.cluster.ModuloShardingStrategy
import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.cats.cluster.RegionKeyExtractor
import com.netflix.spinnaker.cats.cluster.ShardingFilter
import com.netflix.spinnaker.cats.cluster.ShardingKeyExtractor
import com.netflix.spinnaker.cats.cluster.ShardingStrategy
import com.netflix.spinnaker.cats.sql.SqlUtil
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.routing.withPool
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * SQL-based ShardingFilter implementation that uses heartbeats to discover pods and assigns
 * agents to pods based on configurable sharding strategy and key extraction.
 *
 * ## How Sharding Works
 *
 * Each Clouddriver pod registers itself in a SQL table (`caching_replicas`) with an expiry timestamp.
 * The heartbeat mechanism:
 * 1. Inserts/updates this pod's entry with an expiry timestamp
 * 2. Deletes expired entries (stale pods that haven't sent heartbeats)
 * 3. Queries all live pods, sorted alphabetically by pod ID
 * 4. This pod's index in the sorted list becomes its shard assignment
 *
 * ## Scale Event Behavior
 *
 * **With modulo strategy (default):** When pod count changes (e.g., 3→4 pods), nearly all
 * agents are reassigned. This causes a "thundering herd" where all pods simultaneously re-cache
 * their new assignments.
 *
 * **With jump strategy:** When pod count changes from n to n+1, only ~1/(n+1) of agents move
 * to the new pod. For example, scaling from 10→11 pods moves only ~9% of agents.
 *
 * ## Configuration Options
 *
 * - `cache-sharding.strategy`: Hashing strategy
 *   - `"modulo"` (default): legacy compatibility mapping
 *   - `"canonical-modulo"`: positive remainder modulo mapping
 *   - `"jump"`: jump consistent hash for minimal movement on scale events
 * - `cache-sharding.sharding-key`: Key extraction - "account" (default), "region", or "agent"
 * - `cache-sharding.replica-ttl-seconds`: Pod heartbeat TTL (default 60)
 * - `cache-sharding.heartbeat-interval-seconds`: Heartbeat frequency (default 30)
 *
 * @see ShardingStrategy
 * @see ShardingKeyExtractor
 */
class SqlCachingPodsObserver(
  private val jooq: DSLContext,
  private val nodeIdentity: NodeIdentity,
  private val tableNamespace: String? = null,
  private val dynamicConfigService: DynamicConfigService,
  private val shardingStrategy: ShardingStrategy,
  private val keyExtractor: ShardingKeyExtractor,
  private val liveReplicasScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
    ThreadFactoryBuilder().setNameFormat(SqlCachingPodsObserver::class.java.simpleName + "-%d").build()
  )
) : ShardingFilter, Runnable {

  private val log = LoggerFactory.getLogger(javaClass)

  @Volatile
  private var podCount: Int = 0

  @Volatile
  private var podIndex: Int = -1

  private val ttlSeconds = dynamicConfigService.getConfig(Long::class.java, "cache-sharding.replica-ttl-seconds", 60)

  companion object {
    private val POOL_NAME = ConnectionPools.CACHE_WRITER.value
    const val LAST_HEARTBEAT_TIME = "last_heartbeat_time"
    const val POD_ID = "pod_id"

    /**
     * Creates a ShardingStrategy based on configuration name.
     */
    @JvmStatic
    fun createStrategy(name: String): ShardingStrategy {
      return when (name.lowercase()) {
        "canonical-modulo" -> CanonicalModuloShardingStrategy()
        "jump" -> JumpConsistentHashStrategy()
        // Keep modulo as the compatibility default for existing installations.
        else -> ModuloShardingStrategy()
      }
    }

    /**
     * Creates a ShardingKeyExtractor based on configuration name.
     */
    @JvmStatic
    fun createKeyExtractor(name: String): ShardingKeyExtractor {
      return when (name.lowercase()) {
        "agent" -> AgentTypeKeyExtractor()
        "region" -> RegionKeyExtractor()
        else -> AccountKeyExtractor()
      }
    }
  }

  private val replicasReferenceTable = "caching_replicas"
  private val replicasTable = if (tableNamespace.isNullOrBlank()) {
    replicasReferenceTable
  } else {
    "${replicasReferenceTable}_$tableNamespace"
  }

  /**
   * Primary constructor that reads configuration from DynamicConfigService.
   */
  constructor(
    jooq: DSLContext,
    nodeIdentity: NodeIdentity,
    tableNamespace: String? = null,
    dynamicConfigService: DynamicConfigService
  ) : this(
    jooq = jooq,
    nodeIdentity = nodeIdentity,
    tableNamespace = tableNamespace,
    dynamicConfigService = dynamicConfigService,
    shardingStrategy = createStrategy(
      dynamicConfigService.getConfig(String::class.java, "cache-sharding.strategy", "modulo")
    ),
    keyExtractor = createKeyExtractor(
      dynamicConfigService.getConfig(String::class.java, "cache-sharding.sharding-key", "account")
    )
  )

  init {
    if (!tableNamespace.isNullOrBlank()) {
      withPool(POOL_NAME) {
        SqlUtil.createTableLike(jooq, replicasTable, replicasReferenceTable)
      }
    }
    refreshHeartbeat(TimeUnit.SECONDS.toMillis(ttlSeconds))
    val recheckInterval =
      dynamicConfigService.getConfig(Long::class.java, "cache-sharding.heartbeat-interval-seconds", 30)
    liveReplicasScheduler.scheduleAtFixedRate(this, 0, recheckInterval, TimeUnit.SECONDS)
    log.info(
      "Sharding enabled with strategy={}, keyExtractor={}",
      shardingStrategy.name,
      keyExtractor.name
    )
  }

  override fun run() {
    try {
      refreshHeartbeat(TimeUnit.SECONDS.toMillis(ttlSeconds))
    } catch (t: Throwable) {
      log.error("Failed to manage replicas heartbeat", t)
    }
  }

  private fun refreshHeartbeat(newTtl: Long) {
    recordHeartbeat(newTtl)
    deleteExpiredReplicas()
    updatePodState()
  }

  private fun recordHeartbeat(newTtl: Long) {
    try {
      withPool(POOL_NAME) {
        val currentPodRecord = jooq.select()
          .from(table(replicasTable))
          .where(
            DSL.field(POD_ID).eq(nodeIdentity.nodeIdentity)
          )
          .fetch()
          .intoResultSet()
        // insert heartbeat
        if (!currentPodRecord.next()) {
          jooq.insertInto(table(replicasTable))
            .columns(
              DSL.field(POD_ID),
              DSL.field(LAST_HEARTBEAT_TIME)
            )
            .values(
              nodeIdentity.nodeIdentity,
              System.currentTimeMillis() + newTtl
            )
            .execute()
        } else {
          // update heartbeat
          jooq.update(table(replicasTable))
            .set(DSL.field(LAST_HEARTBEAT_TIME), System.currentTimeMillis() + newTtl)
            .where(DSL.field(POD_ID).eq(nodeIdentity.nodeIdentity))
            .execute()
        }
      }
    } catch (e: DataIntegrityViolationException) {
      log.error("Unexpected DataIntegrityViolationException", e)
    } catch (e: SQLException) {
      log.error("Unexpected sql exception while trying to acquire agent lock", e)
    }
  }

  private fun deleteExpiredReplicas() {
    try {
      withPool(POOL_NAME) {
        val existingReplicas = jooq.select()
          .from(table(replicasTable))
          .fetch()
          .intoResultSet()
        val now = System.currentTimeMillis()
        while (existingReplicas.next()) {
          val expiry = existingReplicas.getLong(LAST_HEARTBEAT_TIME)
          val podId = existingReplicas.getString(POD_ID)
          if (now > expiry) {
            try {
              jooq.deleteFrom(table(replicasTable))
                .where(
                  DSL.field(POD_ID).eq(podId)
                    .and(DSL.field(LAST_HEARTBEAT_TIME).eq(expiry))
                )
                .execute()
              log.info("Deleted expired entry having id : {} and expiry millis : {}", podId, expiry)
            } catch (e: SQLException) {
              // this exception can be safely ignored as other pod might have succeeded
              log.info(
                "Unable to delete replica entry ${existingReplicas.getString(POD_ID)} with expiry " +
                  "$expiry, at the moment.",
                e
              )
            }
          }
        }
      }
    } catch (e: SQLException) {
      log.error("Unexpected sql exception while trying to get replica records : ", e)
    }
  }

  private fun updatePodState() {
    var counter = 0
    var index = -1
    try {
      withPool(POOL_NAME) {
        val cachingPods = jooq.select()
          .from(table(replicasTable))
          .orderBy(DSL.field(POD_ID))
          .fetch()
          .intoResultSet()

        while (cachingPods.next()) {
          if (cachingPods.getString(POD_ID).equals(nodeIdentity.nodeIdentity)) {
            index = counter
          }
          counter++
        }
      }
    } catch (e: SQLException) {
      log.error("Failed to fetch live pods count ${e.message}")
    }
    if (counter == 0 || index == -1) {
      log.error("No caching pod heartbeat records detected. Sharding logic can't be applied!!!!")
      // Don't throw - allow degraded mode with pass-through
      return
    }
    podCount = counter
    podIndex = index
    log.debug("Pod count : {} and current pod's index : {}", podCount, podIndex)
  }

  override fun filter(agent: Agent): Boolean {
    // CoreProvider agents bypass sharding (they run on all pods)
    if (agent.providerName.equals(CoreProvider.PROVIDER_NAME)) {
      return true
    }

    // If sharding state is not yet established, default to pass-through to avoid stalls
    if (podCount <= 1 || podIndex < 0) {
      return true
    }

    // Extract the sharding key and compute the owner
    val key = keyExtractor.extractKey(agent)
    val owner = shardingStrategy.computeOwner(key, podCount)
    return owner == podIndex
  }

  /** Returns the current pod count (for testing/metrics). */
  fun getPodCount(): Int = podCount

  /** Returns the current pod index (for testing/metrics). */
  fun getPodIndex(): Int = podIndex

  /** Returns the sharding strategy name (for metrics). */
  fun getStrategyName(): String = shardingStrategy.name

  /** Returns the key extractor name (for metrics). */
  fun getKeyExtractorName(): String = keyExtractor.name

  /** Triggers a heartbeat refresh. Visible for testing. */
  internal fun triggerHeartbeat() {
    refreshHeartbeat(TimeUnit.SECONDS.toMillis(ttlSeconds))
  }
}
