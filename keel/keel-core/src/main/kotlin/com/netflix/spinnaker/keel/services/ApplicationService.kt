package com.netflix.spinnaker.keel.services

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.ArtifactConfig
import com.netflix.spinnaker.keel.actuation.EnvironmentTaskCanceler
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.StatelessConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.constraints.DependsOnConstraintAttributes
import com.netflix.spinnaker.keel.core.api.ArtifactSummary
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.ArtifactVersionSummary
import com.netflix.spinnaker.keel.core.api.ConstraintSummary
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
import com.netflix.spinnaker.keel.core.api.VerificationSummary
import com.netflix.spinnaker.keel.events.MarkAsBadNotification
import com.netflix.spinnaker.keel.events.PinnedNotification
import com.netflix.spinnaker.keel.events.UnpinnedNotification
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.keel.exceptions.InvalidSystemStateException
import com.netflix.spinnaker.keel.exceptions.InvalidVetoException
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.logging.TracingSupport.Companion.blankMDC
import com.netflix.spinnaker.keel.persistence.ArtifactNotFoundException
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoDeliveryConfigForApplication
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigException
import com.netflix.spinnaker.keel.telemetry.InvalidVerificationIdSeen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import org.springframework.core.env.Environment as SpringEnvironment

/**
 * Service object that offers high-level APIs for application-related operations.
 */
