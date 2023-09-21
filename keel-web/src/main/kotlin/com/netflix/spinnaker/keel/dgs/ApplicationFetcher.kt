package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.actuation.ExecutionSummaryService
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.events.EventLevel.ERROR
import com.netflix.spinnaker.keel.events.EventLevel.WARNING
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
import com.netflix.spinnaker.keel.graphql.types.MdGitMetadata
import com.netflix.spinnaker.keel.graphql.types.MdLifecycleStep
import com.netflix.spinnaker.keel.graphql.types.MdNotification
import com.netflix.spinnaker.keel.graphql.types.MdPackageDiff
import com.netflix.spinnaker.keel.graphql.types.MdPausedInfo
import com.netflix.spinnaker.keel.graphql.types.MdPinnedVersion
import com.netflix.spinnaker.keel.graphql.types.MdPullRequest
import com.netflix.spinnaker.keel.graphql.types.MdVersionVeto
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoDeliveryConfigForApplication
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.scm.ScmUtils
import com.netflix.spinnaker.keel.services.ResourceStatusService
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader
import org.springframework.security.access.prepost.PreAuthorize
import java.util.concurrent.CompletableFuture

/**
 * Fetches details about an application, as defined in [schema.graphql]
 *
 * Loads the static data from a delivery config.
 * Adds supplemental data using functions to fetch more information.
 */
