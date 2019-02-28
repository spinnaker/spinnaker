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
package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import java.time.Instant

interface ResourceRepository {
  /**
   * Invokes [callback] once with each registered resource.
   */
  fun allResources(callback: (Triple<ResourceName, ApiVersion, String>) -> Unit)

  /**
   * Retrieves a single resource by its unique [com.netflix.spinnaker.keel.api.ResourceMetadata.uid].
   *
   * @return The resource represented by [uid] or `null` if [uid] is unknown.
   * @throws NoSuchResourceException if [uid] does not map to a resource in the repository.
   */
  fun <T : Any> get(name: ResourceName, specType: Class<T>): Resource<T>

  /**
   * Persists a resource.
   *
   * @return the `uid` of the stored resource.
   */
  fun store(resource: Resource<*>)

  /**
   * Retrieves the last known state of a resource.
   *
   * @return The last known state of the resource represented by [name]. If the state has never been
   * recorded the method should return a tuple of [ResourceState.Unknown] and the current timestamp.
   */
  fun lastKnownState(name: ResourceName): Pair<ResourceState, Instant>

  /**
   * Updates the last known state of the resource represented by [name].
   */
  fun updateState(name: ResourceName, state: ResourceState)

  /**
   * Deletes the resource represented by [name].
   */
  fun delete(name: ResourceName)
}

inline fun <reified T : Any> ResourceRepository.get(name: ResourceName): Resource<T> = get(name, T::class.java)

class NoSuchResourceException(val name: ResourceName) : RuntimeException("No resource named $name exists in the repository")
