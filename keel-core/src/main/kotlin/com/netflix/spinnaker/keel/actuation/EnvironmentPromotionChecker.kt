package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.anyStateful
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.comparator
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionApproved
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class EnvironmentPromotionChecker(
  private val repository: KeelRepository,
  private val constraints: List<ConstraintEvaluator<*>>,
  private val publisher: ApplicationEventPublisher
) {
  private val statefulEvaluators: List<ConstraintEvaluator<*>> = constraints
    .filterIsInstance<StatefulConstraintEvaluator<*>>()
  private val statelessEvaluators = constraints - statefulEvaluators

  suspend fun checkEnvironments(deliveryConfig: DeliveryConfig) {
    val pinnedEnvs: Map<String, PinnedEnvironment> = repository
      .pinnedEnvironments(deliveryConfig)
      .associateBy { envPinKey(it.targetEnvironment, it.artifact) }
    val vetoedArtifacts: Map<String, EnvironmentArtifactVetoes> = repository
      .vetoedEnvironmentVersions(deliveryConfig)
      .associateBy { envPinKey(it.targetEnvironment, it.artifact) }

    deliveryConfig
      .artifacts
      .associateWith { repository.artifactVersions(it) }
      .forEach { (artifact, versions) ->
        if (versions.isEmpty()) {
          log.warn("No versions for ${artifact.type} artifact ${artifact.name} are known")
        } else {
          deliveryConfig.environments.forEach { environment ->
            var hasPin = false
            var versionIsPending = false
            val pendingVersionsToCheck: MutableSet<String> =
              when (environment.constraints.anyStateful) {
                true -> repository
                  .pendingConstraintVersionsFor(deliveryConfig.name, environment.name)
                  .filter { versions.contains(it) }
                  .toMutableSet()
                false -> mutableSetOf()
              }

            val queuedForApproval: MutableSet<String> =
              when (environment.constraints.anyStateful) {
                true -> repository
                  .getQueuedConstraintApprovals(deliveryConfig.name, environment.name)
                  .toMutableSet()
                false -> mutableSetOf()
              }

            val vetoedVersions: Set<String> =
              (vetoedArtifacts[envPinKey(environment.name, artifact)]?.versions)
                ?: emptySet()

            val version = when {
              pinnedEnvs.hasPinFor(environment.name, artifact) -> {
                hasPin = true
                pinnedEnvs.versionFor(environment.name, artifact)
              }
              environment.constraints.isEmpty() -> {
                versions.firstOrNull { v ->
                  !vetoedVersions.contains(v)
                }
              }
              else -> {
                versions
                  .firstOrNull { v ->
                    pendingVersionsToCheck.remove(v)
                    queuedForApproval.remove(v)
                    if (vetoedVersions.contains(v)) {
                      false
                    } else {
                      /**
                       * Only check stateful evaluators if all stateless evaluators pass. We don't
                       * want to request judgement or deploy a canary for artifacts that aren't
                       * deployed to a required environment or outside of an allowed time.
                       */
                      val passesConstraints =
                        checkConstraints(statelessEvaluators, artifact, deliveryConfig, v, environment) &&
                          checkConstraints(statefulEvaluators, artifact, deliveryConfig, v, environment)

                      versionIsPending = when (environment.constraints.anyStateful) {
                        true -> repository
                          .constraintStateFor(deliveryConfig.name, environment.name, v)
                          .any { it.status == PENDING }
                        else -> false
                      }
                      passesConstraints || versionIsPending
                    }
                  }
              }
            }

            /**
             * If there are pending constraints for prior versions, recheck in ascending version order
             * so they can be timed out, failed, or approved. If a newer version was selected above,
             * it is approved last in an attempt to retain ENVIRONMENT_ARTIFACT_VERSIONS.APPROVED_AT
             * ordering.
             */
            var approvedPending = false
            if (!hasPin) {
              pendingVersionsToCheck
                .sortedWith(artifact.versioningStrategy.comparator.reversed()) // oldest first
                .forEach {
                  val passesConstraints: Boolean =
                    checkConstraints(statelessEvaluators, artifact, deliveryConfig, it, environment) &&
                    checkConstraints(statefulEvaluators, artifact, deliveryConfig, it, environment)

                  if (passesConstraints) {
                    approveVersion(deliveryConfig, artifact, it, environment.name)
                    approvedPending = true
                  }
                }

              queuedForApproval
                .sortedWith(artifact.versioningStrategy.comparator.reversed())
                .forEach { v ->
                  /**
                   * We don't need to re-invoke stateful constraint evaluators for these, but we still
                   * check stateless constraints to avoid approval outside of allowed-times.
                   */
                  if (checkConstraints(statelessEvaluators, artifact, deliveryConfig, v, environment)) {
                    approveVersion(deliveryConfig, artifact, v, environment.name)
                    repository.deleteQueuedConstraintApproval(deliveryConfig.name, environment.name, v)
                  }
                }
            }
            if (!approvedPending && versionIsPending || version == null) {
              log.warn("No version of {} passes constraints for environment {}", artifact.name, environment.name)
            } else {
              if (!versionIsPending) {
                approveVersion(deliveryConfig, artifact, version, environment.name)
              }
            }
          }
        }
      }
  }

  private fun approveVersion(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ) {
    val isNewVersion = repository
      .approveVersionFor(deliveryConfig, artifact, version, targetEnvironment)
    if (isNewVersion) {
      log.info(
        "Approved {} {} version {} for {} environment {} in {}",
        artifact.name,
        artifact.type,
        version,
        deliveryConfig.name,
        targetEnvironment,
        deliveryConfig.application)

      publisher.publishEvent(
        ArtifactVersionApproved(
          deliveryConfig.application,
          deliveryConfig.name,
          targetEnvironment,
          artifact.name,
          artifact.type,
          version))
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
