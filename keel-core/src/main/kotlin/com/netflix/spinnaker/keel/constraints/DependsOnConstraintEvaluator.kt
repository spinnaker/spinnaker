package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.persistence.PromotionRepository
import org.springframework.stereotype.Component

@Component
class DependsOnConstraintEvaluator(
  private val promotionRepository: PromotionRepository
) : ConstraintEvaluator<DependsOnConstraint> {

  override val constraintType = DependsOnConstraint::class.java

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: String
  ): Boolean {
    val target = deliveryConfig.environments.firstOrNull { it.name == targetEnvironment }
    requireNotNull(target) {
      "No environment named $targetEnvironment exists in the configuration ${deliveryConfig.name}"
    }

    val constraint = target
      .constraints
      .filterIsInstance<DependsOnConstraint>()
      .first()

    val requiredEnvironment = deliveryConfig
      .environments
      .firstOrNull { it.name == constraint.environment }
    requireNotNull(requiredEnvironment) {
      "No environment named ${constraint.environment} exists in the configuration ${deliveryConfig.name}"
    }
    return promotionRepository.wasSuccessfullyDeployedTo(
      deliveryConfig,
      artifact,
      version,
      requiredEnvironment.name
    )
  }
}
