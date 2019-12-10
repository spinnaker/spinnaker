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

import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.persistence.PausedRepository
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope.APPLICATION
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope.RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.PAUSED
import org.jooq.DSLContext

class SqlPausedRepository(
  val jooq: DSLContext
) : PausedRepository {

  override fun pauseApplication(application: String) {
    insert(APPLICATION, application)
  }

  override fun resumeApplication(application: String) {
    remove(APPLICATION, application)
  }

  override fun applicationPaused(application: String): Boolean =
    exists(APPLICATION, application)

  override fun pauseResource(id: ResourceId) {
    insert(RESOURCE, id.value)
  }

  override fun resumeResource(id: ResourceId) {
    remove(RESOURCE, id.value)
  }

  override fun resourcePaused(id: ResourceId): Boolean =
    exists(RESOURCE, id.value)

  override fun getPausedApplications(): List<String> =
    get(APPLICATION)

  override fun getPausedResources(): List<ResourceId> =
    get(RESOURCE).map { ResourceId(it) }

  private fun insert(scope: Scope, name: String) {
    jooq
      .insertInto(PAUSED)
      .set(PAUSED.SCOPE, scope.name)
      .set(PAUSED.NAME, name)
      .onDuplicateKeyIgnore()
      .execute()
  }

  private fun remove(scope: Scope, name: String) {
    jooq
      .deleteFrom(PAUSED)
      .where(PAUSED.SCOPE.eq(scope.name))
      .and(PAUSED.NAME.eq(name))
      .execute()
  }

  private fun exists(scope: Scope, name: String): Boolean {
    jooq
      .select(PAUSED.NAME)
      .from(PAUSED)
      .where(PAUSED.SCOPE.eq(scope.name))
      .and(PAUSED.NAME.eq(name))
      .fetchOne()
      ?.let { return true }
    return false
  }

  private fun get(scope: Scope): List<String> =
    jooq
      .select(PAUSED.NAME)
      .from(PAUSED)
      .where(PAUSED.SCOPE.eq(scope.name))
      .fetch(PAUSED.NAME)
}
