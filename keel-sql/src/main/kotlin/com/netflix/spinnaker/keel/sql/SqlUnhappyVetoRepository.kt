/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.UNHAPPY_VETO
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import java.time.Clock
import org.jooq.DSLContext
import java.time.Instant

class SqlUnhappyVetoRepository(
  override val clock: Clock,
  private val jooq: DSLContext,
  private val sqlRetry: SqlRetry
) : UnhappyVetoRepository(clock) {

  override fun markUnhappy(resourceId: String, application: String, recheckTime: Instant?) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(UNHAPPY_VETO)
        .set(UNHAPPY_VETO.RESOURCE_ID, resourceId)
        .set(UNHAPPY_VETO.APPLICATION, application)
        .run {
          recheckTime
            ?.let { expiryTime -> set(UNHAPPY_VETO.RECHECK_TIME, expiryTime.toTimestamp()) }
            ?: setNull(UNHAPPY_VETO.RECHECK_TIME)
        }
        .onDuplicateKeyUpdate()
        .run {
          recheckTime
            ?.let { expiryTime -> set(UNHAPPY_VETO.RECHECK_TIME, expiryTime.toTimestamp()) }
            ?: setNull(UNHAPPY_VETO.RECHECK_TIME)
        }
        .execute()
    }
  }

  override fun delete(resourceId: String) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(UNHAPPY_VETO)
        .where(UNHAPPY_VETO.RESOURCE_ID.eq(resourceId))
        .execute()
    }
  }

  override fun getRecheckTime(resourceId: String): Instant? {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(UNHAPPY_VETO.RECHECK_TIME)
        .from(UNHAPPY_VETO)
        .where(UNHAPPY_VETO.RESOURCE_ID.eq(resourceId))
        .fetchOne(UNHAPPY_VETO.RECHECK_TIME)
        ?.toInstant()
    }
  }

  override fun getAll(): Set<String> {
    val now = clock.timestamp()
    return sqlRetry.withRetry(READ) {
      jooq.select(UNHAPPY_VETO.RESOURCE_ID)
        .from(UNHAPPY_VETO)
        .where(UNHAPPY_VETO.RECHECK_TIME.isNull.or(UNHAPPY_VETO.RECHECK_TIME.greaterThan(now)))
        .fetch(UNHAPPY_VETO.RESOURCE_ID)
        .toSet()
    }
  }

  override fun getAllForApp(application: String): Set<String> {
    val now = clock.timestamp()
    return sqlRetry.withRetry(READ) {
      jooq.select(UNHAPPY_VETO.RESOURCE_ID)
        .from(UNHAPPY_VETO)
        .where(UNHAPPY_VETO.APPLICATION.eq(application))
        .and(UNHAPPY_VETO.RECHECK_TIME.isNull.or(UNHAPPY_VETO.RECHECK_TIME.greaterThan(now)))
        .fetch(UNHAPPY_VETO.RESOURCE_ID)
        .toSet()
    }
  }
}
