/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.sql.security

import com.netflix.spinnaker.clouddriver.sql.SqlRetries
import com.netflix.spinnaker.clouddriver.sql.read
import com.netflix.spinnaker.clouddriver.sql.transactional
import com.netflix.spinnaker.kork.pubsub.model.MessageChannel
import com.netflix.spinnaker.kork.sql.routing.withPool
import java.util.UUID
import org.apache.logging.log4j.LogManager
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.springframework.scheduling.annotation.Scheduled

/** A transactional outbox with no account data, credentials, or secret references. */
class SqlAccountDefinitionNotifications(
  private val jooq: DSLContext,
  private val channel: MessageChannel,
  private val poolName: String,
  private val retries: SqlRetries
) {
  fun record(ctx: DSLContext) {
    ctx.insertInto(notificationsTable)
      .set(idColumn, UUID.randomUUID().toString())
      .execute()
  }

  /** Rows are visible only after the account transaction commits. Duplicates are safe full reloads. */
  @Scheduled(fixedDelayString = "\${account.storage.pubsub.publish-interval-ms:1000}")
  fun publishCommitted() {
    withPool(poolName) {
      try {
        val ids = jooq.read(retries) { ctx ->
          ctx.select(idColumn).from(notificationsTable).orderBy(idColumn).limit(100).fetch(idColumn)
        }
        for (id in ids) {
          channel.publish("accounts-changed")
          // A crash between publish and deletion causes a retry, never a lost notification.
          jooq.transactional(retries) { ctx ->
            ctx.deleteFrom(notificationsTable).where(idColumn.eq(id)).execute()
          }
        }
      } catch (e: Exception) {
        LOGGER.warn("Unable to publish committed account changes; the outbox will be retried", e)
      }
    }
  }

  companion object {
    private val notificationsTable = table("account_notifications")
    private val idColumn = field("id", String::class.java)
    private val LOGGER = LogManager.getLogger(SqlAccountDefinitionNotifications::class.java)
  }
}
