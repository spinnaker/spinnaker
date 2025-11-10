/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.event.persistence

import com.netflix.spinnaker.clouddriver.event.Aggregate
import com.netflix.spinnaker.clouddriver.event.SpinnakerEvent
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Positive

/**
 * The [EventRepository] is responsible for reading and writing immutable event logs from a persistent store.
 *
 * There's deliberately no eviction API. It's expected that each [EventRepository] implementation will implement
 * that functionality on their own, including invocation apis and/or scheduling; tailoring to the operational
 * needs of that backend.
 */
interface EventRepository {
  /**
   * Save [events] to an [Aggregate].
   *
   * @param aggregateType The aggregate collection name
   * @param aggregateId The unique identifier of the event aggregate within the [aggregateType]
   * @param originatingVersion The aggregate version that originated the [events]. This is used to ensure events are
   *                           added only based off the latest aggregate state
   * @param newEvents A list of events to be saved
   */
  fun save(aggregateType: String, aggregateId: String, originatingVersion: Long, newEvents: List<SpinnakerEvent>)

  /**
   * List all events for a given [Aggregate].
   *
   * @param aggregateType The aggregate collection name
   * @param aggregateId The unique identifier of the event aggregate within the [aggregateType]
   * @return An ordered list of events, oldest to newest
   */
  fun list(aggregateType: String, aggregateId: String): List<SpinnakerEvent>

  /**
   * List all aggregates for a given type.
   *
   * @param criteria The criteria to limit the response by
   * @return A list of matching aggregates
   */
  fun listAggregates(criteria: ListAggregatesCriteria): ListAggregatesResult

  /**
   * @param aggregateType The type of [Aggregate] to return. If unset, all types will be returned.
   * @param token The page token to paginate from. It will return the first results
   * @param perPage The number of [Aggregate]s to return in each response
   */
  class ListAggregatesCriteria(
    val aggregateType: String? = null,
    val token: String? = null,

    @Positive @Max(1000)
    val perPage: Int = 100
  )

  /**
   * @param aggregates The collection of [Aggregate]s returned
   * @param nextPageToken The next page token
   */
  class ListAggregatesResult(
    val aggregates: List<Aggregate>,
    val nextPageToken: String? = null
  )
}
