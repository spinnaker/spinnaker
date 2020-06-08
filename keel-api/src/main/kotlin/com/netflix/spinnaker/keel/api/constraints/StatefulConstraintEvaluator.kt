/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.keel.api.constraints

import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.events.ConstraintStateChanged

/**
 * TODO: Docs.
 */
abstract class StatefulConstraintEvaluator<T : Constraint, A : ConstraintStateAttributes>(
  protected val repository: ConstraintRepository
) : ConstraintEvaluator<T> {

  /**
   * The type of the metadata saved about the constraint, surfaced here to automatically register it
   * for serialization
   */
  abstract val attributeType: SupportedConstraintAttributesType<A>

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean {
    val constraint = ConstraintEvaluator.getConstraintForEnvironment(
      deliveryConfig,
      targetEnvironment.name,
      supportedType.type
    )

    var state = repository.getConstraintState(
      deliveryConfig.name,
      targetEnvironment.name,
      version,
      constraint.type
    )

    if (state == null) {
      state = ConstraintState(
        deliveryConfigName = deliveryConfig.name,
        environmentName = targetEnvironment.name,
        artifactVersion = version,
        type = constraint.type,
        status = ConstraintStatus.PENDING
      ).also {
        repository.storeConstraintState(it)
        eventPublisher.publishEvent(ConstraintStateChanged(targetEnvironment, constraint, null, it))
      }
    }

    return when {
      state.failed() -> false
      state.passed() -> true
      else -> canPromote(artifact, version, deliveryConfig, targetEnvironment, constraint, state)
    }
  }

  /**
   * TODO: Docs.
   */
  abstract fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    constraint: T,
    state: ConstraintState
  ): Boolean
}
