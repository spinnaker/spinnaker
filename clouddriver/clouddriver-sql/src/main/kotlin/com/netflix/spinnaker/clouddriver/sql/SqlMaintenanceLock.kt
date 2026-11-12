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
import com.netflix.spinnaker.clouddriver.cache.MaintenanceLock
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.kork.sql.routing.withPool
import java.sql.SQLException
import java.time.Clock
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

/**
 * [MaintenanceLock] backed by the `cats_agent_locks` table, using the same pool, key (the bare agent
 * type) and namespacing as `SqlClusteredAgentScheduler`, so nodes that still schedule an agent via
 * CATS contend on the same row.
 *
 * When [tableNamespace] is set the table is `cats_agent_locks_<namespace>`, created at construction
 * if missing (same DDL as `SqlUtil.createTableLike` in cats-sql, which this module cannot depend on).
 *
 * Every statement touches a single row by primary key (agent_name).
 */
class SqlMaintenanceLock(
  private val jooq: DSLContext,
  private val nodeIdentity: NodeIdentity,
  tableNamespace: String? = null,
  private val clock: Clock = Clock.systemUTC()
) : MaintenanceLock {

  private val log = LoggerFactory.getLogger(javaClass)

  private val lockTable = if (tableNamespace.isNullOrBlank()) {
    REFERENCE_TABLE
  } else {
    require(tableNamespace.matches(Regex("""^\w+$"""))) {
      "tableNamespace can only contain characters [a-z, A-Z, 0-9, _]"
    }
    "${REFERENCE_TABLE}_$tableNamespace"
  }

  init {
    if (!tableNamespace.isNullOrBlank()) {
      withPool(POOL_NAME) {
        when (jooq.dialect()) {
          SQLDialect.POSTGRES ->
            jooq.execute("CREATE TABLE IF NOT EXISTS $lockTable (LIKE $REFERENCE_TABLE INCLUDING ALL)")
          else ->
            jooq.execute("CREATE TABLE IF NOT EXISTS $lockTable LIKE $REFERENCE_TABLE")
        }
      }
    }
  }

  override fun tryAcquire(agentType: String, ttlMillis: Long): Boolean {
    val now = clock.millis()
    try {
      withPool(POOL_NAME) {
        // Clear an expired lock (if any) so the insert can win.
        jooq.delete(table(lockTable))
          .where(field("agent_name").eq(agentType).and(field("lock_expiry").lt(now)))
          .execute()

        jooq.insertInto(table(lockTable))
          .columns(
            field("agent_name"),
            field("owner_id"),
            field("lock_acquired"),
            field("lock_expiry")
          )
          .values(agentType, nodeIdentity.nodeIdentity, now, now + ttlMillis)
          .execute()
      }
    } catch (e: DataIntegrityViolationException) {
      // Another node holds the lock.
      return false
    } catch (e: org.jooq.exception.DataAccessException) {
      // Plain jOOQ (no Spring exception translation): a duplicate key means another node holds the lock.
      if (isIntegrityViolation(e)) {
        return false
      }
      log.error("Unexpected sql exception while trying to acquire maintenance lock for $agentType", e)
      return false
    } catch (e: DataAccessException) {
      log.error("Unexpected sql exception while trying to acquire maintenance lock for $agentType", e)
      return false
    } catch (e: SQLException) {
      log.error("Unexpected sql exception while trying to acquire maintenance lock for $agentType", e)
      return false
    }
    return true
  }

  override fun hold(agentType: String, untilEpochMillis: Long) {
    try {
      withPool(POOL_NAME) {
        jooq.update(table(lockTable))
          .set(field("lock_expiry"), untilEpochMillis)
          .where(field("agent_name").eq(agentType).and(field("owner_id").eq(nodeIdentity.nodeIdentity)))
          .execute()
      }
    } catch (e: DataAccessException) {
      log.error("Failed to extend maintenance lock for agent: $agentType", e)
    } catch (e: org.jooq.exception.DataAccessException) {
      log.error("Failed to extend maintenance lock for agent: $agentType", e)
    } catch (e: SQLException) {
      log.error("Failed to extend maintenance lock for agent: $agentType", e)
    }
  }

  override fun release(agentType: String) {
    try {
      withPool(POOL_NAME) {
        jooq.delete(table(lockTable))
          .where(field("agent_name").eq(agentType).and(field("owner_id").eq(nodeIdentity.nodeIdentity)))
          .execute()
      }
    } catch (e: DataAccessException) {
      log.error("Failed to release maintenance lock for agent: $agentType", e)
    } catch (e: org.jooq.exception.DataAccessException) {
      log.error("Failed to release maintenance lock for agent: $agentType", e)
    } catch (e: SQLException) {
      log.error("Failed to release maintenance lock for agent: $agentType", e)
    }
  }

  private fun isIntegrityViolation(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.any {
      it is java.sql.SQLIntegrityConstraintViolationException ||
        (it is SQLException && it.sqlState?.startsWith("23") == true)
    }

  companion object {
    private const val REFERENCE_TABLE = "cats_agent_locks"
    private val POOL_NAME = ConnectionPools.CACHE_WRITER.value
  }
}
