package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.PinnedEnvironment
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

  suspend fun checkEnvironments(deliveryConfig: DeliveryConfig) {
    val pinnedEnvs: Map<String, PinnedEnvironment> = artifactRepository
      .pinnedEnvironments(deliveryConfig)
      .associateBy { envPinKey(it.targetEnvironment, it.artifact) }

    deliveryConfig
      .artifacts
      .associateWith { artifactRepository.versions(it) }
      .forEach { (artifact, versions) ->
        if (versions.isEmpty()) {
          log.warn("No versions for ${artifact.type} artifact ${artifact.name} are known")
        } else {
          deliveryConfig.environments.forEach { environment ->
            val version = when {
              pinnedEnvs.hasPinFor(environment.name, artifact) -> {
                pinnedEnvs.versionFor(environment.name, artifact)
              }
              environment.constraints.isEmpty() -> {
                versions.firstOrNull()
              }
              else -> {
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
    constraints.any { it.javaClass.isAssignableFrom(constraintEvaluator.supportedType.type) }

  private fun Map<String, PinnedEnvironment>.hasPinFor(
    environmentName: String,
    artifact: DeliveryArtifact
  ): Boolean {
    if (isEmpty()) {
      return false
    }

    val key = envPinKey(environmentName, artifact)
    return containsKey(key) && checkNotNull(get(key)).artifact == artifact
  }

  private fun Map<String, PinnedEnvironment>.versionFor(
    environmentName: String,
    artifact: DeliveryArtifact
  ): String? =
    get(envPinKey(environmentName, artifact))?.version

  private fun envPinKey(environmentName: String, artifact: DeliveryArtifact): String =
    "$environmentName:${artifact.name}:${artifact.type.name.toLowerCase()}"

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
