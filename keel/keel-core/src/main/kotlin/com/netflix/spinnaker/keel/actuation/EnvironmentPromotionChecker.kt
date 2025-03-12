package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.config.ArtifactConfig
import com.netflix.spinnaker.keel.actuation.EnvironmentConstraintRunner.EnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.DEPLOYING
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionApproved
import com.netflix.spinnaker.keel.telemetry.EnvironmentCheckComplete
import com.newrelic.api.agent.Trace
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import org.springframework.core.env.Environment as SpringEnvironment

/**
 * This class is responsible for approving artifacts in environments
 */
@Component
@EnableConfigurationProperties(ArtifactConfig::class)
class EnvironmentPromotionChecker(
  private val repository: KeelRepository,
  private val constraintRunner: EnvironmentConstraintRunner,
  private val publisher: ApplicationEventPublisher,
  private val artifactConfig: ArtifactConfig,
  private val springEnv: SpringEnvironment,
  private val clock: Clock
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
        .associateWith { repository.artifactVersions(it, artifactConfig.defaultMaxConsideredVersions) }
        .forEach { (artifact, versions) ->
          if (versions.isEmpty()) {
            log.warn("No versions for ${artifact.type} artifact name ${artifact.name} and reference ${artifact.reference} are known")
          } else {
            deliveryConfig.environments.forEach { environment ->
              if (artifact.isUsedIn(environment)) {

                val latestVersions = versions.map { it.version }
                val versionsToUse = repository
                    .getPendingVersionsInEnvironment(
                      deliveryConfig,
                      artifact.reference,
                      environment.name
                    )
                    .sortedWith(artifact.sortingStrategy.comparator)
                    .map { it.version }
                    .intersect(latestVersions) // only take newest ones so we avoid checking really old versions
                    .toList()

                val envContext = EnvironmentContext(
                  deliveryConfig = deliveryConfig,
                  environment = environment,
                  artifact = artifact,
                  versions = versionsToUse,
                  vetoedVersions = (vetoedArtifacts[envPinKey(environment.name, artifact)]?.versions?.map { it.version })?.toSet()
                    ?: emptySet()
                )

                if (pinnedEnvs.hasPinFor(environment.name, artifact)) {
                  val pinnedVersion = pinnedEnvs.versionFor(environment.name, artifact)
                  // approve version first to fast track deployment
                  approveVersion(deliveryConfig, artifact, pinnedVersion!!, environment)
                  triggerResourceRecheckForPinnedVersion(deliveryConfig, artifact, pinnedVersion!!, environment)
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
                        approveVersion(deliveryConfig, artifact, artifactVersion.version, environment)
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
                  triggerResourceRecheckForVetoedVersion(
                    deliveryConfig,
                    artifact,
                    environment,
                    vetoedArtifacts[envPinKey(environment.name, artifact)]
                  )
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

  private fun triggerResourceRecheckForVetoedVersion(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: Environment,
    vetoedArtifacts: EnvironmentArtifactVetoes?
  ) {
    if (vetoedArtifacts == null) return
    val currentVersion = repository.getCurrentlyDeployedArtifactVersion(deliveryConfig, artifact, targetEnvironment.name)?.version
    if (vetoedArtifacts.versions.map { it.version }.contains(currentVersion)) {
      log.info("Triggering recheck for environment ${targetEnvironment.name} of application ${deliveryConfig.application} that is currently on a vetoed version of ${artifact.reference}")
      // trigger a recheck of the resources if the current version is vetoed
      repository.triggerResourceRecheck(targetEnvironment.name, deliveryConfig.application)
    }
  }

  private fun triggerResourceRecheckForPinnedVersion(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: Environment
  ) {
    val status = repository.getArtifactPromotionStatus(deliveryConfig, artifact, version, targetEnvironment.name)
    if (status !in listOf(CURRENT, DEPLOYING)) {
      log.info("Triggering recheck for pinned environment ${targetEnvironment.name} of application ${deliveryConfig.application} that are on the wrong version. Pinned version $version of ${artifact.reference}")
      // trigger a recheck of the resources if the version isn't already on its way to the environment
      repository.triggerResourceRecheck(targetEnvironment.name, deliveryConfig.application)
    }
  }

  private fun approveVersion(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: Environment
  ) {
    log.debug("Approving application ${deliveryConfig.application} version $version of ${artifact.reference} in environment ${targetEnvironment.name}")
    val isNewVersion = repository
      .approveVersionFor(deliveryConfig, artifact, version, targetEnvironment.name)
    if (isNewVersion) {
      log.info(
        "Approved {} {} version {} for {} environment {} in {}",
        artifact.name,
        artifact.type,
        version,
        deliveryConfig.name,
        targetEnvironment.name,
        deliveryConfig.application
      )

      publisher.publishEvent(
        ArtifactVersionApproved(
          deliveryConfig.application,
          deliveryConfig.name,
          targetEnvironment.name,
          artifact.name,
          artifact.type,
          version
        )
      )

      // persist the status of stateless constraints because their current value is all we care about
      snapshotStatelessConstraintStatus(deliveryConfig, artifact, version, targetEnvironment)

      // recheck all resources in an environment, so action can be taken right away
      repository.triggerResourceRecheck(targetEnvironment.name, deliveryConfig.application)
    }
  }

  /**
   * Save the passing status of all stateless constraints when a version is approved so that
   * their status stays the same forever. We don't want them to be evaluated anymore.
   */
  private fun snapshotStatelessConstraintStatus(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    environment: Environment
  ) {
    constraintRunner.getStatelessConstraintSnapshots(
      artifact = artifact,
      deliveryConfig = deliveryConfig,
      version = version,
      environment = environment,
      currentStatus = PASS // We just checked that all these pass since a version was approved
    ).forEach { constraintState ->
      log.debug("Storing final constraint state snapshot for {} constraint for {} version {} for {} environment {} in {}",
        constraintState.type,
        artifact,
        version,
        deliveryConfig.name,
        environment.name,
        deliveryConfig.application
      )
      repository.storeConstraintState(constraintState)
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
