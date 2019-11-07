package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig

interface ConstraintEvaluator<T : Constraint> {

  companion object {
    fun <T> getConstraintForEnvironment(
      deliveryConfig: DeliveryConfig,
      targetEnvironment: String,
      klass: Class<T>
    ): T {
      val target = deliveryConfig.environments.firstOrNull { it.name == targetEnvironment }
      requireNotNull(target) {
        "No environment named $targetEnvironment exists in the configuration ${deliveryConfig.name}"
      }

      return target
        .constraints
        .filterIsInstance(klass)
        .first()
    }
  }

  // TODO: can we do this via an annotation on the constraint or here?
  val constraintType: Class<T>

  fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: String
  ): Boolean
}
