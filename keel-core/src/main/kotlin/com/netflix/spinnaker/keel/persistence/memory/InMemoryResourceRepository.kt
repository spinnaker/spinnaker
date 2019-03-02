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
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
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
  private val resources = mutableMapOf<ResourceName, Resource<*>>()
  private val states = mutableMapOf<ResourceName, Pair<ResourceState, Instant>>()

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    resources.values.forEach {
      callback(ResourceHeader(it.metadata.uid!!, it.metadata.name, it.metadata.resourceVersion, it.apiVersion, it.kind))
    }
  }

  private val mapper = configuredObjectMapper()

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> get(name: ResourceName, specType: Class<T>): Resource<T> =
    resources[name]?.let {
      if (specType.isAssignableFrom(it.spec.javaClass)) {
        it as Resource<T>
      } else {
        val convertedSpec = mapper.convertValue(it.spec, specType)
        (it as Resource<Any>).copy(spec = convertedSpec) as Resource<T>
      }
    } ?: throw NoSuchResourceException(name)

  override fun store(resource: Resource<*>) {
    resources[resource.metadata.name] = resource
    states[resource.metadata.name] = Unknown to clock.instant()
  }

  override fun delete(name: ResourceName) {
    resources.remove(name)
    states.remove(name)
  }

  override fun lastKnownState(name: ResourceName): Pair<ResourceState, Instant> =
    states[name] ?: (Unknown to clock.instant())

  override fun updateState(name: ResourceName, state: ResourceState) {
    states[name] = state to clock.instant()
  }

  fun dropAll() {
    resources.clear()
  }
}

