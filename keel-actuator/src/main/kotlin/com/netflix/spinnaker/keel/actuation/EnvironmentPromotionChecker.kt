package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionApproved
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class EnvironmentPromotionChecker(
  private val artifactRepository: ArtifactRepository,
  private val constraints: List<ConstraintEvaluator<*>>,
  private val publisher: ApplicationEventPublisher
) {
  private val statefulEvaluators: List<ConstraintEvaluator<*>> = constraints
    .filterIsInstance<StatefulConstraintEvaluator<*>>()
  private val statelessEvaluators = constraints - statefulEvaluators

  /**
   * TODO (Critical): The EC2 ClusterSpec enables artifact filtering by ArtifactStatus. This is
   *  currently handled in ImageResolver after artifacts are "approved" for an environment.
   *  Instead, this needs to happen here or earlier. Otherwise we may deploy canary clusters or
   *  request manual judgements for artifacts that should never be allowed in a given environemnt.
   */
  suspend fun checkEnvironments(deliveryConfig: DeliveryConfig) {
    deliveryConfig
      .artifacts
      .associateWith { artifactRepository.versions(it) }
      .forEach { (artifact, versions) ->
        if (versions.isEmpty()) {
          log.warn("No versions for ${artifact.type} artifact ${artifact.name} are known")
        } else {
          deliveryConfig.environments.forEach { environment ->
            val version = if (environment.constraints.isEmpty()) {
              versions.firstOrNull()
            } else {
              versions.firstOrNull { v ->
                /**
                 * Only check stateful evaluators if all stateless evaluators pass. We don't
                 * want to request judgement or deploy a canary for artifacts that aren't
                 * deployed to a required environment or outside of an allowed time.
                 */
                checkConstraints(statelessEvaluators, artifact, deliveryConfig, v, environment) &&
                  checkConstraints(statefulEvaluators, artifact, deliveryConfig, v, environment)
              }
            }

            if (version == null) {
              log.warn("No version of {} passes constraints for environment {}", artifact.name, environment.name)
            } else {
              val isNewVersion = artifactRepository
                .approveVersionFor(deliveryConfig, artifact, version, environment.name)
              if (isNewVersion) {
                log.info(
                  "Approved {} {} version {} for {} environment {} in {}",
                  artifact.name,
                  artifact.type,
                  version,
                  deliveryConfig.name,
                  environment.name,
                  deliveryConfig.application
                )
                publisher.publishEvent(
                  ArtifactVersionApproved(
                    deliveryConfig.application,
                    deliveryConfig.name,
                    environment.name,
                    artifact.name,
                    artifact.type,
                    version
                  )
                )
              }
            }
          }
        }
      }
  }

  private fun checkConstraints(
    evaluators: List<ConstraintEvaluator<*>>,
    artifact: DeliveryArtifact,
    deliveryConfig: DeliveryConfig,
    version: String,
    environment: Environment
  ): Boolean {
    return if (evaluators.isEmpty()) {
      true
    } else {
      evaluators.all { evaluator ->
        !environment.hasSupportedConstraint(evaluator) ||
          evaluator.canPromote(artifact, version, deliveryConfig, environment)
      }
    }
  }

  private fun Environment.hasSupportedConstraint(constraintEvaluator: ConstraintEvaluator<*>) =
    constraints.any { it.javaClass.isAssignableFrom(constraintEvaluator.constraintType) }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
