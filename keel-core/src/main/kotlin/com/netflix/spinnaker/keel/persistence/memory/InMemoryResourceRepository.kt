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
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceState
import com.netflix.spinnaker.keel.persistence.ResourceState.Unknown
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import java.time.Clock
import java.time.Instant

class InMemoryResourceRepository(
  private val clock: Clock = Clock.systemDefaultZone()
) : ResourceRepository {
  private val resources = mutableMapOf<UID, Resource<*>>()
  private val states = mutableMapOf<UID, MutableList<Pair<ResourceState, Instant>>>()

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    resources.values.forEach {
      callback(ResourceHeader(it))
    }
  }

  private val mapper = configuredObjectMapper()

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> get(name: ResourceName, specType: Class<T>): Resource<T> =
    resources.values.find { it.metadata.name == name }?.let {
      get(it.metadata.uid, specType)
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
    resources[resource.metadata.uid] = resource
    updateState(resource.metadata.uid, Unknown)
  }

  override fun delete(name: ResourceName) {
    resources
      .values
      .filter { it.metadata.name == name }
      .map { it.metadata.uid }
      .singleOrNull()
      ?.also {
        resources.remove(it)
        states.remove(it)
      }
  }

  override fun lastKnownState(uid: UID): Pair<ResourceState, Instant> =
    states.getValue(uid).first()

  override fun stateHistory(uid: UID): List<Pair<ResourceState, Instant>> =
    states.getOrDefault(uid, emptyList())

  override fun updateState(uid: UID, state: ResourceState) {
    states.computeIfAbsent(uid) {
      mutableListOf()
    }
      .let {
        if (it.firstOrNull()?.first != state) {
          it.add(0, state to clock.instant())
        }
      }
  }

  fun dropAll() {
    resources.clear()
  }
}
