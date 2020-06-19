package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.actuation.EnvironmentConstraintRunner.EnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionApproved
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * This class is responsible for approving artifacts in environments
 */
@Component
class EnvironmentPromotionChecker(
  private val repository: KeelRepository,
  private val constraintRunner: EnvironmentConstraintRunner,
  private val publisher: ApplicationEventPublisher
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

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
            val envContext = EnvironmentContext(
              deliveryConfig = deliveryConfig,
              environment = environment,
              artifact = artifact,
              versions = versions,
              vetoedVersions = (vetoedArtifacts[envPinKey(environment.name, artifact)]?.versions) ?: emptySet()
            )

            if (pinnedEnvs.hasPinFor(environment.name, artifact)) {
              val pinnedVersion = pinnedEnvs.versionFor(environment.name, artifact)
              // approve version first to fast track deployment
              approveVersion(deliveryConfig, artifact, pinnedVersion!!, environment.name)
              // then evaluate constraints
              constraintRunner.checkEnvironment(envContext)
            } else {
              constraintRunner.checkEnvironment(envContext)

              // everything the constraint runner has already approved
              val queuedForApproval: MutableSet<String> = repository
                .getQueuedConstraintApprovals(deliveryConfig.name, environment.name)
                .toMutableSet()

              /**
               * Approve all constraints starting with oldest first so that the ordering is
               * maintained.
               */
              queuedForApproval
                .sortedWith(artifact.versioningStrategy.comparator.reversed())
                .forEach { v ->
                  /**
                   * We don't need to re-invoke stateful constraint evaluators for these, but we still
                   * check stateless constraints to avoid approval outside of allowed-times.
                   */
                  log.debug("Version $v of artifact ${artifact.name} is queued for approval, " +
                    "and being evaluated for stateless constraints in environment ${environment.name}")
                  if (constraintRunner.checkStatelessConstraints(artifact, deliveryConfig, v, environment)) {
                    approveVersion(deliveryConfig, artifact, v, environment.name)
                    repository.deleteQueuedConstraintApproval(deliveryConfig.name, environment.name, v)
                  }
                }

              val versionSelected = queuedForApproval
                .sortedWith(artifact.versioningStrategy.comparator.reversed())
                .lastOrNull()
              if (versionSelected == null) {
                log.warn("No version of {} passes constraints for environment {}", artifact.name, environment.name)
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
    log.debug("Approving version $version of ${artifact.type} artifact ${artifact.name} in environment $targetEnvironment")
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

  fun envPinKey(environmentName: String, artifact: DeliveryArtifact): String =
    "$environmentName:${artifact.reference}"
}
