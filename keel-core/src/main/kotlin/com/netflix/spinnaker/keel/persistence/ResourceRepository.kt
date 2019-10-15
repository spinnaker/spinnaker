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
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.ResourceSummary
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.model.SimpleRegionSpec
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ACTUATING
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CREATED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.DIFF
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ERROR
import com.netflix.spinnaker.keel.persistence.ResourceStatus.HAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNHAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNKNOWN
import java.time.Duration

data class ResourceHeader(
  val id: ResourceId,
  val apiVersion: ApiVersion,
  val kind: String
) {
  constructor(resource: Resource<*>) : this(
    resource.id,
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
   * Retrieves a single resource by its unique [id].
   *
   * @return The resource represented by [id] or `null` if [id] is unknown.
   * @throws NoSuchResourceException if [id] does not map to a resource in the repository.
   */
  fun get(id: ResourceId): Resource<out ResourceSpec>

  fun hasManagedResources(application: String): Boolean

  /**
   * Fetches resources for a given application.
   */
  fun getByApplication(application: String): List<String>

  /**
   * Fetches resource summary, including the status
   */
  fun getSummaryByApplication(application: String): List<ResourceSummary>

  /**
   * Persists a resource.
   *
   * @return the `uid` of the stored resource.
   */
  fun store(resource: Resource<*>)

  /**
   * Deletes the resource represented by [id].
   */
  fun delete(id: ResourceId)

  /**
   * Retrieves the history of state change events for the resource represented by [uid].
   *
   * @param id the resource id.
   * @param limit the maximum number of events to return.
   */
  fun eventHistory(id: ResourceId, limit: Int = DEFAULT_MAX_EVENTS): List<ResourceEvent>

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

  fun getStatus(id: ResourceId): ResourceStatus {
    val history = eventHistory(id, 10)
    return when {
      history.isHappy() -> HAPPY
      history.isUnhappy() -> UNHAPPY
      history.isDiff() -> DIFF
      history.isActuating() -> ACTUATING
      history.isError() -> ERROR
      history.isCreated() -> CREATED
      else -> UNKNOWN
    }
  }

  private fun List<ResourceEvent>.isHappy(): Boolean {
    return first() is ResourceValid || first() is ResourceDeltaResolved
  }

  private fun List<ResourceEvent>.isActuating(): Boolean {
    return first() is ResourceActuationLaunched
  }

  private fun List<ResourceEvent>.isError(): Boolean {
    return first() is ResourceCheckError
  }

  private fun List<ResourceEvent>.isCreated(): Boolean {
    return first() is ResourceCreated
  }

  private fun List<ResourceEvent>.isDiff(): Boolean {
    return first() is ResourceDeltaDetected || first() is ResourceMissing
  }

  /**
   * Checks last 10 events for flapping between only ResourceActuationLaunched and ResourceDeltaDetected
   */
  private fun List<ResourceEvent>.isUnhappy(): Boolean {
    val recentSliceOfHistory = this.subList(0, Math.min(10, this.size))
    val filteredHistory = recentSliceOfHistory.filter { it is ResourceDeltaDetected || it is ResourceActuationLaunched }
    if (filteredHistory.size != recentSliceOfHistory.size) {
      // there are other events, we're not thrashing.
      return false
    }
    return true
  }

  fun Resource<*>.toResourceSummary() =
    ResourceSummary(
      id = id,
      kind = kind,
      status = getStatus(id), // todo eb: this will become expensive
      moniker = if (spec is Monikered) {
        spec.moniker
      } else {
        null
      },
      locations = if (spec is Locatable<*>) {
        Locations(
          accountName = spec.locations.accountName,
          vpcName = spec.locations.vpcName,
          subnet = null, // TODO: can we avoid this for non-subnet resources?
          regions = spec.locations.regions.map { SimpleRegionSpec(it.region) }.toSet()
        )
      } else {
        null
      }
    )

  companion object {
    const val DEFAULT_MAX_EVENTS: Int = 10
  }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : ResourceSpec> ResourceRepository.get(id: ResourceId): Resource<T> =
  get(id).also { check(it.spec is T) } as Resource<T>

sealed class NoSuchResourceException(override val message: String?) : RuntimeException(message)

class NoSuchResourceId(id: ResourceId) : NoSuchResourceException("No resource with id $id exists in the repository")

enum class ResourceStatus {
  HAPPY, ACTUATING, UNHAPPY, CREATED, DIFF, ERROR, UNKNOWN
}
