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

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.UNHAPPY_VETO
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import java.time.Clock
import java.time.Instant

class SqlUnhappyVetoRepository(
  override val clock: Clock,
  private val jooq: DSLContext,
  private val sqlRetry: SqlRetry
) : UnhappyVetoRepository(clock) {

  override fun markUnhappy(resource: Resource<*>, recheckTime: Instant?) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(UNHAPPY_VETO)
        .set(UNHAPPY_VETO.RESOURCE_UID, resource.uid)
        .run {
          recheckTime
            ?.let { expiryTime -> set(UNHAPPY_VETO.RECHECK_TIME, expiryTime) }
            ?: setNull(UNHAPPY_VETO.RECHECK_TIME)
        }
        .onDuplicateKeyUpdate()
        .run {
          recheckTime
            ?.let { expiryTime -> set(UNHAPPY_VETO.RECHECK_TIME, expiryTime) }
            ?: setNull(UNHAPPY_VETO.RECHECK_TIME)
        }
        .execute()
    }
  }

  override fun delete(resource: Resource<*>) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(UNHAPPY_VETO)
        .where(UNHAPPY_VETO.RESOURCE_UID.eq(resource.uid))
        .execute()
    }
  }

  override fun getRecheckTime(resource: Resource<*>): Instant? =
    sqlRetry.withRetry(READ) {
      jooq
        .select(UNHAPPY_VETO.RECHECK_TIME)
        .from(UNHAPPY_VETO)
        .where(UNHAPPY_VETO.RESOURCE_UID.eq(resource.uid))
        .fetchOne(UNHAPPY_VETO.RECHECK_TIME)
    }

  override fun getAll(): Set<String> =
    sqlRetry.withRetry(READ) {
      jooq.select(RESOURCE.ID)
        .from(UNHAPPY_VETO, RESOURCE)
        .where(UNHAPPY_VETO.RECHECK_TIME.isNull.or(UNHAPPY_VETO.RECHECK_TIME.greaterThan(clock.instant())))
        .and(UNHAPPY_VETO.RESOURCE_UID.eq(RESOURCE.UID))
        .fetch(RESOURCE.ID)
        .toSet()
    }

  override fun getAllForApp(application: String): Set<String> =
    sqlRetry.withRetry(READ) {
      jooq.select(RESOURCE.ID)
        .from(UNHAPPY_VETO, RESOURCE)
        .where(UNHAPPY_VETO.RECHECK_TIME.isNull.or(UNHAPPY_VETO.RECHECK_TIME.greaterThan(clock.instant())))
        .and(UNHAPPY_VETO.RESOURCE_UID.eq(RESOURCE.UID))
        .and(RESOURCE.APPLICATION.eq(application))
        .fetch(RESOURCE.ID)
        .toSet()
    }

  private val Resource<*>.uid: Select<Record1<String>>
    get() = jooq.select(RESOURCE.UID)
      .from(RESOURCE)
      .where(RESOURCE.ID.eq(id))
}
