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
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationConfig

/**
 * A [ConstraintEvaluator] that deals with the logic for stateful constraints.
 *
 * The [canPromote] function handles reading state from the repository and saving initial state.
 * If the state is 'done' (failed or passed) the specific implementations don't get called.
 * If the state is not 'done', underlying implementations [canPromote] functions get called.
 */
interface StatefulConstraintEvaluator<CONSTRAINT : Constraint, ATTRIBUTES : ConstraintStateAttributes> : ConstraintEvaluator<CONSTRAINT> {
  /**
   * The type of the metadata saved about the constraint, surfaced here to automatically register it
   * for serialization
   */
  val attributeType: SupportedConstraintAttributesType<ATTRIBUTES>

  val repository: ConstraintRepository

  @JvmDefault
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
      constraint.type,
      artifact.reference
    )

    if (state == null) {
      state = ConstraintState(
        deliveryConfigName = deliveryConfig.name,
        environmentName = targetEnvironment.name,
        artifactVersion = version,
        artifactReference = artifact.reference,
        type = constraint.type,
        status = ConstraintStatus.PENDING
      )
      repository.storeConstraintState(state)
    }

    return when {
      state.judgedByUser() &&  state.failed() -> false
      state.judgedByUser() &&  state.passed() -> true
      !state.judgedByUser() &&  shouldAlwaysReevaluate() -> {
        canPromote(artifact, version, deliveryConfig, targetEnvironment, constraint, state)
      }
      !state.judgedByUser() && !shouldAlwaysReevaluate() && state.failed() -> false
      !state.judgedByUser() && !shouldAlwaysReevaluate() && state.passed() -> false
      else -> {
        canPromote(artifact, version, deliveryConfig, targetEnvironment, constraint, state)
      }
    }
  }

  /**
   * This function is called if a user has not judged the constraint
   * (if the state is not OVERRIDE_FAIL or OVERRIDE_PASS) .
   *
   * A constraint can change its status between PASS and FAIL by saving the new status during this function
   * evaluation.
   *
   * @return true if the artifact can be promoted, false otherwise
   */
  fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    constraint: CONSTRAINT,
    state: ConstraintState
  ): Boolean

  /**
   * Callback API for [ConstraintStateChanged]. Override this method if your [StatefulConstraintEvaluator] needs
   * to take action when a supported constraint's state changes.
   *
   * Don't implement both [onConstraintStateChanged] and [onConstraintStateChangedWithNotification], because both
   * will be called.
   */
  @JvmDefault
  @Deprecated(
    message = "Implement the new method with notifications",
    replaceWith = ReplaceWith("onConstraintStateChangedWithNotification")
  )
  fun onConstraintStateChanged(event: ConstraintStateChanged) {
    // default implementation is a no-op
  }

  /**
   * Callback API for [ConstraintStateChanged]. Override this method if your [StatefulConstraintEvaluator] needs
   * to take action when a supported constraint's state changes AND you need to send a notification.
   *
   * Don't implement both [onConstraintStateChanged] and [onConstraintStateChangedWithNotification], because both
   * will be called.
   */
  @JvmDefault
  fun onConstraintStateChangedWithNotification(event: ConstraintStateChanged): PluginNotificationConfig? {
    // default implementation is a no-op
    return null
  }

  /**
   * Controls whether we should ask the constraint plugin to decide the result every time, or just when the
   * status is not a terminal status.
   *
   * Return true to allow this constraint to be able to flip from pass to fail.
   * Otherwise, once the constraint enters a pass/fail state, it stays there forever.
   */
  @JvmDefault
  fun shouldAlwaysReevaluate(): Boolean = false
}
