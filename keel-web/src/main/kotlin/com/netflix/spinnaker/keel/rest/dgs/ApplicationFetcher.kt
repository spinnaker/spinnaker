package com.netflix.spinnaker.keel.rest.dgs


import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.bakery.BakeryMetadataService
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.PublishedArtifactInEnvironment
import com.netflix.spinnaker.keel.graphql.DgsConstants
import com.netflix.spinnaker.keel.graphql.types.MdAction
import com.netflix.spinnaker.keel.graphql.types.MdApplication
import com.netflix.spinnaker.keel.graphql.types.MdArtifact
import com.netflix.spinnaker.keel.graphql.types.MdArtifactStatusInEnvironment
import com.netflix.spinnaker.keel.graphql.types.MdArtifactVersionInEnvironment
import com.netflix.spinnaker.keel.graphql.types.MdComparisonLinks
import com.netflix.spinnaker.keel.graphql.types.MdConstraint
import com.netflix.spinnaker.keel.graphql.types.MdEnvironment
import com.netflix.spinnaker.keel.graphql.types.MdEnvironmentState
import com.netflix.spinnaker.keel.graphql.types.MdLifecycleStep
import com.netflix.spinnaker.keel.graphql.types.MdPackageDiff
import com.netflix.spinnaker.keel.graphql.types.MdPinnedVersion
import com.netflix.spinnaker.keel.graphql.types.MdResource
import com.netflix.spinnaker.keel.graphql.types.MdResourceActuationState
import com.netflix.spinnaker.keel.graphql.types.MdResourceActuationStatus
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoDeliveryConfigForApplication
import com.netflix.spinnaker.keel.services.ResourceStatusService
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.runBlocking
import org.dataloader.DataLoader
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * Fetches details about an application, as defined in [schema.graphql]
 *
 * Loads the static data from a delivery config.
 * Adds supplemental data using functions to fetch more information.
 */
