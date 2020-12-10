package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.api.artifacts.DEFAULT_MAX_ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.artifacts.generateCompareLink
import com.netflix.spinnaker.keel.core.api.AllowedTimesConstraintMetadata
import com.netflix.spinnaker.keel.core.api.ArtifactSummary
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.ArtifactVersionSummary
import com.netflix.spinnaker.keel.core.api.DependOnConstraintMetadata
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.PromotionStatus.APPROVED
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.DEPLOYING
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PENDING
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.core.api.PromotionStatus.SKIPPED
import com.netflix.spinnaker.keel.core.api.ResourceArtifactSummary
import com.netflix.spinnaker.keel.core.api.ResourceSummary
import com.netflix.spinnaker.keel.core.api.StatefulConstraintSummary
import com.netflix.spinnaker.keel.core.api.StatelessConstraintSummary
import com.netflix.spinnaker.keel.core.api.TimeWindowConstraint
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.keel.exceptions.InvalidSystemStateException
import com.netflix.spinnaker.keel.exceptions.InvalidVetoException
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.persistence.ArtifactNotFoundException
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Service object that offers high-level APIs for application-related operations.
 */
@Component
class ApplicationService(
  private val repository: KeelRepository,
  private val resourceStatusService: ResourceStatusService,
  private val constraintEvaluators: List<ConstraintEvaluator<*>>,
  private val artifactSuppliers: List<ArtifactSupplier<*, *>>,
  private val scmInfo: ScmInfo,
  private val lifecycleEventRepository: LifecycleEventRepository
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val statelessEvaluators: List<ConstraintEvaluator<*>> =
    constraintEvaluators.filter { !it.isImplicit() && it !is StatefulConstraintEvaluator<*, *> }

  fun hasManagedResources(application: String) = repository.hasManagedResources(application)

  fun getDeliveryConfig(application: String) = repository.getDeliveryConfigForApplication(application)

  fun deleteConfigByApp(application: String) = repository.deleteDeliveryConfigByApplication(application)

  fun getConstraintStatesFor(application: String) = repository.constraintStateFor(application)

  fun getConstraintStatesFor(application: String, environment: String, limit: Int): List<ConstraintState> {
    val config = repository.getDeliveryConfigForApplication(application)
    return repository.constraintStateFor(config.name, environment, limit)
  }

  fun updateConstraintStatus(user: String, application: String, environment: String, status: UpdatedConstraintStatus) {
    val config = repository.getDeliveryConfigForApplication(application)
    val currentState = repository.getConstraintState(
      config.name,
      environment,
      status.artifactVersion,
      status.type,
      status.artifactReference
    ) ?: throw InvalidConstraintException(
      "${config.name}/$environment/${status.type}/${status.artifactVersion}", "constraint not found"
    )

    repository.storeConstraintState(
      currentState.copy(
        status = status.status,
        comment = status.comment ?: currentState.comment,
        judgedAt = Instant.now(),
        judgedBy = user
      )
    )
  }

  fun pin(user: String, application: String, pin: EnvironmentArtifactPin) {
    val config = repository.getDeliveryConfigForApplication(application)
    repository.pinEnvironment(config, pin.copy(pinnedBy = user))
    // TODO: publish ArtifactPinnedEvent
  }

  fun deletePin(user: String, application: String, targetEnvironment: String, reference: String? = null) {
    val config = repository.getDeliveryConfigForApplication(application)
    repository.deletePin(config, targetEnvironment, reference)
    // TODO: publish ArtifactUnpinnedEvent
  }

  fun markAsVetoedIn(user: String, application: String, veto: EnvironmentArtifactVeto, force: Boolean) {
    val config = repository.getDeliveryConfigForApplication(application)
    val succeeded = repository.markAsVetoedIn(
      deliveryConfig = config,
      veto = veto.copy(vetoedBy = user),
      force = force
    )
    if (!succeeded) {
      throw InvalidVetoException(application, veto.targetEnvironment, veto.reference, veto.version)
    }
    // TODO: publish ArtifactVetoedEvent
  }

  fun deleteVeto(application: String, targetEnvironment: String, reference: String, version: String) {
    val config = repository.getDeliveryConfigForApplication(application)
    val artifact = config.matchingArtifactByReference(reference)
      ?: throw ArtifactNotFoundException(reference, config.name)
    repository.deleteVeto(
      deliveryConfig = config,
      artifact = artifact,
      version = version,
      targetEnvironment = targetEnvironment
    )
  }

  fun getSummariesAllEntities(application: String): Map<String, Any> {
    val summaries: MutableMap<String, Any> = mutableMapOf()
    summaries["resources"] = getResourceSummariesFor(application)
    val envSummary = getEnvironmentSummariesFor(application)
    summaries["environments"] = envSummary
    summaries["artifacts"] = getArtifactSummariesFor(application, envSummary)
    return summaries
  }

  /**
   * Returns a list of [ResourceSummary] for the specified application.
   */
  fun getResourceSummariesFor(application: String): List<ResourceSummary> {
    return try {
      val deliveryConfig = repository.getDeliveryConfigForApplication(application)
      return deliveryConfig.resources.map { resource ->
        resource.toResourceSummary(deliveryConfig)
      }
    } catch (e: NoSuchDeliveryConfigException) {
      emptyList()
    }
  }

  private fun Resource<*>.toResourceSummary(deliveryConfig: DeliveryConfig) =
    ResourceSummary(
      resource = this,
      status = resourceStatusService.getStatus(id),
      locations = if (spec is Locatable<*>) {
        (spec as Locatable<*>).locations
      } else {
        null
      },
      artifact = findAssociatedArtifact(deliveryConfig)
        ?.let {
          ResourceArtifactSummary(it.name, it.type, it.reference)
        }
    )

  /**
   * Returns a list of [EnvironmentSummary] for the specific application.
   *
   * This function assumes there's a single delivery config associated with the application.
   */
  fun getEnvironmentSummariesFor(application: String): List<EnvironmentSummary> =
    try {
      val config = repository.getDeliveryConfigForApplication(application)
      repository.getEnvironmentSummaries(config)
    } catch (e: NoSuchDeliveryConfigException) {
      emptyList()
    }

  /**
   * Returns a list of [ArtifactSummary] for the specified application by traversing the list of [EnvironmentSummary]
   * for the application and reindexing the data so that it matches the right format.
   *
   * The list is capped at the specified [limit] of artifact versions, sorted in descending version order.
   *
   * This function assumes there's a single delivery config associated with the application.
   */
  fun getArtifactSummariesFor(application: String, limit: Int = DEFAULT_MAX_ARTIFACT_VERSIONS): List<ArtifactSummary> {
    val environmentSummaries = getEnvironmentSummariesFor(application)
    return getArtifactSummariesFor(application, environmentSummaries, limit)
  }

  /**
   * If we've already calculated the env summaries, pass them in so we don't have to query again.
   * It's non-trivial to pull that data.
   */
  fun getArtifactSummariesFor(application: String, envSummaries: List<EnvironmentSummary>, limit: Int = DEFAULT_MAX_ARTIFACT_VERSIONS): List<ArtifactSummary> {
    val deliveryConfig = try {
      repository.getDeliveryConfigForApplication(application)
    } catch (e: NoSuchDeliveryConfigException) {
      return emptyList()
    }

    val artifactSummaries = deliveryConfig.artifacts.map { artifact ->
      val artifactVersionSummaries = repository.artifactVersions(artifact, limit).map { artifactVersion ->
        val artifactSummariesInEnvironments = mutableSetOf<ArtifactSummaryInEnvironment>()

        envSummaries.forEach { environmentSummary ->
          val environment = deliveryConfig.environments.find { it.name == environmentSummary.name }!!
          environmentSummary.getArtifactPromotionStatus(artifact, artifactVersion.version)
            ?.let { status ->
              if ( artifact.isUsedIn(environment)) { // only add a summary if the artifact is used in the environment
                buildArtifactSummaryInEnvironment(deliveryConfig, environment.name, artifact, artifactVersion.version, status)
                  ?.also {
                    artifactSummariesInEnvironments.add(
                      it.addStatefulConstraintSummaries(deliveryConfig, environment, artifactVersion.version)
                        .addStatelessConstraintSummaries(deliveryConfig, environment, artifactVersion.version, artifact)
                    )
                  }
              }
            }
        }

        buildArtifactVersionSummary(artifact, artifactVersion.version, artifactSummariesInEnvironments)
      }
      ArtifactSummary(
        name = artifact.name,
        type = artifact.type,
        reference = artifact.reference,
        versions = artifactVersionSummaries.toSet()
      )
    }

    return artifactSummaries
  }

  private fun buildArtifactSummaryInEnvironment(deliveryConfig: DeliveryConfig, environmentName: String, artifact: DeliveryArtifact, version: String, status: PromotionStatus): ArtifactSummaryInEnvironment? {
    val currentArtifact = getArtifactInstance(artifact, version)

    // some environments contain relevant info for skipped artifacts, so
    // try and find that summary before defaulting to less information
    val potentialSummary =
      repository.getArtifactSummaryInEnvironment(
        deliveryConfig = deliveryConfig,
        environmentName = environmentName,
        artifactReference = artifact.reference,
        version = version
      )

    val pinnedArtifact = getPinnedArtifact(deliveryConfig, environmentName, artifact, version)

    return when (status) {
      PENDING -> {
        val olderArtifactVersion = pinnedArtifact?: repository.getArtifactVersionByPromotionStatus(deliveryConfig, environmentName, artifact, CURRENT)
        ArtifactSummaryInEnvironment(
          environment = environmentName,
          version = version,
          state = status.name.toLowerCase(),
          // comparing PENDING (version in question, new code) vs. CURRENT (old code)
          compareLink = generateCompareLink(scmInfo, currentArtifact, olderArtifactVersion, artifact)
        )
      }
      SKIPPED -> {
        if (potentialSummary == null || potentialSummary.state == "pending") {
          ArtifactSummaryInEnvironment(
            environment = environmentName,
            version = version,
            state = status.name.toLowerCase()
          )
        } else {
          potentialSummary
        }
      }

      DEPLOYING, APPROVED -> {
        val olderArtifactVersion = pinnedArtifact?: repository.getArtifactVersionByPromotionStatus(deliveryConfig, environmentName, artifact, CURRENT)
        potentialSummary?.copy(
          // comparing DEPLOYING/APPROVED (version in question, new code) vs. CURRENT (old code)
          compareLink = generateCompareLink(scmInfo, currentArtifact, olderArtifactVersion, artifact)
        )
      }
      PREVIOUS -> {
        val newerArtifactVersion = potentialSummary?.replacedBy?.let { getArtifactInstance(artifact, it) }
        potentialSummary?.copy(
          //comparing PREVIOUS (version in question, old code) vs. the version which replaced it (new code)
          //pinned artifact should not be consider here, as we know exactly which version replace the current one
          compareLink = generateCompareLink(scmInfo, currentArtifact, newerArtifactVersion, artifact)
        )
      }
      CURRENT -> {
        val olderArtifactVersion = pinnedArtifact?: repository.getArtifactVersionByPromotionStatus(deliveryConfig, environmentName, artifact, PREVIOUS)
        potentialSummary?.copy(
          // comparing CURRENT (version in question, new code) vs. PREVIOUS (old code)
          compareLink = generateCompareLink(scmInfo, currentArtifact, olderArtifactVersion, artifact)
        )
      }
      else -> potentialSummary
    }
  }

  // Pinning is a special case when is coming to creating a compare link between versions.
  // If there is a pinned version, which is not the same as the current version, we need
  // to make sure we are creating the comparable link with reference to the pinned version.
  private fun getPinnedArtifact(deliveryConfig: DeliveryConfig, environmentName: String, artifact: DeliveryArtifact, version: String): PublishedArtifact? {
    val pinnedVersion = repository.getPinnedVersion(deliveryConfig, environmentName, artifact.reference)
     return if (pinnedVersion != version)
      pinnedVersion?.let { getArtifactInstance(artifact, it) }
    else { //if pinnedVersion == current version, fetch the version which that the pinned version replaced
       repository.getArtifactVersionByPromotionStatus(deliveryConfig, environmentName, artifact, PREVIOUS, pinnedVersion)
     }
  }

  /**
   * Adds details about any stateful constraints in the given environment to the [ArtifactSummaryInEnvironment].
   * For each constraint type, if it's not yet been evaluated, creates a synthetic constraint summary object
   * with a [ConstraintStatus.NOT_EVALUATED] status.
   */
  private fun ArtifactSummaryInEnvironment.addStatefulConstraintSummaries(
    deliveryConfig: DeliveryConfig,
    environment: Environment,
    version: String
  ): ArtifactSummaryInEnvironment {
    val constraintStates = repository.constraintStateFor(deliveryConfig.name, environment.name, version)
    val notEvaluatedConstraints = environment.constraints.filter { constraint ->
      constraint is StatefulConstraint && constraintStates.none { it.type == constraint.type }
    }.map { constraint ->
      StatefulConstraintSummary(
        type = constraint.type,
        status = NOT_EVALUATED
      )
    }
    return this.copy(
      statefulConstraints = constraintStates
        .map { it.toConstraintSummary() } +
        notEvaluatedConstraints
    )
  }

  /**
   * Adds details about any stateless constraints in the given environment to the [ArtifactSummaryInEnvironment].
   */
  private fun ArtifactSummaryInEnvironment.addStatelessConstraintSummaries(
    deliveryConfig: DeliveryConfig,
    environment: Environment,
    version: String,
    artifact: DeliveryArtifact
  ): ArtifactSummaryInEnvironment {
    val statelessConstraints: List<StatelessConstraintSummary> = environment.constraints.filter { constraint ->
      constraint !is StatefulConstraint
    }.mapNotNull { constraint ->
      statelessEvaluators.find { evaluator ->
        evaluator.supportedType.name == constraint.type
      }?.let {
        StatelessConstraintSummary(
          type = constraint.type,
          currentlyPassing = it.canPromote(artifact, version = version, deliveryConfig = deliveryConfig, targetEnvironment = environment),
          attributes = when (constraint) {
            is DependsOnConstraint -> DependOnConstraintMetadata(constraint.environment)
            is TimeWindowConstraint -> AllowedTimesConstraintMetadata(constraint.windows, constraint.tz)
            else -> null
          }
        )
      }
    }

    return this.copy(
      statelessConstraints = statelessConstraints
    )
  }

  /**
   * Takes an artifact version, plus information about the type of artifact, and constructs a summary view.
   */
  private fun buildArtifactVersionSummary(
    artifact: DeliveryArtifact,
    version: String,
    environments: Set<ArtifactSummaryInEnvironment>
  ): ArtifactVersionSummary {

    val artifactSupplier = artifactSuppliers.supporting(artifact.type)
    val artifactInstance = getArtifactInstance(artifact, version)
      ?: throw InvalidSystemStateException("Loading artifact version $version failed for known artifact $artifact.")
    return ArtifactVersionSummary(
      version = version,
      environments = environments,
      displayName = artifactSupplier.getVersionDisplayName(artifactInstance),
      createdAt = artifactInstance.createdAt,

      // first attempt to use the artifact metadata fetched from the DB, then fallback to the default if not found
      build = artifactInstance.buildMetadata
        ?: artifactSupplier.parseDefaultBuildMetadata(artifactInstance, artifact.sortingStrategy),
      git = artifactInstance.gitMetadata
        ?: artifactSupplier.parseDefaultGitMetadata(artifactInstance, artifact.sortingStrategy),
      lifecycleSteps = lifecycleEventRepository.getSteps(artifact, artifactInstance.version)
    )
  }

  fun getApplicationEventHistory(application: String, limit: Int) =
    repository.applicationEventHistory(application, limit)

  private fun ConstraintState.toConstraintSummary() =
    StatefulConstraintSummary(type, status, createdAt, judgedBy, judgedAt, comment, attributes)

  private fun getArtifactInstance(
    artifact: DeliveryArtifact,
    version: String
  ): PublishedArtifact? {
    val releaseStatus = repository.getReleaseStatus(artifact, version)
    return repository.getArtifactVersion(artifact, version, releaseStatus)
  }

}
