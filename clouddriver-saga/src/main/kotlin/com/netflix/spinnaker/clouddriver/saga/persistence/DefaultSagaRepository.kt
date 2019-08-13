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
package com.netflix.spinnaker.clouddriver.saga.persistence

import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository
import com.netflix.spinnaker.clouddriver.saga.SagaEvent
import com.netflix.spinnaker.clouddriver.saga.SagaSaved
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import org.slf4j.LoggerFactory

/**
 * The default [SagaRepository] implementation. Since Saga persistence is powered entirely by the
 * eventing lib, this class does not need an explicit persistence backend dependency.
 */
class DefaultSagaRepository(
  private val eventRepository: EventRepository
) : SagaRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun list(criteria: SagaRepository.ListCriteria): List<Saga> {
    val sagas = if (criteria.names != null && criteria.names.isNotEmpty()) {
      criteria.names.flatMap { eventRepository.listAggregates(it) }
    } else {
      eventRepository.listAggregates(null)
    }.mapNotNull { get(it.type, it.id) }

    return if (criteria.running == null) {
      sagas
    } else {
      sagas.filter { it.isComplete() != criteria.running }
    }
  }

  override fun get(type: String, id: String): Saga? {
    val events = eventRepository.list(type, id)
    if (events.isEmpty()) {
      return null
    }

    return events
      .filterIsInstance<SagaSaved>()
      .last()
      .saga
      .let {
        // Copy the Saga: We don't want to accidentally mutate the saga that's in the event if the
        // eventRepository is in-memory only.
        Saga(
          name = it.name,
          id = it.id,
          sequence = it.getSequence()
        )
      }
      .also { saga ->
        saga.hydrateEvents(events.filterIsInstance<SagaEvent>())
      }
  }

  override fun save(saga: Saga, additionalEvents: List<SagaEvent>) {
    val events: MutableList<SagaEvent> = saga.getPendingEvents().toMutableList()
    if (additionalEvents.isNotEmpty()) {
      events.addAll(additionalEvents)
    }
    events.add(SagaSaved(saga))
    eventRepository.save(saga.name, saga.id, saga.getVersion(), events)
  }
}
