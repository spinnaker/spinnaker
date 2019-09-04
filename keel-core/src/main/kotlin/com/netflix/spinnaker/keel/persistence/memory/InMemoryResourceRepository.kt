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
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH
import java.time.Period

class InMemoryResourceRepository(
  private val clock: Clock = Clock.systemDefaultZone()
) : ResourceRepository {
  private val resources = mutableMapOf<UID, Resource<*>>()
  private val events = mutableMapOf<UID, MutableList<ResourceEvent>>()
  private val lastCheckTimes = mutableMapOf<UID, Instant>()

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    resources.values.forEach {
      callback(ResourceHeader(it))
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun get(name: ResourceName): Resource<out ResourceSpec> =
    resources.values.find { it.name == name }?.let {
      get(it.uid)
    } ?: throw NoSuchResourceName(name)

  @Suppress("UNCHECKED_CAST")
  override fun get(uid: UID): Resource<out ResourceSpec> =
    resources[uid] ?: throw NoSuchResourceUID(uid)

  override fun hasManagedResources(application: String): Boolean =
    resources.any { it.value.application == application }

  override fun getByApplication(application: String): List<String> =
    resources
      .filterValues { it.application == application }
      .map { it.value.name.toString() }

  override fun store(resource: Resource<*>) {
    resources[resource.uid] = resource
    lastCheckTimes[resource.uid] = EPOCH
  }

  override fun delete(name: ResourceName) {
    resources
      .values
      .filter { it.name == name }
      .map { it.uid }
      .singleOrNull()
      ?.also {
        resources.remove(it)
        events.remove(it)
      }
      ?: throw NoSuchResourceName(name)
  }

  override fun eventHistory(uid: UID, maxAge: Period, limit: Int): List<ResourceEvent> {
    val cutoff = clock.instant().minus(maxAge)
    return events[uid]
      ?.filter { it.timestamp >= cutoff }
      ?.let {
        if (limit > 0) it.take(limit)
        else it
      }
      ?: throw NoSuchResourceUID(uid)
  }

  override fun appendHistory(event: ResourceEvent) {
    if (event.ignoreInHistory) return

    events.computeIfAbsent(event.uid) {
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
