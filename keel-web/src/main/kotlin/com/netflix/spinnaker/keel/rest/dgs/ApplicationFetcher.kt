package com.netflix.spinnaker.keel.rest.dgs


import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.InputArgument
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.graphql.DgsConstants
import com.netflix.spinnaker.keel.graphql.types.DgsApplication
import com.netflix.spinnaker.keel.graphql.types.DgsArtifact
import com.netflix.spinnaker.keel.graphql.types.DgsArtifactStatusInEnvironment
import com.netflix.spinnaker.keel.graphql.types.DgsArtifactVersionInEnvironment
import com.netflix.spinnaker.keel.graphql.types.DgsCommitInfo
import com.netflix.spinnaker.keel.graphql.types.DgsConstraint
import com.netflix.spinnaker.keel.graphql.types.DgsEnvironment
import com.netflix.spinnaker.keel.graphql.types.DgsEnvironmentState
import com.netflix.spinnaker.keel.graphql.types.DgsGitMetadata
import com.netflix.spinnaker.keel.graphql.types.DgsLifecycleStep
import com.netflix.spinnaker.keel.graphql.types.DgsLocation
import com.netflix.spinnaker.keel.graphql.types.DgsPullRequest
import com.netflix.spinnaker.keel.graphql.types.DgsResource
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.services.ResourceStatusService
import com.netflix.spinnaker.kork.exceptions.SystemException
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader
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
) {

  @DgsData(parentType = DgsConstants.QUERY.TYPE_NAME, field = DgsConstants.QUERY.Application)
  fun application(@InputArgument("appName") appName: String): DataFetcherResult<DgsApplication> {
    val config = keelRepository.getDeliveryConfigForApplication(appName)
    val environments: List<DgsEnvironment> = config.environments.map { env ->
      val artifacts = config.artifactsUsedIn(env.name).map { artifact ->
        DgsArtifact(
          environment = env.name,
          name = artifact.name,
          reference = artifact.reference,
          type = artifact.type
        )
      }

      val resources = env.resources.map { resource ->
        DgsResource(
          id = resource.id,
          kind = resource.kind.toString(), // todo: different serialization?
          artifact = resource.findAssociatedArtifact(config)?.let { artifact ->
            DgsArtifact(
              environment = env.name,
              name = artifact.name,
              type = artifact.type,
              reference = artifact.reference
            )
          },
          displayName = resource.spec.displayName,
          location = if (resource.spec is Locatable<*>) {
            DgsLocation(regions = (resource.spec as Locatable<*>).locations.regions.map { it.toString() })
          } else {
            null
          }
        )
      }

      DgsEnvironment(
        name = env.name,
        state = DgsEnvironmentState(
          version = "-1", // todo: replace once exists
          artifacts = artifacts,
          constraints = env.constraints.map { constraint ->
            DgsConstraint(
              type = constraint.type
            )
          },
          resources = resources
        )
      )
    }

    return DataFetcherResult.newResult<DgsApplication>().data(
      DgsApplication(
        name = config.name,
        account = config.serviceAccount,
        environments = environments
      )
    ).localContext(config).build()
  }

  @DgsData(parentType = DgsConstants.DGSRESOURCE.TYPE_NAME, field = DgsConstants.DGSRESOURCE.Status)
  fun resourceStatus(dfe: DgsDataFetchingEnvironment): String {
    val resource: DgsResource = dfe.getSource()
    return resourceStatusService.getStatus(resource.id).toString()
  }

  @DgsData(parentType = DgsConstants.DGSARTIFACT.TYPE_NAME, field = DgsConstants.DGSARTIFACT.Versions)
  fun versions(
    dfe: DataFetchingEnvironment,
    @InputArgument("statuses", collectionType = DgsArtifactStatusInEnvironment::class) statuses: List<DgsArtifactStatusInEnvironment>?
  ): CompletableFuture<List<DgsArtifactVersionInEnvironment>>? {
    val dataLoader: DataLoader<ArtifactAndEnvironment, List<DgsArtifactVersionInEnvironment>> = dfe.getDataLoader("artifact-in-environment")
    val artifact: DgsArtifact = dfe.getSource()
    val config: DeliveryConfig = dfe.getLocalContext()
    val deliveryArtifact = config.matchingArtifactByReference(artifact.reference) ?: return null

    return dataLoader.load(
      ArtifactAndEnvironment(
        artifact = deliveryArtifact,
        deliveryConfig = config,
        environmentName = artifact.environment,
        statuses = statuses ?: emptyList(),
      )
    )
  }

  @DgsData(parentType = DgsConstants.DGSARTIFACTVERSIONINENVIRONMENT.TYPE_NAME, field = DgsConstants.DGSARTIFACTVERSIONINENVIRONMENT.LifecycleSteps)
  fun lifecycleSteps(dfe: DataFetchingEnvironment): CompletableFuture<List<DgsLifecycleStep>>? {
    val dataLoader: DataLoader<ArtifactAndVersion, List<DgsLifecycleStep>> = dfe.getDataLoader("artifact-lifecycle-events-version")
    val artifact: DgsArtifactVersionInEnvironment = dfe.getSource()
    val version = artifact.version ?: return null
    val config: DeliveryConfig = dfe.getLocalContext()
    val deliveryArtifact = config.matchingArtifactByReference(artifact.reference) ?: return null
    return dataLoader.load(
      ArtifactAndVersion(
        deliveryArtifact,
        version
      )
    )
  }

//  add function for putting the resources on the artifactVersion
}

fun GitMetadata.toDgs(): DgsGitMetadata =
  DgsGitMetadata(
    commit = commit,
    author = author,
    project = project,
    branch = branch,
    repoName = repo?.name,
    pullRequest = if (pullRequest != null) {
      DgsPullRequest(
        number = pullRequest?.number,
        link = pullRequest?.url
      )
    } else null,
    commitInfo = if (commitInfo != null) {
      DgsCommitInfo(
        sha = commitInfo?.sha,
        link = commitInfo?.link,
        message = commitInfo?.message
      )
    } else null
  )
