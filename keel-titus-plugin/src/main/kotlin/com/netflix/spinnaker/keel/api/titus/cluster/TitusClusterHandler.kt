/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.api.titus.cluster

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.INCREASING_TAG
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_SEMVER
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.api.titus.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.titus.TITUS_CLUSTER_V1
import com.netflix.spinnaker.keel.api.titus.exceptions.RegistryNotFoundException
import com.netflix.spinnaker.keel.api.titus.exceptions.TitusAccountConfigurationException
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ResourceNotFound
import com.netflix.spinnaker.keel.clouddriver.model.Constraints
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import com.netflix.spinnaker.keel.clouddriver.model.MigrationPolicy
import com.netflix.spinnaker.keel.clouddriver.model.Resources
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.core.api.Capacity
import com.netflix.spinnaker.keel.core.api.ClusterDependencies
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.api.RedBlack
import com.netflix.spinnaker.keel.core.orcaClusterMoniker
import com.netflix.spinnaker.keel.core.serverGroup
import com.netflix.spinnaker.keel.diff.toIndividualDiffs
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.events.ArtifactVersionDeploying
import com.netflix.spinnaker.keel.exceptions.DockerArtifactExportError
import com.netflix.spinnaker.keel.exceptions.ExportError
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.buildSpecFromDiff
import com.netflix.spinnaker.keel.retrofit.isNotFound
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import net.swiftzer.semver.SemVer
import org.springframework.context.ApplicationEventPublisher
import retrofit2.HttpException

class TitusClusterHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  private val clock: Clock,
  private val taskLauncher: TaskLauncher,
  private val publisher: ApplicationEventPublisher,
  resolvers: List<Resolver<*>>,
  private val clusterExportHelper: ClusterExportHelper
) : ResourceHandler<TitusClusterSpec, Map<String, TitusServerGroup>>(resolvers) {

  private val mapper = configuredObjectMapper()

  override val supportedKind = TITUS_CLUSTER_V1

  override suspend fun toResolvedType(resource: Resource<TitusClusterSpec>): Map<String, TitusServerGroup> =
    with(resource.spec) {
      resolve().byRegion()
    }

  override suspend fun current(resource: Resource<TitusClusterSpec>): Map<String, TitusServerGroup> =
    cloudDriverService
      .getServerGroups(resource)
      .byRegion()

  override suspend fun actuationInProgress(resource: Resource<TitusClusterSpec>): Boolean =
    resource
      .spec
      .locations
      .regions
      .map { it.name }
      .any { region ->
        orcaService
          .getCorrelatedExecutions("${resource.id}:$region", resource.serviceAccount)
          .isNotEmpty()
      }

  override suspend fun upsert(
    resource: Resource<TitusClusterSpec>,
    resourceDiff: ResourceDiff<Map<String, TitusServerGroup>>
  ): List<Task> =
    coroutineScope {
      resourceDiff
        .toIndividualDiffs()
        .filter { diff -> diff.hasChanges() }
        .map { diff ->
          val desired = diff.desired
          var tags: Set<String> = emptySet()

          var tagToUse: String? = null
          val version = when {
            diff.isCapacityOnly() -> null
            else -> {
              // calculate the version for the digest
              tags = getTagsForDigest(desired.container, desired.location.account)
              if (tags.size == 1) {
                tagToUse = tags.first() // only one tag, so use it to deploy
                tags.first()
              } else {
                log.debug("Container digest ${desired.container} has multiple tags: $tags")
                // unclear which "version" to print if there is more than one, so use a shortened version of the digest
                desired.container.digest.subSequence(0, 7)
              }
            }
          }

          val job = when {
            diff.isCapacityOnly() -> diff.resizeServerGroupJob()
            else -> diff.upsertServerGroupJob(tagToUse) + resource.spec.deployWith.toOrcaJobProperties()
          }

          val description = when (version) {
            null -> "Resizing server group ${desired.moniker} in ${desired.location.account}/${desired.location.region}"
            else -> "Upserting server group ${desired.moniker} to $version in ${desired.location.account}/${desired.location.region}"
          }
          log.info("Upserting server group using task: {}", job)

          val result = async {
            taskLauncher.submitJob(
              resource = resource,
              description = description,
              correlationId = "${resource.id}:${desired.location.region}",
              job = job
            )
          }

          tags.forEach { tag ->
            publisher.publishEvent(ArtifactVersionDeploying(
              resourceId = resource.id,
              artifactVersion = tag
            ))
          }
          return@map result
        }
        .map { it.await() }
    }

  override suspend fun export(exportable: Exportable): TitusClusterSpec {
    val serverGroups = cloudDriverService.getServerGroups(
      exportable.account,
      exportable.moniker,
      exportable.regions,
      exportable.user
    ).byRegion()

    if (serverGroups.isEmpty()) {
      throw ResourceNotFound("Could not find cluster: ${exportable.moniker} " +
        "in account: ${exportable.account} for export")
    }

    // let's assume that the largest server group is the most important and should be the base
    val base = serverGroups.values.maxBy { it.capacity.desired ?: it.capacity.max }
      ?: throw ExportError("Unable to calculate the server group with the largest capacity from server groups $serverGroups")

    val locations = SimpleLocations(
      account = exportable.account,
      regions = serverGroups.keys.map {
        SimpleRegionSpec(it)
      }.toSet()
    )

    val deployStrategy = clusterExportHelper.discoverDeploymentStrategy(
      cloudProvider = "titus",
      account = exportable.account,
      application = exportable.moniker.app,
      serverGroupName = base.name
    ) ?: RedBlack()

    val spec = TitusClusterSpec(
      moniker = exportable.moniker,
      locations = locations,
      _defaults = base.exportSpec(exportable.moniker.app),
      overrides = mutableMapOf(),
      containerProvider = ReferenceProvider(base.container.repository()),
      deployWith = deployStrategy.withDefaultsOmitted()
    )

    spec.generateOverrides(
      serverGroups
        .filter { it.value.location.region != base.location.region }
    )

    return spec
  }

  override suspend fun exportArtifact(exportable: Exportable): DeliveryArtifact {
    val serverGroups = cloudDriverService.getServerGroups(
      exportable.account,
      exportable.moniker,
      exportable.regions,
      exportable.user
    ).byRegion()

    if (serverGroups.isEmpty()) {
      throw ResourceNotFound("Could not find cluster: ${exportable.moniker} " +
        "in account: ${exportable.account} for export")
    }

    val container = serverGroups.values.maxBy { it.capacity.desired ?: it.capacity.max }?.container
      ?: throw ExportError("Unable to locate container from the largest server group: $serverGroups")

    val registry = getRegistryForTitusAccount(exportable.account)

    val images = cloudDriverService.findDockerImages(
      account = getRegistryForTitusAccount(exportable.account),
      repository = container.repository(),
      user = DEFAULT_SERVICE_ACCOUNT
    )

    val matchingImages = images.filter { it.digest == container.digest }
    if (matchingImages.isEmpty()) {
      throw ExportError("Unable to find matching image (searching by digest) in registry ($registry) for $container")
    }
    val versionStrategy = guessVersioningStrategy(matchingImages)
      ?: throw DockerArtifactExportError(matchingImages.map { it.tag }, container.toString())

    return DockerArtifact(
      name = container.repository(),
      tagVersionStrategy = versionStrategy
    )
  }

  /**
   * Tries to find a matching versioning strategy from a list of docker tags that correspond to the same digest.
   */
  fun guessVersioningStrategy(images: List<DockerImage>): TagVersionStrategy? {
    val versioningStrategies = mutableSetOf<TagVersionStrategy>()

    images.forEach { image ->
      val versionStrategy = deriveVersioningStrategy(image.tag)
      if (versionStrategy != null) {
        versioningStrategies.add(versionStrategy)
      }
    }
    return when (versioningStrategies.size) {
      1 -> versioningStrategies.first()
      0 -> null
      else -> {
        log.warn("Multiple versioning strategies apply for image, returning first")
        versioningStrategies.first()
      }
    }
  }

  fun deriveVersioningStrategy(tag: String): TagVersionStrategy? {
    if (tag.toIntOrNull() != null) {
      return INCREASING_TAG
    }
    if (Regex(SEMVER_JOB_COMMIT_BY_SEMVER.regex).find(tag) != null) {
      return SEMVER_JOB_COMMIT_BY_SEMVER
    }
    if (Regex(BRANCH_JOB_COMMIT_BY_JOB.regex).find(tag) != null) {
      return BRANCH_JOB_COMMIT_BY_JOB
    }
    if (isSemver(tag)) {
      return SEMVER_TAG
    }
    return null
  }

  private fun isSemver(input: String) =
    try {
      SemVer.parse(input.removePrefix("v"))
      true
    } catch (e: IllegalArgumentException) {
      false
    }

  private fun ResourceDiff<TitusServerGroup>.resizeServerGroupJob(): Map<String, Any?> {
    val current = requireNotNull(current) {
      "Current server group must not be null when generating a resize job"
    }
    return mapOf(
      "type" to "resizeServerGroup",
      "capacity" to mapOf(
        "min" to desired.capacity.min,
        "max" to desired.capacity.max,
        "desired" to desired.capacity.desired
      ),
      "cloudProvider" to CLOUD_PROVIDER,
      "credentials" to desired.location.account,
      "moniker" to current.moniker.orcaClusterMoniker,
      "region" to current.location.region,
      "serverGroupName" to current.name
    )
  }

  /**
   * If a tag is provided, deploys by tag.
   * Otherwise, deploys by digest.
   */
  private fun ResourceDiff<TitusServerGroup>.upsertServerGroupJob(tag: String?): Map<String, Any?> =
    with(desired) {
      val image = if (tag == null) {
        mapOf(
          "digest" to container.digest,
          "imageId" to "${container.organization}/${container.image}:${container.digest}"
        )
      } else {
        mapOf(
          "tag" to tag,
          "imageId" to "${container.organization}/${container.image}:$tag"
        )
      }

      mapOf(
        "application" to moniker.app,
        "credentials" to location.account,
        "region" to location.region,
        "network" to "default",
        "inService" to true,
        "capacity" to mapOf(
          "min" to capacity.min,
          "max" to capacity.max,
          "desired" to capacity.desired
        ),
        "targetHealthyDeployPercentage" to 100, // TODO: any reason to do otherwise?
        "iamProfile" to iamProfile,
        // <titus things>
        "capacityGroup" to capacityGroup,
        "entryPoint" to entryPoint,
        "env" to env,
        "containerAttributes" to containerAttributes,
        "constraints" to constraints,
        "registry" to runBlocking { getRegistryForTitusAccount(location.account) },
        "migrationPolicy" to migrationPolicy,
        "resources" to resources,
        // </titus things>
        "stack" to moniker.stack,
        "freeFormDetails" to moniker.detail,
        "tags" to tags,
        "moniker" to moniker.orcaClusterMoniker,
        "reason" to "Diff detected at ${clock.instant().iso()}",
        "type" to "createServerGroup",
        "cloudProvider" to CLOUD_PROVIDER,
        "securityGroups" to securityGroupIds(),
        "loadBalancers" to dependencies.loadBalancerNames,
        "targetGroups" to dependencies.targetGroups,
        "account" to location.account
      ) + image
    }
      .let { job ->
        current?.run {
          job + mapOf(
            "source" to mapOf(
              "account" to location.account,
              "region" to location.region,
              "asgName" to moniker.serverGroup
            )
          )
        } ?: job
      }

  /**
   * @return `true` if the only changes in the diff are to capacity.
   */
  private fun ResourceDiff<TitusServerGroup>.isCapacityOnly(): Boolean =
    current != null && affectedRootPropertyTypes.all { it == Capacity::class.java }

  private fun TitusClusterSpec.generateOverrides(serverGroups: Map<String, TitusServerGroup>) =
    serverGroups.forEach { (region, serverGroup) ->
      val workingSpec = serverGroup.exportSpec(moniker.app)
      val override: TitusServerGroupSpec? = buildSpecFromDiff(defaults, workingSpec)
      if (override != null) {
        (overrides as MutableMap)[region] = override
      }
    }

  private suspend fun CloudDriverService.getServerGroups(resource: Resource<TitusClusterSpec>): Iterable<TitusServerGroup> =
    getServerGroups(resource.spec.locations.account,
      resource.spec.moniker,
      resource.spec.locations.regions.map { it.name }.toSet(),
      resource.serviceAccount
    )
      .also { them ->
        val sameContainer: Boolean = them.distinctBy { it.container.digest }.size == 1
        val healthy: Boolean = them.all { it.instanceCounts?.isHealthy() == true }
        if (sameContainer && healthy) {
          // only publish a successfully deployed event if the server group is healthy
          val container = them.first().container
          getTagsForDigest(container, resource.spec.locations.account)
            .forEach { tag ->
              // We publish an event for each tag that matches the digest
              // so that we handle the tags like `latest` where more than one tags have the same digest
              // and we don't care about some of them.
              publisher.publishEvent(ArtifactVersionDeployed(
                resourceId = resource.id,
                artifactVersion = tag
              ))
            }
        }
      }

  /**
   * Get all tags that match a digest, filtering out the "latest" tag.
   * Note: there may be more than one tag with the same digest
   */
  private suspend fun getTagsForDigest(container: DigestProvider, titusAccount: String): Set<String> =
    cloudDriverService.findDockerImages(
      account = getRegistryForTitusAccount(titusAccount),
      repository = container.repository(),
      user = DEFAULT_SERVICE_ACCOUNT
    )
      .filter { it.digest == container.digest && it.tag != "latest" }
      .map { it.tag }
      .toSet()

  private suspend fun CloudDriverService.getServerGroups(
    account: String,
    moniker: Moniker,
    regions: Set<String>,
    serviceAccount: String
  ): Iterable<TitusServerGroup> =
    coroutineScope {
      regions.map {
        async {
          try {
            titusActiveServerGroup(
              serviceAccount,
              moniker.app,
              account,
              moniker.toString(),
              it,
              CLOUD_PROVIDER
            )
              .toTitusServerGroup()
          } catch (e: HttpException) {
            if (!e.isNotFound) {
              throw e
            }
            null
          }
        }
      }
        .mapNotNull { it.await() }
    }

  private fun TitusActiveServerGroup.toTitusServerGroup() =
    TitusServerGroup(
      name = name,
      location = Location(
        account = placement.account,
        region = region
      ),
      capacity = capacity,
      container = DigestProvider(
        organization = image.dockerImageName.split("/").first(),
        image = image.dockerImageName.split("/").last(),
        digest = image.dockerImageDigest
      ),
      entryPoint = entryPoint,
      resources = resources,
      env = env,
      containerAttributes = containerAttributes,
      constraints = constraints,
      iamProfile = iamProfile.substringAfterLast("/"),
      capacityGroup = capacityGroup,
      migrationPolicy = migrationPolicy,
      dependencies = ClusterDependencies(
        loadBalancers,
        securityGroupNames = securityGroupNames,
        targetGroups = targetGroups
      ),
      instanceCounts = instanceCounts
    )

  private suspend fun getAwsAccountNameForTitusAccount(titusAccount: String): String =
    cloudDriverService.getAccountInformation(titusAccount, DEFAULT_SERVICE_ACCOUNT)["awsAccount"]?.toString()
      ?: throw TitusAccountConfigurationException(titusAccount, "awsAccount")

  private suspend fun getRegistryForTitusAccount(titusAccount: String): String =
    cloudDriverService.getAccountInformation(titusAccount, DEFAULT_SERVICE_ACCOUNT)["registry"]?.toString()
      ?: throw RegistryNotFoundException(titusAccount)

  fun TitusServerGroup.securityGroupIds(): Collection<String> =
    runBlocking {
      val awsAccount = getAwsAccountNameForTitusAccount(location.account)
      dependencies
        .securityGroupNames
        // no need to specify these as Orca will auto-assign them, also the application security group
        // gets auto-created so may not exist yet
        .filter { it !in setOf("nf-infrastructure", "nf-datacenter", moniker.app) }
        .map { cloudDriverCache.securityGroupByName(awsAccount, location.region, it).id }
    }

  private val TitusActiveServerGroup.securityGroupNames: Set<String>
    get() = securityGroups.map {
      cloudDriverCache.securityGroupById(awsAccount, region, it).name
    }
      .toSet()

  private fun Instant.iso() =
    atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_DATE_TIME)

  private fun TitusServerGroup.exportSpec(application: String): TitusServerGroupSpec {
    val defaults = TitusServerGroupSpec(
      capacity = Capacity(1, 1, 1),
      iamProfile = application + "InstanceProfile",
      resources = mapper.convertValue(Resources()),
      entryPoint = "",
      constraints = Constraints(),
      migrationPolicy = MigrationPolicy(),
      dependencies = ClusterDependencies(),
      capacityGroup = application,
      env = emptyMap(),
      containerAttributes = emptyMap(),
      tags = emptyMap()
    )

    val thisSpec = TitusServerGroupSpec(
      capacity = capacity,
      capacityGroup = capacityGroup,
      constraints = constraints,
      container = ReferenceProvider(container.repository()),
      dependencies = dependencies,
      entryPoint = entryPoint,
      env = env,
      containerAttributes = containerAttributes,
      iamProfile = iamProfile,
      migrationPolicy = migrationPolicy,
      resources = resources.toSpec(),
      tags = tags
    )

    return checkNotNull(buildSpecFromDiff(defaults, thisSpec))
  }

  private fun Resources.toSpec(): ResourcesSpec =
    ResourcesSpec(
      cpu = cpu,
      disk = disk,
      gpu = gpu,
      memory = memory,
      networkMbps = networkMbps
    )
}