@Component
@EnableConfigurationProperties(ArtifactConfig::class)
class ApplicationService(
  private val repository: KeelRepository,
  private val resourceStatusService: ResourceStatusService,
  constraintEvaluators: List<ConstraintEvaluator<*>>,
  private val artifactSuppliers: List<ArtifactSupplier<*, *>>,
  private val lifecycleEventRepository: LifecycleEventRepository,
  private val publisher: ApplicationEventPublisher,
  private val springEnv: SpringEnvironment,
  private val clock: Clock,
  private val spectator: Registry,
  private val artifactConfig: ArtifactConfig,
  private val artifactVersionLinks: ArtifactVersionLinks,
  private val environmentTaskCanceler: EnvironmentTaskCanceler
) : CoroutineScope {
  override val coroutineContext: CoroutineContext = Dispatchers.Default

  companion object {
    //attributes that should be stripped before being returned through the api
    val privateConstraintAttrs = listOf("manual-judgement")
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val verificationsEnabled: Boolean
    get() = springEnv.getProperty("keel.verifications.summary.enabled", Boolean::class.java, false)

  private val now: Instant
    get() = clock.instant()

  private val RESOURCE_SUMMARY_CONSTRUCT_DURATION_ID = "keel.api.resource.summary.duration"
  private val ENV_SUMMARY_CONSTRUCT_DURATION_ID = "keel.api.environment.summary.duration"
  private val ARTIFACT_SUMMARY_CONSTRUCT_DURATION_ID = "keel.api.artifact.summary.duration"
  private val ARTIFACT_IN_ENV_SUMMARY_CONSTRUCT_DURATION = "keel.api.artifact.in.environment.summary.duration"
  private val ARTIFACT_VERSION_SUMMARY_CONSTRUCT_DURATION_ID = "keel.api.artifact.version.summary.duration"

  private val statelessEvaluators: List<ConstraintEvaluator<*>> =
    constraintEvaluators.filter { !it.isImplicit() && it !is StatefulConstraintEvaluator<*, *> }

  private val snapshottedStatelessConstraintAttrs: List<String> = constraintEvaluators
    .filterIsInstance<StatelessConstraintEvaluator<*,*>>()
    .map { it.attributeType.name }

  fun hasManagedResources(application: String) = repository.hasManagedResources(application)

  fun getDeliveryConfig(application: String) = repository.getDeliveryConfigForApplication(application)

  fun deleteConfigByApp(application: String) {
    launch(blankMDC) {
      try {
        repository.deleteDeliveryConfigByApplication(application)
      } catch(ex: NoDeliveryConfigForApplication) {
        log.info("attempted to delete delivery config for app that doesn't have a config: $application")
      }
    }
  }

  fun getConstraintStatesFor(application: String): List<ConstraintState> =
    repository
      .constraintStateFor(application)
      .filterNot {
        // remove snapshotted "stateless" constraints from this list
        snapshottedStatelessConstraintAttrs.contains(it.type)
      }
      .removePrivateConstraintAttrs()

  fun getConstraintStatesFor(application: String, environment: String, limit: Int): List<ConstraintState> {
    val config = repository.getDeliveryConfigForApplication(application)
    return repository.constraintStateFor(config.name, environment, limit).removePrivateConstraintAttrs()
  }

  fun updateConstraintStatus(user: String, application: String, environment: String, status: UpdatedConstraintStatus): Boolean {
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

    val newState = currentState.copy(
      status = status.status,
      comment = status.comment ?: currentState.comment,
      judgedAt = Instant.now(),
      judgedBy = user
    )
    repository.storeConstraintState(newState)
    repository.triggerDeliveryConfigRecheck(application) // recheck environments to fast track a deployment

    if (currentState.status != newState.status){
      return true
    }
    return false
  }

  fun pin(user: String, application: String, pin: EnvironmentArtifactPin) {
    log.info("Pinning application $application by user $user: {}", pin)
    val config = repository.getDeliveryConfigForApplication(application)
    repository.pinEnvironment(config, pin.copy(pinnedBy = user))
    environmentTaskCanceler.cancelTasksForPin(application, pin, user)
    repository.triggerDeliveryConfigRecheck(application) // recheck environments to reflect pin immediately
    publisher.publishEvent(PinnedNotification(config, pin.copy(pinnedBy = user)))
  }

  fun deletePin(user: String, application: String, targetEnvironment: String, reference: String? = null) {
    val config = repository.getDeliveryConfigForApplication(application)
    val pinnedEnvironment = repository.pinnedEnvironments(config).find { it.targetEnvironment == targetEnvironment }
    repository.deletePin(config, targetEnvironment, reference)
    repository.triggerDeliveryConfigRecheck(application) // recheck environments to reflect pin removal immediately

    publisher.publishEvent(UnpinnedNotification(config,
      pinnedEnvironment,
      targetEnvironment,
      user))
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
    environmentTaskCanceler.cancelTasksForVeto(application, veto, user)
    repository.triggerDeliveryConfigRecheck(application) // recheck environments to reflect veto immediately
    publisher.publishEvent(MarkAsBadNotification(
      config = config,
      user = user,
      veto = veto.copy(vetoedBy = user)
    ))
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
    repository.triggerDeliveryConfigRecheck(application) // recheck environments to reflect removed veto immediately
  }

  fun getSummariesAllEntities(application: String): Map<String, Any> {
    val summaries: MutableMap<String, Any> = mutableMapOf()
    summaries["resources"] = getResourceSummariesFor(application)
    val envSummary = getEnvironmentSummariesFor(application)
    summaries["environments"] = envSummary
    summaries["artifacts"] = getArtifactSummariesFor(application, envSummary, artifactConfig.defaultMaxConsideredVersions)
    return summaries
  }

  /**
   * Returns a list of [ResourceSummary] for the specified application.
   */
  fun getResourceSummariesFor(application: String): List<ResourceSummary> {
    return try {
      val startTime = now
      val deliveryConfig = repository.getDeliveryConfigForApplication(application)
      val summaries = getResourceSummaries(deliveryConfig)
      spectator.timer(
        RESOURCE_SUMMARY_CONSTRUCT_DURATION_ID,
        listOf(BasicTag("application", application))
      ).record(Duration.between(startTime, now))
      summaries
    } catch (e: NoSuchDeliveryConfigException) {
      emptyList()
    }
  }

  fun getResourceSummaries(deliveryConfig: DeliveryConfig): List<ResourceSummary> =
    deliveryConfig.resources.map { resource ->
      resource.toResourceSummary(deliveryConfig)
    }

  fun Resource<*>.toResourceSummary(deliveryConfig: DeliveryConfig) =
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
      val startTime = now
      val config = repository.getDeliveryConfigForApplication(application)
      val summaries = repository.getEnvironmentSummaries(config)
      spectator.timer(
        ENV_SUMMARY_CONSTRUCT_DURATION_ID,
        listOf(BasicTag("application", application))
      ).record(Duration.between(startTime, now))
      summaries.sortedByDependencies()
    } catch (e: NoSuchDeliveryConfigException) {
      emptyList()
    }

  private fun List<EnvironmentSummary>.sortedByDependencies() =
    sortedWith { env1, env2 ->
      when {
        env1.dependsOn(env2) -> 1
        env2.dependsOn(env1) -> -1
        env1.hasDependencies() && !env2.hasDependencies() -> 1
        env2.hasDependencies() && !env1.hasDependencies() -> -1
        else -> 0
      }
    }

  private fun EnvironmentSummary.dependsOn(another: EnvironmentSummary) =
    environment.constraints.any { it is DependsOnConstraint && it.environment == another.environment.name }

  private fun EnvironmentSummary.hasDependencies() =
    environment.constraints.any { it is DependsOnConstraint }

  /**
   * Returns a list of [ArtifactSummary] for the specified application by traversing the list of [EnvironmentSummary]
   * for the application and reindexing the data so that it matches the right format.
   *
   * The list is capped at the specified [limit] of artifact versions, sorted in descending version order.
   *
   * This function assumes there's a single delivery config associated with the application.
   */
  fun getArtifactSummariesFor(application: String, limit: Int): List<ArtifactSummary> {
    val startTime = now
    val environmentSummaries = getEnvironmentSummariesFor(application)
    spectator.timer(
      ENV_SUMMARY_CONSTRUCT_DURATION_ID,
      listOf(BasicTag("application", application))
    ).record(Duration.between(startTime, now))
    return getArtifactSummariesFor(application, environmentSummaries, limit)
  }

  /**
   * If we've already calculated the env summaries, pass them in so we don't have to query again.
   * It's non-trivial to pull that data.
   */
  fun getArtifactSummariesFor(application: String, envSummaries: List<EnvironmentSummary>, limit: Int): List<ArtifactSummary> {
    val startTime = now
    val deliveryConfig = try {
      repository.getDeliveryConfigForApplication(application)
    } catch (e: NoSuchDeliveryConfigException) {
      return emptyList()
    }

    val artifactSummaries = deliveryConfig.artifacts.map { artifact ->
      // We calculate a bunch of data for each artifact, so pre-load the bulk of it here and pass that info into
      //  later functions to save redundant database calls.

      // Load all artifact version information
      val artifactVersions = repository.artifactVersions(artifact, limit)
      // Load the status of the versions in the environments
      val artifactVersionInfoInEnvironment: Map<String, List<StatusInfoForArtifactInEnvironment>> = deliveryConfig
        .environments
        .associate { env ->
          env.name to repository.getVersionInfoInEnvironment(deliveryConfig, env.name, artifact)
        }

      // Load the summary info for each version in each environment
      val artifactSummariesInEnv: Map<String, List<ArtifactSummaryInEnvironment>> = deliveryConfig
        .environments
        .associate { env->
          env.name to repository.getArtifactSummariesInEnvironment(
            deliveryConfig,
            env.name,
            artifact.reference,
            artifactVersions.map { it.version }
          )
        }

      // A verification context identifies an artifact version in an environment
      // For each context, there may be multiple verifications (e.g., test-container, canary)
      //
      // This map associates a context with this collection of verifications and their states
      val verificationStateMap = try {
        getVerificationStates(deliveryConfig, artifactVersions)
      } catch(e: Exception) {
        log.error("error getting verification states for application ${deliveryConfig.application}", e)
        emptyMap()
      }

      val artifactVersionSummaries = artifactVersions.map { artifactVersion ->
        // For each version of the artifact
        val artifactSummariesInEnvironments = mutableSetOf<ArtifactSummaryInEnvironment>()

        envSummaries.forEach { environmentSummary ->
          // For each environment
          val environment = deliveryConfig.environments.find { it.name == environmentSummary.name }!!
          val verifications = getVerifications(deliveryConfig, environment, artifactVersion, verificationStateMap)

          // Build the context needed for other functions to use
          val versionEnvironmentContext = ArtifactSummaryContext(
            deliveryConfig = deliveryConfig,
            environmentName = environment.name,
            artifact = artifact,
            verifications = verifications,
            allVersions = artifactVersions,
            artifactInfoInEnvironment = artifactVersionInfoInEnvironment[environment.name] ?: emptyList(),
            artifactSummariesInEnv = artifactSummariesInEnv[environment.name] ?: emptyList()
          )

          environmentSummary.getArtifactPromotionStatus(artifact, artifactVersion.version)
            ?.let { status ->
              if (artifact.isUsedIn(environment)) { // only add a summary if the artifact is used in the environment
                val artifactInEnvStartTime = now
                buildArtifactSummaryInEnvironment(
                  versionEnvironmentContext,
                  artifactVersion,
                  status
                )
                  ?.also {
                    artifactSummariesInEnvironments.add(
                      it.addConstraintSummaries(deliveryConfig, environment, artifactVersion.version, artifact)
                    )
                  }
                spectator.timer(
                  ARTIFACT_IN_ENV_SUMMARY_CONSTRUCT_DURATION,
                  listOf(BasicTag("application", application))
                ).record(Duration.between(artifactInEnvStartTime, now))
              }
            }
        }

        val versionStartTime = now
        val summary = buildArtifactVersionSummary(
          artifact,
          artifactVersion.version,
          artifactSummariesInEnvironments,
          artifactVersions
        )
        spectator.timer(
          ARTIFACT_VERSION_SUMMARY_CONSTRUCT_DURATION_ID,
          listOf(BasicTag("application", application))
        ).record(Duration.between(versionStartTime, now))
        summary
      }
      ArtifactSummary(
        name = artifact.name,
        type = artifact.type,
        reference = artifact.reference,
        versions = artifactVersionSummaries.toSet()
      )
    }
    spectator.timer(
      ARTIFACT_SUMMARY_CONSTRUCT_DURATION_ID,
      listOf(BasicTag("application", application))
    ).record(Duration.between(startTime, now))
    return artifactSummaries
  }

  private fun getVerificationStates(
    deliveryConfig: DeliveryConfig,
    artifactVersions: List<PublishedArtifact>
  ) =
    if (verificationsEnabled) {
      repository.getVerificationStates(deliveryConfig, artifactVersions)
    } else {
      emptyMap()
    }

  private fun getVerifications(
    deliveryConfig: DeliveryConfig,
    environment: Environment,
    artifactVersion: PublishedArtifact,
    verificationStateMap: Map<ArtifactInEnvironmentContext, Map<Verification, ActionState>>
  ) : List<VerificationSummary> =
    if(verificationsEnabled) {
      val verificationContext = ArtifactInEnvironmentContext(deliveryConfig, environment, artifactVersion)
      verificationStateMap[verificationContext]
        ?.map { (verification, state) -> VerificationSummary(verification, state) }
        ?: emptyList()
    } else {
      emptyList()
    }

  private fun buildArtifactSummaryInEnvironment(
    context: ArtifactSummaryContext,
    currentArtifact: PublishedArtifact,
    status: PromotionStatus,
  ): ArtifactSummaryInEnvironment? {
    // some environments contain relevant info for skipped artifacts, so
    // try and find that summary before defaulting to less information
    val potentialSummary = context.artifactSummariesInEnv.firstOrNull{ it.version == currentArtifact.version }
      ?.copy(verifications = context.verifications)
    val pinnedArtifact = getPinnedArtifact(context, currentArtifact.version)

    return when (status) {
      PENDING -> {
        val olderArtifactVersion = pinnedArtifact?: getArtifactVersionByPromotionStatus(context, CURRENT, null)
        ArtifactSummaryInEnvironment(
          environment = context.environmentName,
          version = currentArtifact.version,
          state = status.name.toLowerCase(),
          // comparing PENDING (version in question, new code) vs. CURRENT (old code)
          compareLink = artifactVersionLinks.generateCompareLink(currentArtifact, olderArtifactVersion, context.artifact)
        )
      }
      SKIPPED -> {
        if (potentialSummary == null || potentialSummary.state == "pending") {
          ArtifactSummaryInEnvironment(
            environment = context.environmentName,
            version = currentArtifact.version,
            state = status.name.toLowerCase()
          )
        } else {
          potentialSummary
        }
      }

      DEPLOYING, APPROVED -> {
        val olderArtifactVersion = pinnedArtifact?: getArtifactVersionByPromotionStatus(context, CURRENT, null)
        potentialSummary?.copy(
          // comparing DEPLOYING/APPROVED (version in question, new code) vs. CURRENT (old code)
          compareLink = artifactVersionLinks.generateCompareLink(currentArtifact, olderArtifactVersion, context.artifact)
        )
      }
      PREVIOUS -> {
        val newerArtifactVersion = potentialSummary?.replacedBy?.let { replacedByVersion -> context.allVersions.find { it.version == replacedByVersion }}
        potentialSummary?.copy(
          //comparing PREVIOUS (version in question, old code) vs. the version which replaced it (new code)
          //pinned artifact should not be consider here, as we know exactly which version replace the current one
          compareLink = artifactVersionLinks.generateCompareLink(currentArtifact, newerArtifactVersion, context.artifact)
        )
      }
      CURRENT -> {
        val olderArtifactVersion = pinnedArtifact?: getArtifactVersionByPromotionStatus(context, PREVIOUS, null)
        potentialSummary?.copy(
          // comparing CURRENT (version in question, new code) vs. PREVIOUS (old code)
          compareLink = artifactVersionLinks.generateCompareLink(currentArtifact, olderArtifactVersion, context.artifact)
        )
      }
      else -> potentialSummary
    }
  }

  private fun getArtifactVersionByPromotionStatus(
    context: ArtifactSummaryContext,
    promotionStatus: PromotionStatus,
    version: String?,
  ): PublishedArtifact? {
    //only CURRENT and PREVIOUS are supported, as they can be sorted by deploy_at
    require(promotionStatus in listOf(CURRENT, PREVIOUS)) { "Invalid promotion status used to query" }
    // we sort the versions by their deployed at time in descending order, then we take
    // the first one where all the conditions match.
    val chosenVersion = if (version == null) {
      context.artifactInfoInEnvironment.sortedByDescending { it.deployedAt }.firstOrNull { it.status == promotionStatus }
    } else {
      context.artifactInfoInEnvironment.sortedByDescending { it.deployedAt }.firstOrNull { it.status == promotionStatus && it.replacedByVersion == version}
    } ?: return null
    return context.allVersions.find { it.version == chosenVersion.version }
  }

  // Pinning is a special case when is coming to creating a compare link between versions.
  // If there is a pinned version, which is not the same as the current version, we need
  // to make sure we are creating the comparable link with reference to the pinned version.
  private fun getPinnedArtifact(
    context: ArtifactSummaryContext,
    version: String,
  ): PublishedArtifact? {
    // there can only be one pinned version
    val pinnedVersion = context.artifactSummariesInEnv.firstOrNull { it.pinned != null }?.version
    return if (pinnedVersion != version) {
      pinnedVersion?.let {
        context.allVersions.find { it.version == pinnedVersion }
      }
    } else { //if pinnedVersion == current version, fetch the version which the pinned version replaced
      val chosenVersion = context.artifactInfoInEnvironment.find { it.replacedByVersion == pinnedVersion && it.status == PREVIOUS }?.version
      context.allVersions.find { it.version == chosenVersion }
    }
  }

  /**
   * Adds details about any stateful constraints in the given environment to the [ArtifactSummaryInEnvironment].
   * Also, adds details about any stateless constraints that have their status saved into the database
   * (this happens when we approve an artifact version).
   *
   * For each stateful constraint type, if it's not yet been evaluated, creates a synthetic constraint summary object
   * with a [ConstraintStatus.NOT_EVALUATED] status.
   *
   * Also, adds details about any stateless constraints that haven't already been populated
   * in the given environment to the [ArtifactSummaryInEnvironment].
   */
  private fun ArtifactSummaryInEnvironment.addConstraintSummaries(
    deliveryConfig: DeliveryConfig,
    environment: Environment,
    version: String,
    artifact: DeliveryArtifact
  ): ArtifactSummaryInEnvironment {
    val persistedStates = repository
      .constraintStateFor(deliveryConfig.name, environment.name, version, artifact.reference)
      .removePrivateConstraintAttrs()
    val notEvaluatedPersistedConstraints = environment.constraints.filter { constraint ->
      constraint is StatefulConstraint && persistedStates.none { it.type == constraint.type }
    }.map { constraint ->
      ConstraintSummary(
        type = constraint.type,
        status = NOT_EVALUATED
      )
    }

    val statelessConstraintsStates: List<ConstraintSummary> = environment.constraints.filter { constraint ->
      // some/all stateless constraints might already have summary info, so filter those out.
      constraint !is StatefulConstraint && persistedStates.none { it.type == constraint.type }
    }.mapNotNull { constraint ->
      statelessEvaluators.find { evaluator ->
        evaluator.supportedType.name == constraint.type
      }?.let { evaluator ->
        val passes = evaluator.canPromote(artifact, version = version, deliveryConfig = deliveryConfig, targetEnvironment = environment)
        ConstraintSummary(
          type = constraint.type,
          status = if (passes) PASS else ConstraintStatus.PENDING,
          attributes = when (constraint) {
            is DependsOnConstraint -> DependsOnConstraintAttributes(constraint.environment, passes)
            else -> null
          }
        )
      }
    }

    return this.copy(
      constraints = persistedStates
        .map { it.toConstraintSummary() } +
        notEvaluatedPersistedConstraints +
        statelessConstraintsStates
    )
  }

  /**
   * Takes an artifact version, plus information about the type of artifact, and constructs a summary view.
   */
  private fun buildArtifactVersionSummary(
    artifact: DeliveryArtifact,
    version: String,
    environments: Set<ArtifactSummaryInEnvironment>,
    allVersions: List<PublishedArtifact>
  ): ArtifactVersionSummary {

    val artifactSupplier = artifactSuppliers.supporting(artifact.type)
    val artifactInstance = allVersions.find { it.version == version }
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
    ConstraintSummary(type, status, createdAt, judgedBy, judgedAt, comment, attributes)

  /**
   * Query the repository for all of the verification states associated with [versions]
   *
   * This just calls [KeelRepository.getVerificationStatesBatch] and reshapes the returned value to a map
   */
  fun KeelRepository.getVerificationStates(
    deliveryConfig: DeliveryConfig,
    versions: List<PublishedArtifact>
  ): Map<ArtifactInEnvironmentContext, Map<Verification, ActionState>> =
    deliveryConfig.contexts(versions).let { contexts: List<ArtifactInEnvironmentContext> ->
      contexts.zip(getVerificationStatesBatch(contexts))
        .associate { (ctx, vIdToState) -> ctx to vIdToState.toVerificationMap(deliveryConfig, ctx) }
    }

  /**
   * Convert a (verification id -> verification state) map to a (verification -> verification state) map
   *
   * Most of the logic in this method is to deal with the case where the verification id is invalid
   */
  fun Map<String, ActionState>.toVerificationMap(deliveryConfig: DeliveryConfig, ctx: ArtifactInEnvironmentContext) : Map<Verification, ActionState> =
    entries
      .mapNotNull { (vId: String, state: ActionState) ->
        ctx.verification(vId)
          ?.let { verification -> verification to state }
          .also { if (it == null) { onInvalidVerificationId(vId, deliveryConfig, ctx) } }
      }
      .toMap()

  /**
   * Actions to take when the verification state database table references a verification id that doesn't exist
   * in the delivery config
   */
  fun onInvalidVerificationId(vId: String, deliveryConfig: DeliveryConfig, ctx: ArtifactInEnvironmentContext) {
    publisher.publishEvent(
      InvalidVerificationIdSeen(
        vId,
        deliveryConfig.application,
        deliveryConfig.name,
        ctx.environmentName
      )
    )
    log.error("verification_state table contains invalid verification id: $vId  config: ${deliveryConfig.name} env: ${ctx.environmentName}. Valid ids in this env: ${ctx.environment.verifyWith.map { it.id }}")
  }

  fun retryArtifactVersionAction(application: String, environment: String, artifactReference: String, artifactVersion: String, actionType: ActionType, actionId: String, user: String): ConstraintStatus {
    ArtifactInEnvironmentContext(
      deliveryConfig = repository.getDeliveryConfigForApplication(application),
      environmentName = environment,
      artifactReference = artifactReference,
      version = artifactVersion
    ).run {
      val action = action(actionType, actionId) ?: throw InvalidActionId(actionId, this)
      repository.getActionState(
        context = this,
        action = action
      )?.run {
        if (!status.complete) throw ActionIncomplete()
      }
      return repository.resetActionState(context = this, action = action, user = user)
    }
  }

  @ResponseStatus(HttpStatus.CONFLICT)
  private class ActionIncomplete :
    IllegalStateException("Verifications may only be retried once complete.")

  @ResponseStatus(HttpStatus.NOT_FOUND)
  private class InvalidActionId(id: String, context: ArtifactInEnvironmentContext) :
    IllegalStateException("Unknown verification id: $id. Expecting one of: ${context.verifications.map { it.id }}")
}

fun List<ConstraintState>.removePrivateConstraintAttrs() =
  map { state ->
    if (state.attributes?.type in ApplicationService.privateConstraintAttrs) {
      state.copy(attributes = null)
    } else {
      state
    }
  }

/**
 * Container class for pre-loaded information, to be used by functions in this class
 * that generate UI summary information for artifacts by rearranging this data.
 */
data class ArtifactSummaryContext(
  val deliveryConfig: DeliveryConfig,
  val environmentName: String,
  val artifact: DeliveryArtifact,
  val verifications: List<VerificationSummary>,
  val allVersions: List<PublishedArtifact>,
  val artifactInfoInEnvironment: List<StatusInfoForArtifactInEnvironment>,
  val artifactSummariesInEnv: List<ArtifactSummaryInEnvironment>
)


/**
 * A verification context identifies an (environment, artifact version) pair.
 *
 * This takes a list of [PublishedArtifact] (artifact versions) and returns the corresponding contexts
 */
fun DeliveryConfig.contexts(
  versions: List<PublishedArtifact>
): List<ArtifactInEnvironmentContext> =
  versions.flatMap { version ->
    environments.map { env -> ArtifactInEnvironmentContext(this, env, version) }
  }

/**
 * Holds the info we need about artifacts in an environment for building the UI view.
 *
 * This is used in a list of versions pertaining to a specific delivery artifact.
 */
data class StatusInfoForArtifactInEnvironment(
  val version: String,
  val status: PromotionStatus,
  val replacedByVersion: String?,
  val deployedAt: Instant
)
