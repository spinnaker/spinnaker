package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator.Companion.getConstraintForEnvironment
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import org.springframework.stereotype.Component

@Component
class DependsOnConstraintEvaluator(
  private val artifactRepository: ArtifactRepository
) : ConstraintEvaluator<DependsOnConstraint> {

  override val constraintType = DependsOnConstraint::class.java

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: String
  ): Boolean {
    val constraint = getConstraintForEnvironment(deliveryConfig, targetEnvironment, constraintType)

    val requiredEnvironment = deliveryConfig
      .environments
      .firstOrNull { it.name == constraint.environment }
    requireNotNull(requiredEnvironment) {
      "No environment named ${constraint.environment} exists in the configuration ${deliveryConfig.name}"
    }
    return artifactRepository.wasSuccessfullyDeployedTo(
      deliveryConfig,
      artifact,
      version,
      requiredEnvironment.name
    )
  }
}
