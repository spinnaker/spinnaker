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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.cats.cluster.NoopShardingFilter
import com.netflix.spinnaker.cats.cluster.ShardingFilter
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.cats.sql.SqlUtil
import com.netflix.spinnaker.cats.sql.cluster.SqlCachingPodsObserver
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.sql.SqlAgent
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.config.SqlUnknownAgentCleanupProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import java.sql.SQLException
import java.util.concurrent.TimeUnit
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider

/**
 * Intermittently scans the entire database looking for records created by caching agents that
 * are no longer configured.
 */
class SqlUnknownAgentCleanupAgent(
  private val providerRegistry: ObjectProvider<ProviderRegistry>,
  private val jooq: DSLContext,
  private val registry: Registry,
  private val sqlNames: SqlNames,
  private val cleanupProperties: SqlUnknownAgentCleanupProperties,
  private val shardingFilter: ShardingFilter,
  private val shardingEnabled: Boolean
) : RunnableAgent, CustomScheduledAgent, SqlAgent {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val deletedId = registry.createId("sql.cacheCleanupAgent.dataTypeRecordsDeleted")
  private val timingId = registry.createId("sql.cacheCleanupAgent.dataTypeCleanupDuration")

  override fun run() {
    log.info("Scanning for cache records to cleanup")

    val (agentTypes, agentDataTypes) = findAgentDataTypes()
    if (agentTypes.isEmpty()) {
      log.warn("No agent types found, skipping cleanup to avoid deleting valid data")
      return
    }

    // Safety: If sharding is enabled but we got the fallback NoopShardingFilter (which allows all),
    // we risk deleting records that belong to other pods. Skip until proper filter is available.
    if (shardingEnabled && shardingFilter is NoopShardingFilter) {
      log.warn("Cache sharding is enabled but NoopShardingFilter is in use; skipping cleanup run")
      return
    }

    // Safety: SqlCachingPodsObserver needs time to discover peer pods via database heartbeat.
    // Until topology is established, we don't know which records belong to this pod.
    if (shardingFilter is SqlCachingPodsObserver) {
      if (shardingFilter.getPodIndex() < 0 || shardingFilter.getPodCount() == 0) {
        log.warn("Sharding state not established (podIndex < 0 or podCount == 0); skipping cleanup run")
        return
      }
    }

    val runState = RunState(agentTypes)

    val numDataTypes = agentDataTypes.size
    log.info("Found {} cache data types generated from {} agent types", numDataTypes, agentTypes.size)

    var failures = 0
    withPool(ConnectionPools.CACHE_WRITER.value) {
      agentDataTypes.forEachIndexed { i, dataType ->
        if (cleanupProperties.excludedDataTypes.contains(dataType)) {
          log.debug("Skipping data type '{}' because it is excluded", dataType)
          // Kotlin: return@forEachIndexed is a labeled return that acts like "continue" in a forEach loop.
          // It returns from the lambda (skipping this iteration) rather than from the enclosing function.
          return@forEachIndexed
        }
        log.info("Scanning '$dataType' (${i + 1}/$numDataTypes) cache records to cleanup")
        try {
          registry.timer(timingId.withTag("dataType", dataType)).record {
            cleanTable(CacheTable.RELATIONSHIP, dataType, runState)
            cleanTable(CacheTable.RESOURCE, dataType, runState)
          }
        } catch (e: SQLException) {
          log.error("Failed to cleanup '$dataType'", e)
          failures++
        }
      }
    }

    log.info("Finished cleanup ($failures failures)")
  }

  /**
   * If the table for [dataType] has not been touched yet, scan through each record it contains,
   * deleting all records that do not correlate to a currently configured agent.
   */
  private fun cleanTable(cacheTable: CacheTable, dataType: String, state: RunState) {
    val tableName = cacheTable.getName(sqlNames, dataType)

    if (state.touchedTables.contains(tableName)) {
      // Nothing to do here, we've already processed this table.
      return
    }
    log.debug("Checking table '$tableName' for '$dataType' data cleanup")

    val tableExists = SqlUtil.getTablesLike(jooq, tableName)

    if (!tableExists.next()) {
      log.debug("Table '$tableName' not found")
      state.touchedTables.add(tableName)
      return
    }

    // Select fields: [id/uuid, agent/rel_agent, last_updated] - indices are 1-based in JDBC ResultSet
    val fieldsToSelect = cacheTable.fields + field("last_updated")
    val rs = jooq.select(*fieldsToSelect)
      .from(table(tableName))
      .fetch()
      .intoResultSet()

    val cleanedAgentTypes = mutableSetOf<String>()
    val idsToClean = mutableListOf<String>()
    val skippedMissingLastUpdated = mutableListOf<String>()
    val now = System.currentTimeMillis()
    // Records older than cutoffTime are eligible for cleanup
    val cutoffTime = now - TimeUnit.SECONDS.toMillis(cleanupProperties.minRecordAgeSeconds)
    while (rs.next()) {
      // Column 2 is "agent" (RESOURCE) or "rel_agent" (RELATIONSHIP)
      val agentType = processRelAgentTypeValue(rs.getString(2))
      if (state.agentTypes.contains(agentType)) {
        continue
      }

      // Column 3 is "last_updated" timestamp
      val lastUpdated = rs.getLong(3)
      if (cleanupProperties.minRecordAgeSeconds > 0) {
        // JDBC returns 0 for NULL columns; wasNull() tells us if the column was actually NULL.
        // Skip records with missing timestamps to avoid deleting newly-inserted data.
        val missing = rs.wasNull() || lastUpdated <= 0L
        if (missing) {
          skippedMissingLastUpdated.add(rs.getString(1))
          continue
        }
        // Skip records younger than minRecordAgeSeconds to allow for replication lag
        // and avoid race conditions with agents that are still starting up.
        if (lastUpdated > cutoffTime) {
          continue
        }
      }

      // Sharding check: Only delete records for agents this pod is responsible for.
      // We wrap the agentType in a stub Agent because ShardingFilter.filter() expects an Agent,
      // and it uses the agentType to hash-partition cleanup work across pods.
      if (!shardingFilter.filter(CleanupStubAgent(agentType))) {
        continue
      }

      // Column 1 is "id" (RESOURCE) or "uuid" (RELATIONSHIP) - the primary key
      idsToClean.add(rs.getString(1))
      cleanedAgentTypes.add(agentType)
    }

    if (idsToClean.isNotEmpty()) {
      log.info(
        "Found ${idsToClean.size} records to cleanup from '$tableName' for data type '$dataType'. " +
          "Reason: Data generated by unknown caching agents ($cleanedAgentTypes)"
      )
      if (skippedMissingLastUpdated.isNotEmpty()) {
        log.warn(
          "Skipped ${skippedMissingLastUpdated.size} records in '$tableName' (dataType '$dataType') " +
            "because last_updated was null/zero while minRecordAgeSeconds=${cleanupProperties.minRecordAgeSeconds}"
        )
      }
      if (cleanupProperties.dryRun) {
        log.info("Dry-run enabled; skipping deletion for '$tableName'")
      } else {
        val batchSize = if (cleanupProperties.deleteBatchSize > 0) cleanupProperties.deleteBatchSize else 100
        idsToClean.chunked(batchSize) { chunk ->
          jooq.deleteFrom(table(tableName))
            .where(field(cacheTable.idColumn()).`in`(*chunk.toTypedArray()))
            .execute()
        }
        registry
          .counter(deletedId.withTags("dataType", dataType, "table", cacheTable.name))
          .increment(idsToClean.size.toLong())
      }
    }

    state.touchedTables.add(tableName)
  }

  /**
   * The "rel_agent" column value is a little wonky. It uses a format of `{dataType}:{agentName}`, but we only want the
   * agent name, so we'll split on the colon value, removing the first element.
   *
   * TODO(rz): The Eureka health agents are particularly annoying, since they're just named after the HTTP endpoint
   *  they hit. This case is handled specifically, but we should just change the agent name to have better consistency
   *  with other agent names.
   */
  private fun processRelAgentTypeValue(agentType: String): String =
    agentType.split(":").let {
      if (it.size == 1) {
        agentType
      } else {
        // Eureka agents use URLs as their agent type (e.g., "http://eureka:8080/health").
        // These contain colons that aren't prefixes, so return as-is.
        if (agentType.startsWith("http://") || agentType.startsWith("https://")) {
          agentType
        } else {
          // rel_agent format is "dataType:account/AgentName" (e.g., "instances:prod/InstanceAgent").
          // We need just "account/AgentName", so drop the first segment and rejoin the rest.
          // Rejoining with ":" handles edge cases where the agent name itself contains colons.
          it.subList(1, it.size).joinToString(":")
        }
      }
    }

  /**
   * Returns a set of all known caching agent names and another set of all known authoritative
   * data types from those caching agents.
   *
   * Agent names will be used to identify what records in the database are no longer attached
   * to existing caching agents, whereas the data types themselves are needed to create the
   * SQL table names, as the tables are derived from the data types, not the agents.
   */
  private fun findAgentDataTypes(): Pair<Set<String>, Set<String>> {
    var result: Pair<Set<String>, Set<String>> = Pair(setOf(), setOf())
    providerRegistry.ifAvailable { registry ->
      val agents = registry.providers
        .flatMap { it.agents }
        .filterIsInstance<CachingAgent>()

      val dataTypes = agents
        .flatMap { it.providedDataTypes }
        .filter { it.authority == AUTHORITATIVE }
        .map { it.typeName }
        .toSet()

      result = Pair(agents.mapNotNull { sqlNames.checkAgentName(it.agentType) }.toSet(), dataTypes)
    }
    return result
  }

  /**
   * Contains per-run state of this cleanup agent.
   */
  private data class RunState(
    val agentTypes: Set<String>,
    val touchedTables: MutableList<String> = mutableListOf()
  )

  /**
   * Abstracts the logical differences--as far as this agent is concerned--between the two
   * varieties of cache tables: The table names and the associated fields we need to read
   * from the database.
   */
  private enum class CacheTable(val fields: Array<Field<*>>) {
    RESOURCE(arrayOf(field("id"), field("agent"))),
    RELATIONSHIP(arrayOf(field("uuid"), field("rel_agent")));

    fun idColumn(): String =
      when (this) {
        RESOURCE -> "id"
        RELATIONSHIP -> "uuid"
      }

    fun getName(sqlNames: SqlNames, dataType: String): String =
      when (this) {
        RESOURCE -> sqlNames.resourceTableName(dataType)
        RELATIONSHIP -> sqlNames.relTableName(dataType)
      }
  }

  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis(): Long = TimeUnit.SECONDS.toMillis(cleanupProperties.pollIntervalSeconds)
  override fun getTimeoutMillis(): Long = TimeUnit.SECONDS.toMillis(cleanupProperties.timeoutSeconds)
  override fun getAgentType(): String = javaClass.simpleName

  /**
   * Minimal Agent implementation used to query the ShardingFilter.
   *
   * The sharding filter determines pod ownership by hashing the account name
   * extracted from agentType (e.g., "prod/MyAgent" -> "prod"). This stub
   * allows us to check if a given agentType belongs to this pod without
   * having a real agent instance.
   */
  private inner class CleanupStubAgent(private val agentType: String) : Agent {
    override fun getAgentType(): String = agentType
    override fun getProviderName(): String = "cleanup-check"
    override fun getAgentExecution(providerRegistry: ProviderRegistry) =
      throw UnsupportedOperationException("Cleanup stub agent is not executable")
  }
}
