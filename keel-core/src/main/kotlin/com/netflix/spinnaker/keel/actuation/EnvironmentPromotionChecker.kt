package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.actuation.EnvironmentConstraintRunner.EnvironmentContext
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionApproved
import com.netflix.spinnaker.keel.telemetry.EnvironmentCheckComplete
import com.newrelic.api.agent.Trace
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * This class is responsible for approving artifacts in environments
 */
@Component
class EnvironmentPromotionChecker(
  private val repository: KeelRepository,
  private val constraintRunner: EnvironmentConstraintRunner,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock = Clock.systemDefaultZone()
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Trace(dispatcher = true)
  suspend fun checkEnvironments(deliveryConfig: DeliveryConfig) {
    val startTime = clock.instant()
    try {
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
            log.warn("No versions for ${artifact.type} artifact name ${artifact.name} and reference ${artifact.reference} are known")
          } else {
            deliveryConfig.environments.forEach { environment ->
              if (artifact.isUsedIn(environment)) {
                val envContext = EnvironmentContext(
                  deliveryConfig = deliveryConfig,
                  environment = environment,
                  artifact = artifact,
                  versions = versions.map { it.version },
                  vetoedVersions = (vetoedArtifacts[envPinKey(environment.name, artifact)]?.versions)
                    ?: emptySet()
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
                  val queuedForApproval = repository
                    .getArtifactVersionsQueuedForApproval(deliveryConfig.name, environment.name, artifact)
                    .toMutableList()

                  /**
                   * Approve all constraints starting with oldest first so that the ordering is
                   * maintained.
                   */
                  queuedForApproval
                    .reversed()
                    .forEach { artifactVersion ->
                      /**
                       * We don't need to re-invoke stateful constraint evaluators for these, but we still
                       * check stateless constraints to avoid approval outside of allowed-times.
                       */
                      log.debug(
                        "Version ${artifactVersion.version} of artifact ${artifact.name} is queued for approval, " +
                          "and being evaluated for stateless constraints in environment ${environment.name}"
                      )
                      if (constraintRunner.checkStatelessConstraints(artifact, deliveryConfig, artifactVersion.version, environment)) {
                        approveVersion(deliveryConfig, artifact, artifactVersion.version, environment.name)
                        repository.deleteArtifactVersionQueuedForApproval(
                          deliveryConfig.name, environment.name, artifact, artifactVersion.version)
                      } else {
                        log.debug("Version ${artifactVersion.version} of $artifact does not currently pass stateless constraints")
                        queuedForApproval.remove(artifactVersion)
                      }
                    }

                  val versionSelected = queuedForApproval.firstOrNull()
                  if (versionSelected == null) {
                    log.warn("No version of {} passes constraints for environment {}", artifact, environment.name)
                  }
                }
              } else {
                log.debug("Skipping checks for {} as it is not used in environment {}", artifact, environment.name)
              }
            }
          }
        }
    } finally {
      publisher.publishEvent(
        EnvironmentCheckComplete(
          application = deliveryConfig.application,
          deliveryConfigName = deliveryConfig.name,
          duration = Duration.between(startTime, clock.instant())
        )
      )
    }
  }

  /**
   * @return `true` if this artifact is used by any resource in [environment], `false` otherwise.
   */
  private fun DeliveryArtifact.isUsedIn(environment: Environment) =
    environment
      .resources
      .map { (it.spec as? ArtifactReferenceProvider)?.artifactReference }
      .contains(reference)

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
        deliveryConfig.application
      )

      publisher.publishEvent(
        ArtifactVersionApproved(
          deliveryConfig.application,
          deliveryConfig.name,
          targetEnvironment,
          artifact.name,
          artifact.type,
          version
        )
      )
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