@DgsComponent
class ApplicationFetcher(
  private val keelRepository: KeelRepository,
  private val resourceStatusService: ResourceStatusService,
  private val actuationPauser: ActuationPauser,
  private val cloudDriverService: CloudDriverService,
  private val bakeryMetadataService: BakeryMetadataService?,
  private val artifactVersionLinks: ArtifactVersionLinks,
) {

  companion object {
    private val log by lazy { LoggerFactory.getLogger(ApplicationFetcher::class.java) }

    private fun getDeliveryConfigFromContext(dfe: DataFetchingEnvironment): DeliveryConfig {
      val context: ApplicationContext = DgsContext.getCustomContext(dfe)
      return context.getConfig()
    }
  }

  @DgsData(parentType = DgsConstants.QUERY.TYPE_NAME, field = DgsConstants.QUERY.Application)
  fun application(dfe: DataFetchingEnvironment, @InputArgument("appName") appName: String): MdApplication {
    val config = try {
      keelRepository.getDeliveryConfigForApplication(appName)
    } catch (ex: NoDeliveryConfigForApplication) {
      throw DgsEntityNotFoundException(ex.message!!)
    }
    val context: ApplicationContext = DgsContext.getCustomContext(dfe)
    context.deliveryConfig = config
    val environments: List<MdEnvironment> = config.environments.sortedWith { env1, env2 ->
      when {
        env1.dependsOn(env2) -> 1
        env2.dependsOn(env1) -> -1
        env1.hasDependencies() && !env2.hasDependencies() -> 1
        env2.hasDependencies() && !env1.hasDependencies() -> -1
        else -> 0
      }
    }.map { env ->
      val artifacts = config.artifactsUsedIn(env.name).map { artifact ->
        MdArtifact(
          id = "${env.name}-${artifact.reference}",
          environment = env.name,
          name = artifact.name,
          reference = artifact.reference,
          type = artifact.type
        )
      }

      MdEnvironment(
        id = env.name,
        name = env.name,
        state = MdEnvironmentState(
          id = "${env.name}-state",
          artifacts = artifacts,
          resources = env.resources.map { it.toDgs(config, env.name) }
        )
      )
    }

    return MdApplication(
      id = config.application,
      name = config.application,
      account = config.serviceAccount,
      environments = environments
    )
  }

  @DgsData(parentType = DgsConstants.MDAPPLICATION.TYPE_NAME, field = DgsConstants.MDAPPLICATION.IsPaused)
  fun isPaused(dfe: DgsDataFetchingEnvironment): Boolean {
    val app: MdApplication = dfe.getSource()
    return actuationPauser.applicationIsPaused(app.name)
  }

  @DgsData(parentType = DgsConstants.MDRESOURCE.TYPE_NAME, field = DgsConstants.MDRESOURCE.State)
  fun resourceStatus(dfe: DgsDataFetchingEnvironment): MdResourceActuationState {
    val resource: MdResource = dfe.getSource()
    val state = resourceStatusService.getActuationState(resource.id)
    return MdResourceActuationState(
      status = MdResourceActuationStatus.valueOf(state.status.name),
      reason = state.reason,
      event = state.eventMessage
    )
  }

  @DgsData(parentType = DgsConstants.MDARTIFACT.TYPE_NAME, field = DgsConstants.MDARTIFACT.Versions)
  fun versions(
    dfe: DataFetchingEnvironment,
    @InputArgument("statuses", collectionType = MdArtifactStatusInEnvironment::class) statuses: List<MdArtifactStatusInEnvironment>?,
    @InputArgument("versions") versionIds: List<String>?
  ): CompletableFuture<List<DataFetcherResult<MdArtifactVersionInEnvironment>>>? {
    val dataLoader: DataLoader<ArtifactAndEnvironment, List<MdArtifactVersionInEnvironment>> = dfe.getDataLoader(ArtifactInEnvironmentDataLoader.Descriptor.name)
    val artifact: MdArtifact = dfe.getSource()
    val config = getDeliveryConfigFromContext(dfe)
    val applicationContext: ApplicationContext = DgsContext.getCustomContext(dfe)
    if (statuses != null && applicationContext.requestedStatuses == null) {
      applicationContext.requestedStatuses = statuses.toSet()
    }
    if (versionIds != null && applicationContext.requestedVersionIds == null) {
      applicationContext.requestedVersionIds = versionIds.toSet()
    }

    val deliveryArtifact = config.matchingArtifactByReference(artifact.reference) ?: return null

    return dataLoader.load(
      ArtifactAndEnvironment(
        artifact = deliveryArtifact,
        environmentName = artifact.environment,
      )
    ).thenApply { versions ->
      versions.map { version ->
        DataFetcherResult.newResult<MdArtifactVersionInEnvironment>().data(version).localContext(version).build()
      }
    }
  }

  @DgsData(parentType = DgsConstants.MDGITMETADATA.TYPE_NAME, field = DgsConstants.MDGITMETADATA.ComparisonLinks)
  fun comparisonLinks(dfe: DataFetchingEnvironment): MdComparisonLinks? {
    val mdArtifactVersion: MdArtifactVersionInEnvironment = dfe.getLocalContext()
    val (deliveryArtifact, artifactVersions) = getPublishedArtifactsInEnvironment(dfe, mdArtifactVersion)
    val thisVersion = artifactVersions.firstOrNull { it.publishedArtifact.version == mdArtifactVersion.version }
      ?: throw DgsEntityNotFoundException("artifact ${mdArtifactVersion.reference} has no version named ${mdArtifactVersion.version}")
    val currentVersion = artifactVersions.firstOrNull { it.status == PromotionStatus.CURRENT }
    val previousVersion = artifactVersions.firstOrNull { it.replacedBy == mdArtifactVersion.version }

    return MdComparisonLinks(
      toPreviousVersion = if (previousVersion != thisVersion) {
        artifactVersionLinks.generateCompareLink(thisVersion.publishedArtifact, previousVersion?.publishedArtifact, deliveryArtifact)
      } else {
        null
      },
      toCurrentVersion = if (currentVersion != thisVersion) {
        artifactVersionLinks.generateCompareLink(thisVersion.publishedArtifact, currentVersion?.publishedArtifact, deliveryArtifact)
      } else {
        null
      }
    )
  }

  @DgsData(
    parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME,
    field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.PackageDiff
  )
  fun packageDiff(dfe: DataFetchingEnvironment): MdPackageDiff? {
    if (bakeryMetadataService == null) {
      return null
    }

    val mdArtifactVersion: MdArtifactVersionInEnvironment = dfe.getLocalContext()
    val (deliveryArtifact, artifactVersions) = getPublishedArtifactsInEnvironment(dfe, mdArtifactVersion)

    if (deliveryArtifact.type != DEBIAN) {
      return null
    }

    val thisVersion = artifactVersions.firstOrNull { it.publishedArtifact.version == mdArtifactVersion.version }
      ?: throw DgsEntityNotFoundException("artifact ${mdArtifactVersion.reference} has no version named ${mdArtifactVersion.version}")

    val previousVersion = artifactVersions.firstOrNull { it.replacedBy == mdArtifactVersion.version }

    val currentImage = runBlocking {
      cloudDriverService.namedImages(
        user = DEFAULT_SERVICE_ACCOUNT,
        imageName = thisVersion.publishedArtifact.normalizedVersion,
        account = null
      ).firstOrNull()
    } ?: throw DgsEntityNotFoundException("Image for version ${thisVersion.publishedArtifact.version} not found")

    val previewsImage = if (previousVersion != null) {
      runBlocking {
        cloudDriverService.namedImages(
          user = DEFAULT_SERVICE_ACCOUNT,
          imageName = previousVersion.publishedArtifact.normalizedVersion,
          account = null
        ).firstOrNull()
      } ?: throw DgsEntityNotFoundException("Image for version ${previousVersion.publishedArtifact.version} not found")
    } else {
      null
    }

    val region = artifactResources(dfe)
      ?.firstOrNull()?.location?.regions?.firstOrNull()
      ?: return null
        .also { log.warn("Unable to determine region for $deliveryArtifact in environment ${mdArtifactVersion.environment}") }

    val diff = runBlocking {
      bakeryMetadataService.getPackageDiff(
        oldImage = previewsImage?.normalizedImageName,
        newImage = currentImage.normalizedImageName,
        region = region // TODO: figure out a way to get a region
      )
    }.toDgs()

    return diff
  }

  private fun getPublishedArtifactsInEnvironment(
    dfe: DataFetchingEnvironment,
    mdArtifactVersion: MdArtifactVersionInEnvironment
  ): Pair<DeliveryArtifact, List<PublishedArtifactInEnvironment>> {
    val deliveryConfig = getDeliveryConfigFromContext(dfe)
    val applicationContext: ApplicationContext = DgsContext.getCustomContext(dfe) // the artifact versions store context
    val deliveryArtifact = deliveryConfig.matchingArtifactByReference(mdArtifactVersion.reference)
      ?: throw DgsEntityNotFoundException("Artifact ${mdArtifactVersion.reference} was not found in the delivery config") // the delivery artifact of this artifact
    val artifactVersions = mdArtifactVersion.environment?.let { applicationContext.getArtifactVersions(deliveryArtifact = deliveryArtifact, environmentName = it) }
      ?: throw DgsEntityNotFoundException("Environment ${mdArtifactVersion.environment} has not versions for artifact ${mdArtifactVersion.reference}")
    return deliveryArtifact to artifactVersions
  }

  @DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.LifecycleSteps)
  fun lifecycleSteps(dfe: DataFetchingEnvironment): CompletableFuture<List<MdLifecycleStep>>? {
    val dataLoader: DataLoader<ArtifactAndVersion, List<MdLifecycleStep>> = dfe.getDataLoader(LifecycleEventsByVersionDataLoader.Descriptor.name)
    val artifact: MdArtifactVersionInEnvironment = dfe.getSource()
    val config = getDeliveryConfigFromContext(dfe)
    val deliveryArtifact = config.matchingArtifactByReference(artifact.reference) ?: return null
    return dataLoader.load(
      ArtifactAndVersion(
        deliveryArtifact,
        artifact.version
      )
    )
  }

  @DgsData(parentType = DgsConstants.MDARTIFACT.TYPE_NAME, field = DgsConstants.MDARTIFACT.PinnedVersion)
  fun pinnedVersion(dfe: DataFetchingEnvironment): CompletableFuture<MdPinnedVersion>? {
    val dataLoader: DataLoader<PinnedArtifactAndEnvironment, MdPinnedVersion> = dfe.getDataLoader(PinnedVersionInEnvironmentDataLoader.Descriptor.name)
    val artifact: MdArtifact = dfe.getSource()
    val config = getDeliveryConfigFromContext(dfe)
    val deliveryArtifact = config.matchingArtifactByReference(artifact.reference) ?: return null
    return dataLoader.load(PinnedArtifactAndEnvironment(
      artifact = deliveryArtifact,
      environment = artifact.environment
    ))
  }

  @DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.Constraints)
  fun artifactConstraints(dfe: DataFetchingEnvironment): CompletableFuture<List<MdConstraint>>? {
    val dataLoader: DataLoader<EnvironmentArtifactAndVersion, List<MdConstraint>> = dfe.getDataLoader(ConstraintsDataLoader.Descriptor.name)
    val artifact: MdArtifactVersionInEnvironment = dfe.getSource()
    return artifact.environment?.let { environmentName ->
      dataLoader.load(
        EnvironmentArtifactAndVersion(
          environmentName,
          artifact.reference,
          artifact.version
        )
      )
    }
  }

  @DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.Verifications)
  fun artifactVerifications(dfe: DataFetchingEnvironment): CompletableFuture<List<MdAction>>? {
    val dataLoader: DataLoader<EnvironmentArtifactAndVersion, List<MdAction>> = dfe.getDataLoader(ActionsDataLoader.Descriptor.name)
    val artifact: MdArtifactVersionInEnvironment = dfe.getSource()
    return artifact.environment?.let { environmentName ->
      dataLoader.load(
        EnvironmentArtifactAndVersion(
          environmentName,
          artifact.reference,
          artifact.version,
          ActionType.VERIFICATION
        )
      )
    }
  }

  @DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.PostDeploy)
  fun artifactPostDeploy(dfe: DataFetchingEnvironment): CompletableFuture<List<MdAction>>? {
    val dataLoader: DataLoader<EnvironmentArtifactAndVersion, List<MdAction>> = dfe.getDataLoader(ActionsDataLoader.Descriptor.name)
    val artifact: MdArtifactVersionInEnvironment = dfe.getSource()
    return artifact.environment?.let { environmentName ->
      dataLoader.load(
        EnvironmentArtifactAndVersion(
          environmentName,
          artifact.reference,
          artifact.version,
          ActionType.POST_DEPLOY
        )
      )
    }
  }

  @DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.Resources)
  fun artifactResources(dfe: DataFetchingEnvironment): List<MdResource>? {
    val artifact: MdArtifactVersionInEnvironment = dfe.getSource()
    val config = getDeliveryConfigFromContext(dfe)
    return artifact.environment?.let {
      config.resourcesUsing(artifact.reference, artifact.environment).map { it.toDgs(config, artifact.environment) }
    }
  }

//  add function for putting the resources on the artifactVersion
}

fun Environment.dependsOn(another: Environment) =
  constraints.any { it is DependsOnConstraint && it.environment == another.name }

fun Environment.hasDependencies() =
  constraints.any { it is DependsOnConstraint }
