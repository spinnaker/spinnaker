package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.core.api.ArtifactUsedConstraint
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ArtifactUsedConstraintEvaluator(
  override val eventPublisher: ApplicationEventPublisher
) : ConstraintEvaluator<ArtifactUsedConstraint> {
  override val supportedType = SupportedConstraintType<ArtifactUsedConstraint>("artifact-used")
  override fun isImplicit() = true

  override fun canPromote(artifact: DeliveryArtifact, version: String, deliveryConfig: DeliveryConfig, targetEnvironment: Environment): Boolean {
    val allowedReferences = mutableSetOf<String>()
    deliveryConfig
      .environments
      .firstOrNull { it.name == targetEnvironment.name }
      ?.resources
      ?.forEach { resource ->
        resource.findAssociatedArtifact(deliveryConfig)
          ?.let { usedArtifact ->
            allowedReferences.add(usedArtifact.reference)
          }
      }

    return artifact.reference in allowedReferences
  }
}
