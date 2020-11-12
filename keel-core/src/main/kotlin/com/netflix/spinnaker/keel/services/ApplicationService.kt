package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.api.artifacts.DEFAULT_MAX_ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.supporting
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
import com.netflix.spinnaker.keel.exceptions.UnsupportedScmType
import com.netflix.spinnaker.keel.persistence.ArtifactNotFoundException
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigException
import kotlinx.coroutines.runBlocking
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
  private val scmInfo: ScmInfo
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
    val deliveryConfig = try {
      repository.getDeliveryConfigForApplication(application)
    } catch (e: NoSuchDeliveryConfigException) {
      return emptyList()
    }

    val environmentSummaries = getEnvironmentSummariesFor(application)

    val artifactSummaries = deliveryConfig.artifacts.map { artifact ->
      val artifactVersionSummaries = repository.artifactVersions(artifact, limit).map { version ->
        val artifactSummariesInEnvironments = mutableSetOf<ArtifactSummaryInEnvironment>()

        environmentSummaries.forEach { environmentSummary ->
          val environment = deliveryConfig.environments.find { it.name == environmentSummary.name }!!
          environmentSummary.getArtifactPromotionStatus(artifact, version)
            ?.let { status ->
              buildArtifactSummaryInEnvironment(deliveryConfig, environment.name, artifact, version, status)
                ?.also {
                  artifactSummariesInEnvironments.add(
                    it.addStatefulConstraintSummaries(deliveryConfig, environment, version)
                      .addStatelessConstraintSummaries(deliveryConfig, environment, version, artifact)
                  )
                }
            }
        }

        buildArtifactVersionSummary(artifact, version, artifactSummariesInEnvironments)
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
    val artifactGitMetadata = getArtifactInstance(artifact, version)?.gitMetadata
    val baseScmUrl = artifactGitMetadata?.commitInfo?.link?.let { getScmBaseLink(it) }

    // some environments contain relevant info for skipped artifacts, so
    // try and find that summary before defaulting to less information
    val potentialSummary =
      repository.getArtifactSummaryInEnvironment(
        deliveryConfig = deliveryConfig,
        environmentName = environmentName,
        artifactReference = artifact.reference,
        version = version
      )

    return when (status) {
      PENDING -> {
        val olderGitMetadata = repository.getGitMetadataByPromotionStatus(deliveryConfig, environmentName, artifact, CURRENT.name)

        ArtifactSummaryInEnvironment(
          environment = environmentName,
          version = version,
          state = status.name.toLowerCase(),
          // comparing PENDING (version in question, new code) vs. CURRENT (old code)
          compareLink = generateDiffLink(baseScmUrl, artifactGitMetadata, olderGitMetadata)
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
      DEPLOYING -> {
        val olderGitMetadata = repository.getGitMetadataByPromotionStatus(deliveryConfig, environmentName, artifact, CURRENT.name)
        potentialSummary?.copy(
          // comparing DEPLOYING (version in question, new code) vs. CURRENT (old code)
          compareLink = generateDiffLink(baseScmUrl, artifactGitMetadata, olderGitMetadata)
        )
      }
      PREVIOUS -> {
        val newerGitMetadata = potentialSummary?.replacedBy?.let { getArtifactInstance(artifact, it)?.gitMetadata }
        potentialSummary?.copy(
          //comparing PREVIOUS (version in question, old code) vs. the version which replaced it (new code)
          compareLink = generateDiffLink(baseScmUrl, newerGitMetadata, artifactGitMetadata)
        )
      }
      CURRENT -> {
        val olderGitMetadata = repository.getGitMetadataByPromotionStatus(deliveryConfig, environmentName, artifact, PREVIOUS.name)
        potentialSummary?.copy(
          // comparing CURRENT (version in question, new code) vs. PREVIOUS (old code)
          compareLink = generateDiffLink(baseScmUrl, artifactGitMetadata, olderGitMetadata)
        )
      }
      else -> potentialSummary
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
        ?: artifactSupplier.parseDefaultBuildMetadata(artifactInstance, artifact.versioningStrategy),
      git = artifactInstance.gitMetadata
        ?: artifactSupplier.parseDefaultGitMetadata(artifactInstance, artifact.versioningStrategy)
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
    return repository.getArtifactInstance(artifact.name, artifact.type, version, releaseStatus)
  }

  // Generating a SCM diff link between source and target versions (the order does matter!)
  private fun generateDiffLink(baseUrl: String?, newerGitMetadata: GitMetadata?, olderGitMetadata: GitMetadata?): String? {
    return if (baseUrl != null && newerGitMetadata != null && olderGitMetadata != null) {
          "$baseUrl/projects/${newerGitMetadata.project}/repos/${newerGitMetadata.repo?.name}/compare/commits?" +
            "targetBranch=${olderGitMetadata.commitInfo?.sha}&sourceBranch=${newerGitMetadata.commitInfo?.sha}"
    } else {
      null
    }
  }

  // Calling igor to fetch all base urls by SCM type, and returning the right one based on current commit link
  private fun getScmBaseLink(commitLink: String): String? {
    val scmInfo = runBlocking {
      scmInfo.getScmInfo()
    }
    //TODO[gyardeni]: replace this parsing when rocket will add scm type to gitMetadata
    when {
      "stash" in commitLink ->
        return scmInfo["stash"]
      else ->
        throw UnsupportedScmType(message = "Stash is currently the only supported SCM type")
    }
  }
}