@DgsComponent
class ApplicationFetcher(
  private val authorizationSupport: AuthorizationSupport,
  private val keelRepository: KeelRepository,
  private val actuationPauser: ActuationPauser,
  private val artifactVersionLinks: ArtifactVersionLinks,
  private val applicationFetcherSupport: ApplicationFetcherSupport,
  private val notificationRepository: DismissibleNotificationRepository,
  private val scmUtils: ScmUtils,
) {

  @DgsData.List(
    DgsData(parentType = DgsConstants.QUERY.TYPE_NAME, field = DgsConstants.QUERY.Application),
    DgsData(parentType = DgsConstants.QUERY.TYPE_NAME, field = DgsConstants.QUERY.Md_application)
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #appName)""")
  fun application(dfe: DataFetchingEnvironment, @InputArgument("appName") appName: String): MdApplication {
    val config = try {
      keelRepository.getDeliveryConfigForApplication(appName)
    } catch (ex: NoDeliveryConfigForApplication) {
      throw DgsEntityNotFoundException(ex.message!!)
    }
    val context: ApplicationContext = DgsContext.getCustomContext(dfe)
    context.deliveryConfig = config
    return MdApplication(
      id = config.application,
      name = config.application,
      account = config.serviceAccount,
      environments = emptyList()
    )
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDAPPLICATION.TYPE_NAME, field = DgsConstants.MDAPPLICATION.Environments),
    DgsData(parentType = DgsConstants.MD_APPLICATION.TYPE_NAME, field = DgsConstants.MD_APPLICATION.Environments)
  )
  fun environments(dfe: DgsDataFetchingEnvironment): List<DataFetcherResult<MdEnvironment>> {
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    return config.environments.sortedWith { env1, env2 ->
      when {
        env1.dependsOn(env2) -> -1
        env2.dependsOn(env1) -> 1
        env1.hasDependencies() && !env2.hasDependencies() -> -1
        env2.hasDependencies() && !env1.hasDependencies() -> 1
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
      DataFetcherResult.newResult<MdEnvironment>().data(
        MdEnvironment(
          id = env.name,
          name = env.name,
          isPreview = env.isPreview,
          basedOn = env.basedOn,
          state = MdEnvironmentState(
            id = "${env.name}-state",
            artifacts = artifacts,
            resources = env.resources.map { it.toDgs(config, env.name) }
          ),
        )
      ).localContext(env).build()
    }
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDENVIRONMENT.TYPE_NAME, field = DgsConstants.MDENVIRONMENT.GitMetadata),
    DgsData(parentType = DgsConstants.MD_ENVIRONMENT.TYPE_NAME, field = DgsConstants.MD_ENVIRONMENT.GitMetadata)
  )
  fun environmentGitMetadata(dfe: DgsDataFetchingEnvironment): MdGitMetadata? {
    val env: Environment = dfe.getLocalContext()
    return if (env.isPreview) {
      MdGitMetadata(
        repoName = env.repoKey,
        branch = env.branch,
        pullRequest = MdPullRequest(
          number = env.pullRequestId,
          link = scmUtils.getPullRequestLink(
            env.repoType,
            env.projectKey,
            env.repoSlug,
            env.pullRequestId
          )
        )
      )
    } else {
      null
    }
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDAPPLICATION.TYPE_NAME, field = DgsConstants.MDAPPLICATION.IsPaused),
    DgsData(parentType = DgsConstants.MD_APPLICATION.TYPE_NAME, field = DgsConstants.MD_APPLICATION.IsPaused),
  )
  fun isPaused(dfe: DgsDataFetchingEnvironment): Boolean {
    val app: MdApplication = dfe.getSource()
    return actuationPauser.applicationIsPaused(app.name)
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDAPPLICATION.TYPE_NAME, field = DgsConstants.MDAPPLICATION.PausedInfo),
    DgsData(parentType = DgsConstants.MD_APPLICATION.TYPE_NAME, field = DgsConstants.MD_APPLICATION.PausedInfo),
  )
  fun pausedInfo(dfe: DgsDataFetchingEnvironment): MdPausedInfo? {
    val app: MdApplication = dfe.getSource()
    return actuationPauser.getApplicationPauseInfo(app.name)?.toDgsPaused()
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDARTIFACT.TYPE_NAME, field = DgsConstants.MDARTIFACT.Versions),
    DgsData(parentType = DgsConstants.MD_ARTIFACT.TYPE_NAME, field = DgsConstants.MD_ARTIFACT.Versions),
  )
  fun versions(
    dfe: DataFetchingEnvironment,
    @InputArgument("statuses", collectionType = MdArtifactStatusInEnvironment::class) statuses: List<MdArtifactStatusInEnvironment>?,
    @InputArgument("versions") versionIds: List<String>?,
    @InputArgument("limit") limit: Int?
  ): CompletableFuture<List<DataFetcherResult<MdArtifactVersionInEnvironment>>>? {
    val dataLoader: DataLoader<ArtifactAndEnvironment, List<MdArtifactVersionInEnvironment>> = dfe.getDataLoader(ArtifactInEnvironmentDataLoader.Descriptor.name)
    val artifact: MdArtifact = dfe.getSource()
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    val applicationContext: ApplicationContext = DgsContext.getCustomContext(dfe)
    if (statuses != null && applicationContext.requestedStatuses == null) {
      applicationContext.requestedStatuses = statuses.toSet()
    }
    if (versionIds != null && applicationContext.requestedVersionIds == null) {
      applicationContext.requestedVersionIds = versionIds.toSet()
    }
    if (limit != null && applicationContext.requestedLimit == null) {
      applicationContext.requestedLimit = limit
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

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDGITMETADATA.TYPE_NAME, field = DgsConstants.MDGITMETADATA.ComparisonLinks),
    DgsData(parentType = DgsConstants.MD_GITMETADATA.TYPE_NAME, field = DgsConstants.MD_GITMETADATA.ComparisonLinks),
  )
  fun comparisonLinks(dfe: DataFetchingEnvironment): MdComparisonLinks? {
    val diffContext = applicationFetcherSupport.getDiffContext(dfe)

    with(diffContext) {
      return MdComparisonLinks(
        toPreviousVersion = if (previousDeployedVersion != fetchedVersion) {
          artifactVersionLinks.generateCompareLink(fetchedVersion.publishedArtifact, previousDeployedVersion?.publishedArtifact, deliveryArtifact)
        } else {
          null
        },
        toCurrentVersion = if (currentDeployedVersion != fetchedVersion) {
          artifactVersionLinks.generateCompareLink(fetchedVersion.publishedArtifact, currentDeployedVersion?.publishedArtifact, deliveryArtifact)
        } else {
          null
        }
      )
    }
  }

  @DgsData.List(
    DgsData(
      parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME,
      field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.PackageDiff
    ),
    DgsData(
      parentType = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.TYPE_NAME,
      field = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.PackageDiff
    ),
  )
  fun packageDiff(dfe: DataFetchingEnvironment): MdPackageDiff? {
    return applicationFetcherSupport.getDebianPackageDiff(dfe)
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.LifecycleSteps),
    DgsData(parentType = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.LifecycleSteps),
  )
  fun lifecycleSteps(dfe: DataFetchingEnvironment): CompletableFuture<List<MdLifecycleStep>>? {
    val dataLoader: DataLoader<ArtifactAndVersion, List<MdLifecycleStep>> = dfe.getDataLoader(LifecycleEventsByVersionDataLoader.Descriptor.name)
    val artifact: MdArtifactVersionInEnvironment = dfe.getSource()
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    val deliveryArtifact = config.matchingArtifactByReference(artifact.reference) ?: return null
    return dataLoader.load(
      ArtifactAndVersion(
        deliveryArtifact,
        artifact.version
      )
    )
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDARTIFACT.TYPE_NAME, field = DgsConstants.MDARTIFACT.PinnedVersion),
    DgsData(parentType = DgsConstants.MD_ARTIFACT.TYPE_NAME, field = DgsConstants.MD_ARTIFACT.PinnedVersion),
  )
  fun pinnedVersion(dfe: DataFetchingEnvironment): CompletableFuture<MdPinnedVersion>? {
    val dataLoader: DataLoader<PinnedArtifactAndEnvironment, MdPinnedVersion> = dfe.getDataLoader(PinnedVersionInEnvironmentDataLoader.Descriptor.name)
    val artifact: MdArtifact = dfe.getSource()
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    val deliveryArtifact = config.matchingArtifactByReference(artifact.reference) ?: return null
    return dataLoader.load(PinnedArtifactAndEnvironment(
      artifact = deliveryArtifact,
      environment = artifact.environment
    ))
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDARTIFACT.TYPE_NAME, field = DgsConstants.MDARTIFACT.LatestApprovedVersion),
    DgsData(parentType = DgsConstants.MD_ARTIFACT.TYPE_NAME, field = DgsConstants.MD_ARTIFACT.LatestApprovedVersion),
  )
  fun latestApprovedVersion(dfe: DataFetchingEnvironment): MdArtifactVersionInEnvironment? {
    val artifact: MdArtifact = dfe.getSource()
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    val deliveryArtifact = config.matchingArtifactByReference(artifact.reference) ?: return null

    //[gyardeni + rhorev] please note - some values (like MdComparisonLinks) will not be retrieved for MdArtifactVersionInEnvironment
    //due to our current dgs model.
    keelRepository.getLatestApprovedInEnvArtifactVersion(config, deliveryArtifact, artifact.environment)
      ?.let {
        return it.toDgs(artifact.environment)
      }

    return null
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.Constraints),
    DgsData(parentType = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.Constraints),
  )
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

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.Verifications),
    DgsData(parentType = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.Verifications),
  )
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

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.PostDeploy),
    DgsData(parentType = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.PostDeploy),
  )
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

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MDARTIFACTVERSIONINENVIRONMENT.Veto),
    DgsData(parentType = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.MD_ARTIFACTVERSIONINENVIRONMENT.Veto),
  )
  fun versionVetoed(dfe: DataFetchingEnvironment): CompletableFuture<MdVersionVeto?>? {
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    val dataLoader: DataLoader<EnvironmentArtifactAndVersion, MdVersionVeto?> = dfe.getDataLoader(VetoedDataLoader.Descriptor.name)
    val artifact: MdArtifactVersionInEnvironment = dfe.getSource()
    return artifact.environment?.let { environmentName ->
      dataLoader.load(
        EnvironmentArtifactAndVersion(
          environmentName,
          artifact.reference,
          artifact.version,
        )
      )
    }
  }

  /**
   * Fetches the list of dismissible notifications for the application in context.
   */
  @DgsData.List(
    DgsData(parentType = DgsConstants.MDAPPLICATION.TYPE_NAME, field = DgsConstants.MDAPPLICATION.Notifications),
    DgsData(parentType = DgsConstants.MD_APPLICATION.TYPE_NAME, field = DgsConstants.MD_APPLICATION.Notifications),
  )
  fun applicationNotifications(dfe: DataFetchingEnvironment): List<MdNotification>? {
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    return notificationRepository.notificationHistory(config.application, true, setOf(WARNING, ERROR))
      .map { it.toDgs() }
  }

  @DgsData.List(
    DgsData(parentType = DgsConstants.MDENVIRONMENT.TYPE_NAME, field = DgsConstants.MDENVIRONMENT.IsDeleting),
    DgsData(parentType = DgsConstants.MD_ENVIRONMENT.TYPE_NAME, field = DgsConstants.MD_ENVIRONMENT.IsDeleting),
  )
  fun environmentIsDeleting(dfe: DataFetchingEnvironment): CompletableFuture<Boolean>? {
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    val dataLoader: DataLoader<Environment, Boolean> = dfe.getDataLoader(EnvironmentDeletionStatusLoader.NAME)
    val environment = dfe.getSource<MdEnvironment>().let { mdEnv ->
      config.environments.find { it.name == mdEnv.name }
    }
    return environment?.let { dataLoader.load(environment) }
  }
}

fun Environment.dependsOn(another: Environment) =
  constraints.any { it is DependsOnConstraint && it.environment == another.name }

fun Environment.hasDependencies() =
  constraints.any { it is DependsOnConstraint }
