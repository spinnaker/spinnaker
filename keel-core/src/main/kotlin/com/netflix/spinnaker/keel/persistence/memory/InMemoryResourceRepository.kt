/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.ResourceSummary
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

class InMemoryResourceRepository(
  private val clock: Clock = Clock.systemDefaultZone()
) : ResourceRepository {
  private val resources = mutableMapOf<ResourceId, Resource<*>>()
  private val events = mutableMapOf<ResourceId, MutableList<ResourceEvent>>()
  private val lastCheckTimes = mutableMapOf<ResourceId, Instant>()

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    resources.values.forEach {
      callback(ResourceHeader(it))
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun get(id: ResourceId): Resource<out ResourceSpec> =
    resources.values.find { it.id == id } ?: throw NoSuchResourceId(id)

  override fun hasManagedResources(application: String): Boolean =
    resources.any { it.value.application == application }

  override fun getByApplication(application: String): List<String> =
    resources
      .filterValues { it.application == application }
      .map { it.value.id.toString() }

  override fun getSummaryByApplication(application: String): List<ResourceSummary> =
    resources
      .filterValues { it.application == application }
      .map { (_, resource) ->
        resource.toResourceSummary()
      }

  override fun store(resource: Resource<*>) {
    resources[resource.id] = resource
    lastCheckTimes[resource.id] = EPOCH
  }

  override fun delete(id: ResourceId) {
    resources
      .values
      .filter { it.id == id }
      .map { it.id }
      .singleOrNull()
      ?.also {
        resources.remove(it)
        events.remove(it)
      }
      ?: throw NoSuchResourceId(id)
  }

  override fun eventHistory(id: ResourceId, limit: Int): List<ResourceEvent> {
    require(limit > 0) { "limit must be a positive integer" }
    return events[id]?.take(limit) ?: throw NoSuchResourceId(id)
  }

  override fun appendHistory(event: ResourceEvent) {
    events.computeIfAbsent(event.resourceId) {
      mutableListOf()
    }
      .let {
        if (!event.ignoreRepeatedInHistory || event.javaClass != it.firstOrNull()?.javaClass) {
          it.add(0, event)
        }
      }
  }

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<ResourceHeader> {
    val cutoff = clock.instant().minus(minTimeSinceLastCheck)
    return lastCheckTimes
      .filter { it.value <= cutoff }
      .keys
      .take(limit)
      .also { uids ->
        uids.forEach {
          lastCheckTimes[it] = clock.instant()
        }
      }
      .map { uid -> ResourceHeader(resources[uid]!!) }
  }

  fun dropAll() {
    resources.clear()
    events.clear()
    lastCheckTimes.clear()
  }

  fun size() = resources.size
}
