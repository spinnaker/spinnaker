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
package com.netflix.spinnaker.clouddriver.saga.exceptions

import com.netflix.spinnaker.clouddriver.saga.models.Saga

/**
 * Thrown when an integration attempts to interact with the internal [Saga] event state incorrectly.
 */
class SagaStateIntegrationException(message: String) : SagaIntegrationException(message) {
  companion object {
    fun typeNotFound(expectedType: Class<*>, saga: Saga) =
      SagaStateIntegrationException(
        "No SagaEvent present for requested type: ${expectedType.simpleName} (${saga.name}/${saga.id})"
      )

    fun tooManyResults(expectedType: Class<*>, saga: Saga) =
      SagaStateIntegrationException(
        "More than one SagaEvent present for requested type: ${expectedType.simpleName} (${saga.name}/${saga.id})"
      )
  }
}
