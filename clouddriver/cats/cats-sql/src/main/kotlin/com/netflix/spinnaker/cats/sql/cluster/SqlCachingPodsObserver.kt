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
import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.cats.cluster.ShardingFilter
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
import kotlin.math.abs

class SqlCachingPodsObserver (
  private val jooq: DSLContext,
  private val nodeIdentity: NodeIdentity,
  private val tableNamespace: String? = null,
  private val dynamicConfigService : DynamicConfigService,
  private val liveReplicasScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
    ThreadFactoryBuilder().setNameFormat(SqlCachingPodsObserver::class.java.simpleName + "-%d").build()
  )
) : ShardingFilter, Runnable{
  private val log = LoggerFactory.getLogger(javaClass)
  private var podCount: Int = 0
  private var podIndex: Int = -1
  private var ttlSeconds = dynamicConfigService.getConfig(Long::class.java, "cache-sharding.replica-ttl-seconds", 60)

  companion object {
    private val POOL_NAME = ConnectionPools.CACHE_WRITER.value
    const val LAST_HEARTBEAT_TIME = "last_heartbeat_time"
    const val POD_ID = "pod_id"
  }
  private val replicasReferenceTable = "caching_replicas"
  private val replicasTable = if (tableNamespace.isNullOrBlank()) {
    replicasReferenceTable
  } else {
    "${replicasReferenceTable}_$tableNamespace"
  }

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
    log.info("Account based sharding across caching pods is enabled.")
  }

  override fun run() {
    try {
      refreshHeartbeat(TimeUnit.SECONDS.toMillis(60))
    } catch (t: Throwable) {
      log.error("Failed to manage replicas heartbeat", t)
    }

  }

  private fun refreshHeartbeat(newTtl: Long){
    recordHeartbeat(newTtl)
    deleteExpiredReplicas()
    preFilter()
  }

  private fun recordHeartbeat( newTtl: Long) {
    try {
      withPool(POOL_NAME) {
        val currentPodRecord = jooq.select()
          .from(table(replicasTable))
          .where(
            DSL.field(POD_ID).eq(nodeIdentity.nodeIdentity))
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
          //update heartbeat
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

  private fun deleteExpiredReplicas(){
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
              //this exception can be safely ignored as other pod might have succeeded
              log.info(
                "Unable to delete replica entry ${existingReplicas.getString(POD_ID)} with expiry " +
                  "$expiry, at the moment.", e )
            }
          }
        }
      }
    } catch (e: SQLException) {
      log.error("Unexpected sql exception while trying to get replica records : ", e)
    }
  }

  private fun getAccountName(agentType: String): String{
    return if(agentType.contains("/")) agentType.substring(0,agentType.indexOf('/')) else agentType
  }

  private fun preFilter(){
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
            index = counter;
          }
          counter++
        }
      }
    }catch (e: SQLException){
      log.error( "Failed to fetch live pods count ${e.message}")
    }
    if(counter == 0 || index == -1){
      throw RuntimeException("No caching pod heartbeat records detected. Sharding logic can't be applied!!!!")
    }
    podCount = counter
    podIndex = index
    log.debug("Pod count : {} and current pod's index : {}", podCount, podIndex)
  }

  override fun filter(agent: Agent) : Boolean{
    if(agent.providerName.equals(CoreProvider.PROVIDER_NAME)){
      return true
    }
    if (podCount == 1 || abs(getAccountName(agent.agentType).hashCode() % podCount) == podIndex) {
      return true
    }
    return false
  }


}
