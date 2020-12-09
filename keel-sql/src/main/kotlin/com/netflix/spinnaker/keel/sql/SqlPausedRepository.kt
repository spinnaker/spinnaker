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

import com.netflix.spinnaker.keel.pause.Pause
import com.netflix.spinnaker.keel.pause.PauseScope
import com.netflix.spinnaker.keel.pause.PauseScope.APPLICATION
import com.netflix.spinnaker.keel.pause.PauseScope.RESOURCE
import com.netflix.spinnaker.keel.persistence.PausedRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.PAUSED
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import org.jooq.DSLContext
import java.time.Clock

class SqlPausedRepository(
  val jooq: DSLContext,
  val sqlRetry: SqlRetry,
  override val clock: Clock
) : PausedRepository {

  override fun getPause(scope: PauseScope, name: String): Pause? {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(PAUSED.PAUSED_AT, PAUSED.PAUSED_BY)
        .from(PAUSED)
        .where(PAUSED.SCOPE.eq(scope.name))
        .and(PAUSED.NAME.eq(name))
        .fetchOne { (timestamp, user) ->
          Pause(scope, name, user, timestamp)
        }
    }
  }

  override fun pauseApplication(application: String, user: String) {
    insert(APPLICATION, application, user)
  }

  override fun resumeApplication(application: String) {
    remove(APPLICATION, application)
  }

  override fun applicationPaused(application: String): Boolean =
    exists(APPLICATION, application)

  override fun pauseResource(id: String, user: String) {
    insert(RESOURCE, id, user)
  }

  override fun resumeResource(id: String) {
    remove(RESOURCE, id)
  }

  override fun resourcePaused(id: String): Boolean =
    exists(RESOURCE, id)

  override fun getPausedApplications(): List<String> =
    get(APPLICATION)

  override fun getPausedResources(): List<String> =
    get(RESOURCE)

  private fun insert(scope: PauseScope, name: String, user: String) {
    sqlRetry.withRetry(WRITE) {
      jooq
        .insertInto(PAUSED)
        .set(PAUSED.SCOPE, scope.name)
        .set(PAUSED.NAME, name)
        .set(PAUSED.PAUSED_AT, clock.instant())
        .set(PAUSED.PAUSED_BY, user)
        .onDuplicateKeyIgnore()
        .execute()
    }
  }

  private fun remove(scope: PauseScope, name: String) {
    sqlRetry.withRetry(WRITE) {
      jooq
        .deleteFrom(PAUSED)
        .where(PAUSED.SCOPE.eq(scope.name))
        .and(PAUSED.NAME.eq(name))
        .execute()
    }
  }

  private fun exists(scope: PauseScope, name: String): Boolean {
    return sqlRetry.withRetry(READ) {
      jooq
        .select(PAUSED.NAME)
        .from(PAUSED)
        .where(PAUSED.SCOPE.eq(scope.name))
        .and(PAUSED.NAME.eq(name))
        .fetchOne()
    } != null
  }

  private fun get(scope: PauseScope): List<String> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(PAUSED.NAME)
        .from(PAUSED)
        .where(PAUSED.SCOPE.eq(scope.name))
        .fetch(PAUSED.NAME)
    }
}
