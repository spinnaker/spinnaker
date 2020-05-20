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
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

class InMemoryResourceRepository(
  override val clock: Clock = Clock.systemUTC()
) : ResourceRepository {

  private val resources = mutableMapOf<String, Resource<*>>()
  private val events = mutableListOf<PersistentEvent>()
  private val lastCheckTimes = mutableMapOf<String, Instant>()
  private val resourceArtifacts = mutableMapOf<String, DeliveryArtifact>()

  fun dropAll() {
    resources.clear()
    events.clear()
    lastCheckTimes.clear()
    resourceArtifacts.clear()
  }

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    resources.values.forEach {
      callback(ResourceHeader(it))
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun get(id: String): Resource<ResourceSpec> =
    resources.values.find { it.id == id } ?: throw NoSuchResourceId(id)

  override fun hasManagedResources(application: String): Boolean =
    resources.any { it.value.application == application }

  override fun getResourceIdsByApplication(application: String): List<String> =
    resources
      .filterValues { it.application == application }
      .map { it.value.id }

  override fun getResourcesByApplication(application: String): List<Resource<*>> =
    resources
      .filterValues { it.application == application }
      .map { it.value }
      .toList()

  override fun store(resource: Resource<*>) {
    resources[resource.id] = resource
    lastCheckTimes[resource.id] = EPOCH
  }

  override fun delete(id: String) {
    resources
      .values
      .filter { it.id == id }
      .map { it.id }
      .singleOrNull()
      ?.also {
        resources.remove(it)
        events.removeIf { event -> event.ref == id }
        resourceArtifacts.remove(it)
      }
      ?: throw NoSuchResourceId(id)
  }

  override fun applicationEventHistory(application: String, limit: Int): List<ApplicationEvent> {
    require(limit > 0) { "limit must be a positive integer" }
    return events
      .filterIsInstance<ApplicationEvent>()
      .filter { it.application == application }
      .sortedByDescending { it.timestamp }
      .take(limit)
  }

  override fun applicationEventHistory(application: String, after: Instant): List<ApplicationEvent> {
    return events
      .filterIsInstance<ApplicationEvent>()
      .filter { it.application == application }
      .sortedByDescending { it.timestamp }
      .takeWhile { !it.timestamp.isBefore(after) }
  }

  override fun eventHistory(id: String, limit: Int): List<ResourceHistoryEvent> {
    require(limit > 0) { "limit must be a positive integer" }
    val resource = get(id)
    return events
      .filterIsInstance<ResourceHistoryEvent>()
      .filter { it.ref == resource.id || it.ref == resource.application }
      .sortedByDescending { it.timestamp }
      .take(limit)
  }

  override fun appendHistory(event: ResourceEvent) {
    doAppendHistory(event)
  }

  override fun appendHistory(event: ApplicationEvent) {
    doAppendHistory(event)
  }

  private fun doAppendHistory(event: PersistentEvent) {
    val mostRecentEvent = events.firstOrNull() // we get the first because the list is in descending order
    if ((!event.ignoreRepeatedInHistory || event.javaClass != mostRecentEvent?.javaClass)) {
      events.add(0, event)
    }
  }

  fun clearResourceEvents(id: String) =
    events.removeIf { it.ref == id }

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<ResourceSpec>> {
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
      .map { resources[it]!! }
      .toList()
  }

  fun size() = resources.size
}
