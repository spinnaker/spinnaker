package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig

interface ConstraintEvaluator<T : Constraint> {

  // TODO: can we do this via an annotation on the constraint or here?
  val constraintType: Class<T>

  fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: String
  ): Boolean
}
