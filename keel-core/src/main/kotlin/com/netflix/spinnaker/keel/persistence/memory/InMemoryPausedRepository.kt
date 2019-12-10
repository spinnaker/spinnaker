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
package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.persistence.PausedRepository
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope.APPLICATION
import com.netflix.spinnaker.keel.persistence.PausedRepository.Scope.RESOURCE

class InMemoryPausedRepository : PausedRepository {
  private val paused: MutableList<Record> = mutableListOf()

  override fun pauseApplication(application: String) {
    paused.add(Record(APPLICATION, application))
  }

  override fun resumeApplication(application: String) {
    paused.remove(Record(APPLICATION, application))
  }

  override fun pauseResource(id: ResourceId) {
    paused.add(Record(RESOURCE, id.value))
  }

  override fun resumeResource(id: ResourceId) {
    paused.remove(Record(RESOURCE, id.value))
  }

  override fun resourcePaused(id: ResourceId): Boolean =
    paused.contains(Record(RESOURCE, id.value))

  override fun getPausedResources(): List<ResourceId> =
    paused.filter { it.scope == RESOURCE }.map { ResourceId(it.name) }.toList()

  override fun applicationPaused(application: String): Boolean =
    paused.contains(Record(APPLICATION, application))

  override fun getPausedApplications(): List<String> =
    paused.filter { it.scope == APPLICATION }.map { it.name }.toList()

  fun flush() =
    paused.clear()

  data class Record(
    val scope: Scope,
    val name: String
  )
}
