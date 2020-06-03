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
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint

/**
 * TODO: Docs
 */
interface ConstraintEvaluator<T : Constraint> : SpinnakerExtensionPoint {

  companion object {
    /**
     * TODO: Docs
     */
    fun <T> getConstraintForEnvironment(
      deliveryConfig: DeliveryConfig,
      targetEnvironment: String,
      constraintType: Class<T>
    ): T {
      val target = deliveryConfig.environments.firstOrNull { it.name == targetEnvironment }
      requireNotNull(target) {
        "No environment named $targetEnvironment exists in the configuration ${deliveryConfig.name}"
      }

      return target
        .constraints
        .filterIsInstance(constraintType)
        .first()
    }
  }

  /**
   * TODO: Docs
   */
  val supportedType: SupportedConstraintType<T>

  /**
   * TODO: Docs
   */
  val eventPublisher: EventPublisher

  /**
   * returns true if a constraint should be run for every environment in every delivery config, without being
   * exposed to delivery config author.
   */
  fun isImplicit(): Boolean = false

  /**
   * TODO: Docs
   */
  fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean
}
