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
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.events.ResourceEvent
import java.time.Duration

data class ResourceHeader(
  val uid: UID,
  val name: ResourceName,
  val apiVersion: ApiVersion,
  val kind: String
) {
  constructor(resource: Resource<*>) : this(
    resource.uid,
    resource.name,
    resource.apiVersion,
    resource.kind
  )
}

interface ResourceRepository : PeriodicallyCheckedRepository<ResourceHeader> {
  /**
   * Invokes [callback] once with each registered resource.
   */
  fun allResources(callback: (ResourceHeader) -> Unit)

  /**
   * Retrieves a single resource by its unique [name].
   *
   * @return The resource represented by [name] or `null` if [name] is unknown.
   * @throws NoSuchResourceException if [name] does not map to a resource in the repository.
   */
  fun get(name: ResourceName): Resource<out ResourceSpec>

  /**
   * Retrieves a single resource by its unique [uid].
   *
   * @return The resource represented by [uid] or `null` if [uid] is unknown.
   * @throws NoSuchResourceException if [uid] does not map to a resource in the repository.
   */
  fun get(uid: UID): Resource<out ResourceSpec>

  fun hasManagedResources(application: String): Boolean

  /**
   * Fetches resources for a given application.
   */
  fun getByApplication(application: String): List<String>

  /**
   * Persists a resource.
   *
   * @return the `uid` of the stored resource.
   */
  fun store(resource: Resource<*>)

  /**
   * Deletes the resource represented by [name].
   */
  fun delete(name: ResourceName)

  /**
   * Retrieves the history of state change events for the resource represented by [uid].
   *
   * @param uid the resource id.
   * @param limit the maximum number of events to return.
   */
  fun eventHistory(uid: UID, limit: Int = DEFAULT_MAX_EVENTS): List<ResourceEvent>

  /**
   * Records an event associated with a resource.
   */
  fun appendHistory(event: ResourceEvent)

  /**
   * Returns between zero and [limit] resources that have not been checked (i.e. returned by this
   * method) in at least [minTimeSinceLastCheck].
   *
   * This method is _not_ intended to be idempotent, subsequent calls are expected to return
   * different values.
   */
  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<ResourceHeader>

  companion object {
    const val DEFAULT_MAX_EVENTS: Int = 10
  }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : ResourceSpec> ResourceRepository.get(name: ResourceName): Resource<T> =
  get(name).also { check(it.spec is T) } as Resource<T>

@Suppress("UNCHECKED_CAST")
inline fun <reified T : ResourceSpec> ResourceRepository.get(uid: UID): Resource<T> =
  get(uid).also { check(it.spec is T) } as Resource<T>

sealed class NoSuchResourceException(override val message: String?) : RuntimeException(message)

class NoSuchResourceName(name: ResourceName) : NoSuchResourceException("No resource named $name exists in the repository")
class NoSuchResourceUID(uid: UID) : NoSuchResourceException("No resource with uid $uid exists in the repository")
