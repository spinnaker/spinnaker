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

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class ResourceHeader(
  val id: String,
  val kind: ResourceKind
) {
  constructor(resource: Resource<*>) : this(
    resource.id,
    resource.kind
  )
}

interface ResourceRepository : PeriodicallyCheckedRepository<Resource<ResourceSpec>> {
  val clock: Clock
    get() = Clock.systemUTC()

  /**
   * Invokes [callback] once with each registered resource.
   */
  fun allResources(callback: (ResourceHeader) -> Unit)

  /**
   * Retrieves a single resource by its unique [id].
   *
   * @return The resource represented by [id] or `null` if [id] is unknown.
   * @throws NoSuchResourceException if [id] does not map to a resource in the repository.
   */
  fun get(id: String): Resource<ResourceSpec>

  fun hasManagedResources(application: String): Boolean

  /**
   * Fetches resources for a given application.
   */
  fun getResourceIdsByApplication(application: String): List<String>

  /**
   * Fetches resources for a given application.
   */
  fun getResourcesByApplication(application: String): List<Resource<*>>

  /**
   * Persists a resource.
   *
   * @return the `uid` of the stored resource.
   */
  fun store(resource: Resource<*>)

  /**
   * Deletes the resource represented by [id].
   */
  fun delete(id: String)

  /**
   * Retrieves the history of persisted events for [application].
   *
   * @param application the name of the application.
   * @param limit the maximum number of events to return.
   */
  fun applicationEventHistory(application: String, limit: Int = DEFAULT_MAX_EVENTS): List<ApplicationEvent>

  /**
   * Retrieves the history of persisted events for [application].
   *
   * @param application the name of the application.
   * @param after the time of the oldest event to return (events are returned in descending order of timestamp).
   */
  fun applicationEventHistory(application: String, after: Instant): List<ApplicationEvent>

  /**
   * Retrieves the history of state change events for the resource represented by [id].
   *
   * @param id the resource id.
   * @param limit the maximum number of events to return.
   */
  fun eventHistory(id: String, limit: Int = DEFAULT_MAX_EVENTS): List<ResourceHistoryEvent>

  /**
   * Retrieves the last event from the history of state change events for the resource represented by [id] or null if
   * none found.
   *
   * @param id the resource id.
   */
  fun lastEvent(id: String): ResourceHistoryEvent? = eventHistory(id, 1).firstOrNull()

  /**
   * Records an event associated with a resource.
   */
  fun appendHistory(event: ResourceEvent)

  /**
   * Records an event associated with an application.
   * TODO: adding this here as there's no ApplicationRepository or EventRepository, but might want to move it.
   */
  fun appendHistory(event: ApplicationEvent)

  /**
   * Returns between zero and [limit] resources that have not been checked (i.e. returned by this
   * method) in at least [minTimeSinceLastCheck].
   *
   * This method is _not_ intended to be idempotent, subsequent calls are expected to return
   * different values.
   */
  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<ResourceSpec>>

  companion object {
    const val DEFAULT_MAX_EVENTS: Int = 10
  }
}

@Suppress("UNCHECKED_CAST", "EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified T : ResourceSpec> ResourceRepository.get(id: String): Resource<T> =
  get(id).also { check(it.spec is T) } as Resource<T>

abstract class NoSuchResourceException(override val message: String?) :
  NoSuchEntityException(message)

class NoSuchResourceId(id: String) :
  NoSuchResourceException("No resource with id $id exists in the database")

enum class ResourceStatus {
  CREATED,
  DIFF,
  ACTUATING,
  HAPPY,
  UNHAPPY,
  MISSING_DEPENDENCY,
  CURRENTLY_UNRESOLVABLE,
  ERROR,
  PAUSED,
  RESUMED,
  UNKNOWN,
  DIFF_NOT_ACTIONABLE
}
