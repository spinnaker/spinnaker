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
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH

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

  private val mapper = configuredObjectMapper()

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> get(name: ResourceName, specType: Class<T>): Resource<T> =
    resources.values.find { it.name == name }?.let {
      get(it.uid, specType)
    } ?: throw NoSuchResourceName(name)

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> get(uid: UID, specType: Class<T>): Resource<T> =
    resources[uid]?.let {
      if (specType.isAssignableFrom(it.spec.javaClass)) {
        it as Resource<T>
      } else {
        val convertedSpec = mapper.convertValue(it.spec, specType)
        (it as Resource<Any>).copy(spec = convertedSpec) as Resource<T>
      }
    } ?: throw NoSuchResourceUID(uid)

  override fun store(resource: Resource<*>) {
    resources[resource.uid] = resource
    markCheckDue(resource)
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

  override fun eventHistory(uid: UID): List<ResourceEvent> =
    events[uid] ?: throw NoSuchResourceUID(uid)

  override fun appendHistory(event: ResourceEvent) {
    events.computeIfAbsent(event.uid) {
      mutableListOf()
    }
      .let {
        it.add(0, event)
      }
  }

  override fun nextResourcesDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<ResourceHeader> {
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

  override fun markCheckDue(resource: Resource<*>) {
    lastCheckTimes[resource.uid] = EPOCH
  }

  fun dropAll() {
    resources.clear()
    events.clear()
    lastCheckTimes.clear()
  }

  fun size() = resources.size
}
