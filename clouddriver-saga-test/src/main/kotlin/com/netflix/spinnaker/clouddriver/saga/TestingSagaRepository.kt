/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.saga

import com.netflix.spinnaker.clouddriver.event.EventMetadata
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository
import java.util.UUID

class TestingSagaRepository : SagaRepository {

  private val sagas: MutableMap<String, Saga> = mutableMapOf()

  override fun list(criteria: SagaRepository.ListCriteria): List<Saga> {
    return sagas.values.toList()
  }

  override fun get(type: String, id: String): Saga? {
    return sagas[createId(type, id)]
  }

  override fun save(saga: Saga, additionalEvents: List<SagaEvent>) {
    sagas.putIfAbsent(createId(saga), saga)

    val currentSequence = saga.getEvents().map { it.getMetadata().sequence }.maxOrNull() ?: 0
    val originatingVersion = saga.getVersion()

    saga.getPendingEvents()
      .plus(additionalEvents)
      .forEachIndexed { index, event ->
        event.setMetadata(
          EventMetadata(
            id = UUID.randomUUID().toString(),
            aggregateType = saga.name,
            aggregateId = saga.id,
            sequence = currentSequence + index + 1,
            originatingVersion = originatingVersion
          )
        )
        saga.addEventForTest(event)
      }
  }

  private fun createId(saga: Saga): String = createId(saga.name, saga.id)

  private fun createId(sagaName: String, sagaId: String) = "$sagaName/$sagaId"
}
