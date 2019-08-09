package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import org.springframework.stereotype.Component

@Component
class EnvironmentPromotionChecker(
  private val artifactRepository: ArtifactRepository,
  private val constraints: List<ConstraintEvaluator<*>>
) {

  suspend fun checkEnvironments(deliveryConfig: DeliveryConfig) {
    deliveryConfig
      .artifacts
      .associateWith { artifactRepository.versions(it) }
      .forEach { (artifact, versions) ->
        deliveryConfig.environments.forEach { environment ->
          val version = if (environment.constraints.isEmpty()) {
            versions.first()
          } else {
            versions.first { v ->
              constraints.all { constraintEvaluator ->
                !environment.hasSupportedConstraint(constraintEvaluator) || constraintEvaluator.canPromote(artifact, v, deliveryConfig, environment.name)
              }
            }
          }
          artifactRepository.approveVersionFor(deliveryConfig, artifact, version, environment.name)
        }
      }
  }

  private fun Environment.hasSupportedConstraint(constraintEvaluator: ConstraintEvaluator<*>) =
    constraints.any { it.javaClass.isAssignableFrom(constraintEvaluator.constraintType) }
}
