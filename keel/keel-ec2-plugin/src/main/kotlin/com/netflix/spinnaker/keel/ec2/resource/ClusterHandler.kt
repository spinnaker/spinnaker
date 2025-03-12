package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.rocket.api.artifact.internal.debian.DebianArtifactParser
import com.netflix.spinnaker.keel.actuation.RolloutLocation
import com.netflix.spinnaker.keel.actuation.RolloutTarget
import com.netflix.spinnaker.keel.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.NoStrategy
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.actuation.Job
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.UNKNOWN
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Capacity.AutoScalingCapacity
import com.netflix.spinnaker.keel.api.ec2.Capacity.DefaultCapacity
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.CapacitySpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.HealthSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.CustomizedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.DEFAULT_AUTOSCALE_INSTANCE_WARMUP
import com.netflix.spinnaker.keel.api.ec2.EC2_CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.Location
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.MetricDimension
import com.netflix.spinnaker.keel.api.ec2.PredefinedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess.AddToLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess.Launch
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess.Terminate
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.ActiveServerGroupImage
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.Health
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.StepAdjustment
import com.netflix.spinnaker.keel.api.ec2.StepScalingPolicy
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.ec2.byRegion
import com.netflix.spinnaker.keel.api.ec2.hasScalingPolicies
import com.netflix.spinnaker.keel.api.ec2.resolve
import com.netflix.spinnaker.keel.api.ec2.resolveCapacity
import com.netflix.spinnaker.keel.api.ec2.resolveDependencies
import com.netflix.spinnaker.keel.api.ec2.resolveHealth
import com.netflix.spinnaker.keel.api.ec2.resolveScaling
import com.netflix.spinnaker.keel.api.ec2.resolveTags
import com.netflix.spinnaker.keel.api.plugins.BaseClusterHandler
import com.netflix.spinnaker.keel.api.plugins.CurrentImages
import com.netflix.spinnaker.keel.api.plugins.ImageInRegion
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.support.Tag
import com.netflix.spinnaker.keel.api.withDefaultsOmitted
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ResourceNotFound
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.CustomizedMetricSpecificationModel
import com.netflix.spinnaker.keel.clouddriver.model.MetricDimensionModel
import com.netflix.spinnaker.keel.clouddriver.model.PredefinedMetricSpecificationModel
import com.netflix.spinnaker.keel.clouddriver.model.ScalingPolicy
import com.netflix.spinnaker.keel.clouddriver.model.StepAdjustmentModel
import com.netflix.spinnaker.keel.clouddriver.model.SuspendedProcess
import com.netflix.spinnaker.keel.clouddriver.model.subnet
import com.netflix.spinnaker.keel.clouddriver.model.toActive
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.orcaClusterMoniker
import com.netflix.spinnaker.keel.core.parseMoniker
import com.netflix.spinnaker.keel.core.serverGroup
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.ec2.MissingAppVersionException
import com.netflix.spinnaker.keel.ec2.toEc2Api
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.exceptions.ActiveServerGroupsException
import com.netflix.spinnaker.keel.exceptions.ExportError
import com.netflix.spinnaker.keel.filterNotNullValues
import com.netflix.spinnaker.keel.igor.artifact.ArtifactService
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.toOrcaJobProperties
import com.netflix.spinnaker.keel.parseAppVersion
import com.netflix.spinnaker.keel.parseAppVersionOrNull
import com.netflix.spinnaker.keel.plugin.buildSpecFromDiff
import com.netflix.spinnaker.keel.retrofit.isNotFound
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroup as ClouddriverServerGroup

/**
 * [ResourceHandler] implementation for EC2 clusters, represented by [ClusterSpec].
 */
class ClusterHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  override val taskLauncher: TaskLauncher,
  private val clock: Clock,
  override val eventPublisher: EventPublisher,
  resolvers: List<Resolver<*>>,
  private val clusterExportHelper: ClusterExportHelper,
  private val blockDeviceConfig: BlockDeviceConfig,
  private val artifactService: ArtifactService,
) : BaseClusterHandler<ClusterSpec, ServerGroup>(resolvers, taskLauncher) {

  private val debianArtifactParser = DebianArtifactParser()

  private val mapper = configuredObjectMapper()

  override val supportedKind = EC2_CLUSTER_V1_1

  override val cloudProvider = "aws"

  override suspend fun toResolvedType(resource: Resource<ClusterSpec>): Map<String, ServerGroup> =
    with(resource.spec) {
      resolve().byRegion()
    }

  override suspend fun current(resource: Resource<ClusterSpec>): Map<String, ServerGroup> =
    cloudDriverService
      .getActiveServerGroups(resource)
      .byRegion()

  override fun getDesiredRegion(diff: ResourceDiff<ServerGroup>): String =
    diff.desired.location.region

  override fun getUnhealthyRegionsForActiveServerGroup(resource: Resource<ClusterSpec>): List<String> {
    val unhealthyRegions = mutableListOf<String>()
    val activeServerGroups = runBlocking {
      cloudDriverService.getActiveServerGroups(resource)
    }

    activeServerGroups.forEach { serverGroup ->
      val healthy = isHealthy(serverGroup, resource)
      if (!healthy) {
        unhealthyRegions.add(serverGroup.location.region)
      }
    }

    return unhealthyRegions
  }

  fun isHealthy(serverGroup: ServerGroup, resource: Resource<ClusterSpec>): Boolean =
    serverGroup.instanceCounts?.isHealthy(
      resource.spec.deployWith.health,
      resource.spec.resolveCapacity(serverGroup.location.region)
    ) == true

  override suspend fun getImage(resource: Resource<ClusterSpec>): CurrentImages =
    current(resource)
      .map { (region, serverGroup) ->
        val account = serverGroup.location.account
        val images = cloudDriverService.getImage(account = account, region = region, amiId = serverGroup.launchConfiguration.imageId)
        if (images.size > 1) {
          log.error("More than one image with ami id ${serverGroup.launchConfiguration.imageId}, using first...")
        }
        ImageInRegion(region, images.first().imageName , serverGroup.location.account)
      }
      .let { images ->
        CurrentImages(supportedKind.kind, images, resource.id)
      }

  override fun ResourceDiff<ServerGroup>.moniker(): Moniker =
    desired.moniker

  override fun ServerGroup.moniker(): Moniker =
    moniker

  override fun Resource<ClusterSpec>.isStaggeredDeploy(): Boolean =
    spec.deployWith.isStaggered

  override fun Resource<ClusterSpec>.isManagedRollout(): Boolean =
    spec.managedRollout.enabled

  override fun Resource<ClusterSpec>.regions(): List<String> =
    spec.locations.regions.map { it.name }

  override fun Resource<ClusterSpec>.moniker(): Moniker =
    spec.moniker

  override fun getDesiredAccount(diff: ResourceDiff<ServerGroup>): String =
    diff.desired.location.account

  override fun Resource<ClusterSpec>.account(): String =
    spec.locations.account

  override fun correlationId(resource: Resource<ClusterSpec>, diff: ResourceDiff<ServerGroup>): String =
    "${resource.id}:${diff.desired.location.region}"

  override fun ResourceDiff<ServerGroup>.version(resource: Resource<ClusterSpec>): String =
    desired.launchConfiguration.appVersion ?: throw MissingAppVersionException(resource.id)

  override fun Resource<ClusterSpec>.getDeployWith(): ClusterDeployStrategy =
    spec.deployWith

  override suspend fun export(exportable: Exportable): ClusterSpec {
    // Get existing infrastructure
    val serverGroups = cloudDriverService.getActiveServerGroups(
      account = exportable.account,
      moniker = exportable.moniker,
      regions = exportable.regions,
      serviceAccount = exportable.user
    )
      .byRegion()

    if (serverGroups.isEmpty()) {
      throw ResourceNotFound(
        "Could not find cluster: ${exportable.moniker} " +
          "in account: ${exportable.account} for export"
      )
    }

    val zonesByRegion = serverGroups.map { (region, serverGroup) ->
      region to cloudDriverCache.availabilityZonesBy(
        account = exportable.account,
        vpcId = cloudDriverCache.subnetBy(exportable.account, region, serverGroup.location.subnet).vpcId,
        purpose = serverGroup.location.subnet,
        region = region
      )
    }
      .toMap()

    val subnetAwareRegionSpecs = serverGroups.map { (region, serverGroup) ->
      SubnetAwareRegionSpec(
        name = region,
        availabilityZones =
        if (!serverGroup.location.availabilityZones.containsAll(
            zonesByRegion[region]
              ?: error("Failed resolving availabilityZones for account: ${exportable.account}, region: $region")
          )
        ) {
          serverGroup.location.availabilityZones
        } else {
          emptySet()
        }
      )
    }
      .toSet()

    // let's assume that the largest server group is the most important and should be the base
    val base = serverGroups.values.maxByOrNull { it.capacity.desired ?: it.capacity.max }
      ?: throw ExportError("Unable to calculate the server group with the largest capacity from server groups $serverGroups")

    // Construct the minimal locations object
    val locations = SubnetAwareLocations(
      account = exportable.account,
      subnet = base.location.subnet,
      vpc = base.location.vpc,
      regions = subnetAwareRegionSpecs
    ).withDefaultsOmitted()

    val deployStrategy = clusterExportHelper.discoverDeploymentStrategy(
      cloudProvider = "aws",
      account = exportable.account,
      application = exportable.moniker.app,
      serverGroupName = base.name
    ) ?: RedBlack()

    // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
    val appversion = base.image?.appVersion?.parseAppVersionOrNull()?.packageName

    val spec = ClusterSpec(
      moniker = exportable.moniker,
      artifactReference = appversion,
      locations = locations,
      deployWith = deployStrategy.withDefaultsOmitted(),
      _defaults = base.exportSpec(exportable.account, exportable.moniker.app),
      overrides = mutableMapOf()
    )

    spec.generateOverrides(
      exportable.account,
      exportable.moniker.app,
      serverGroups
        .filter { it.value.location.region != base.location.region }
    )

    return spec
  }

  override suspend fun exportArtifact(exportable: Exportable): DeliveryArtifact {
    val serverGroups = cloudDriverService.getActiveServerGroups(
      account = exportable.account,
      moniker = exportable.moniker,
      regions = exportable.regions,
      serviceAccount = exportable.user
    )
      .byRegion()

    if (serverGroups.isEmpty()) {
      throw ResourceNotFound(
        "Could not find cluster: ${exportable.moniker} " +
          "in account: ${exportable.account} for export"
      )
    }

    val base = serverGroups.values.maxByOrNull { it.capacity.desired ?: it.capacity.max }
      ?: throw ExportError("Unable to determine largest server group: $serverGroups")

    if (base.image == null) {
      throw ExportError("Server group ${base.name} doesn't have image information - unable to correctly export artifact.")
    }

    // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
    val appVersion = checkNotNull(base.launchConfiguration.appVersion).parseAppVersion()
    val artifactName = appVersion.packageName
    val artifactVersion = base.launchConfiguration.appVersion!!.removePrefix("${artifactName}-")
    val artifact = try {
      artifactService.getArtifact(artifactName, artifactVersion, DEBIAN)
    } catch (e: HttpException) {
      if (e.isNotFound) null else throw e
    }

    // prefer branch-based artifact spec, fallback to release statuses
    if (artifact?.branch != null) {
      log.debug("Found branch name '{}' for artifact {}:{} from metadata. Returning source-driven artifact.",
        artifact.branch, artifactName, artifactVersion)

      return DebianArtifact(
        name = artifactName,
        vmOptions = VirtualMachineOptions(regions = serverGroups.keys, baseOs = guessBaseOsFrom(base.image)),
        branch = artifact.branch
      )
    } else {
      // fall back to exporting the artifact in the old format if we can't retrieve metadata
      log.debug("Unable to retrieve branch name for artifact {}:{} from metadata. Returning legacy artifact.",
        appVersion.packageName, appVersion.version)

      val status = debianArtifactParser.parseStatus(base.launchConfiguration.appVersion?.substringAfter("$artifactName-"))
      if (status == UNKNOWN) {
        throw ExportError("Unable to determine release status from appVersion ${base.launchConfiguration.appVersion}, you'll have to configure this artifact manually.")
      }

      return DebianArtifact(
        name = artifactName,
        vmOptions = VirtualMachineOptions(regions = serverGroups.keys, baseOs = guessBaseOsFrom(base.image)),
        statuses = setOf(status)
      )
    }
  }

  /**
   * This function attempts to use image description to guess what OS the image is.
   * If not found, it will throw an error.
   */
  private fun guessBaseOsFrom(image: ActiveServerGroupImage?): String =
    parseBaseOsFrom(image?.description)
      ?: throw ExportError("Unable to determine the base image from image description: $image")

  /**
   * Parses the base os from the start of the ancestor name of a description like:
   * "name=fiat, arch=x86_64, ancestor_name=bionic-classicbase-x86_64-202005072313-ebs,
   *   ancestor_id=ami-1111, ancestor_version=nflx-base-5.540.0-h1708.eeeeeee"
   */
  private fun parseBaseOsFrom(description: String?): String? {
    if (description == null) {
      return null
    }
    val info: List<String> = description.split(",").map { it.trim() }
    val ancestor = info.firstOrNull { it.startsWith("ancestor_name") } ?: return null
    val ancestorValue = ancestor.split("=")[1]
    return ancestorValue.substringBefore("base-x")
  }

  override suspend fun actuationInProgress(resource: Resource<ClusterSpec>) =
    generateCorrelationIds(resource).any { correlationId ->
        orcaService
          .getCorrelatedExecutions(correlationId)
          .isNotEmpty()
      }

  private fun ClusterSpec.generateOverrides(account: String, application: String, serverGroups: Map<String, ServerGroup>) =
    serverGroups.forEach { (region, serverGroup) ->
      val workingSpec = serverGroup.exportSpec(account, application)
      val override: ServerGroupSpec? = buildSpecFromDiff(
        defaults,
        workingSpec,
        OVERRIDABLE_SERVER_GROUP_PROPERTIES
      )
      if (override != null) {
        (overrides as MutableMap)[region] = override
      }
    }

  /**
   * @return `true` if the only changes in the diff are to capacity.
   */
  override fun ResourceDiff<ServerGroup>.isCapacityOnly(): Boolean =
    current != null && affectedRootPropertyTypes.all {
      it == Capacity::class.java || it == DefaultCapacity::class.java
    }

  override fun ResourceDiff<ServerGroup>.isSuspendPropertiesAndCapacityOnly() =
    affectedRootPropertyTypes.all {
      it == Scaling::class.java || it == Capacity::class.java || it == DefaultCapacity::class.java
    }
      && sameSuspendedProcesses(current?.scaling, desired.scaling)

  /**
   * Clusters have different scaling config when disabled.
   * This function checks if the scaling is the same minus that expected difference.
   */
  private fun sameSuspendedProcesses(
    current: Scaling?,
    desired: Scaling
  ): Boolean {
    if (current == null){
      return false
    }

    val disabledProcesses = setOf(Launch, AddToLoadBalancer, Terminate)
    val currentMinusDisabled = current.copy(suspendedProcesses = current.suspendedProcesses - disabledProcesses)

    return !DefaultResourceDiff(desired, currentMinusDisabled).hasChanges()
  }

  /**
   * @return `true` if the only changes in the diff are to scaling.
   */
  override fun ResourceDiff<ServerGroup>.isAutoScalingOnly(): Boolean =
    current != null &&
      affectedRootPropertyTypes.any { it == Scaling::class.java } &&
      affectedRootPropertyTypes.all { it == Capacity::class.java || it == Scaling::class.java } &&
      current!!.scaling.suspendedProcesses == desired.scaling.suspendedProcesses &&
      current!!.capacity.min == desired.capacity.min &&
      current!!.capacity.max == desired.capacity.max

  /**
   * @return true if the only difference is in the onlyEnabledServerGroup property
   */
  override fun ResourceDiff<ServerGroup>.isEnabledOnly(): Boolean =
    current != null &&
      affectedRootPropertyNames.all { it == "onlyEnabledServerGroup" } &&
      current!!.onlyEnabledServerGroup != desired.onlyEnabledServerGroup

  override fun ResourceDiff<ServerGroup>.hasScalingPolicies(): Boolean =
    desired.scaling.hasScalingPolicies()


  /**
   * @return `true` if [current] exists and the diff impacts scaling policies or capacity only.
   */
  override fun ResourceDiff<ServerGroup>.isCapacityOrAutoScalingOnly(): Boolean =
    current != null &&
      affectedRootPropertyTypes.all { it == Capacity::class.java || it == Scaling::class.java } &&
      current!!.scaling.suspendedProcesses == desired.scaling.suspendedProcesses

  /**
   * @return `true` if [current] exists and the diff includes a scaling policy change.
   */
  override fun ResourceDiff<ServerGroup>.hasScalingPolicyDiff(): Boolean =
    current != null && affectedRootPropertyTypes.contains(Scaling::class.java) &&
      (
        current!!.scaling.targetTrackingPolicies != desired.scaling.targetTrackingPolicies ||
          current!!.scaling.stepScalingPolicies != desired.scaling.stepScalingPolicies
        )

  override suspend fun getServerGroupsByRegion(resource: Resource<ClusterSpec>): Map<String, List<ServerGroup>> =
    getExistingServerGroupsByRegion(resource)
      .mapValues { regionalList ->
        regionalList.value.map { serverGroup: ClouddriverServerGroup ->
          serverGroup.toActive(resource.spec.locations.account).toServerGroup()
        }
      }

  override fun ResourceDiff<ServerGroup>.disableOtherServerGroupJob(resource: Resource<ClusterSpec>, desiredVersion: String): Job {
    val current = requireNotNull(current) {
      "Current server group must not be null when generating a disable job"
    }
    val existingServerGroups: Map<String, List<ClouddriverServerGroup>> = runBlocking { getExistingServerGroupsByRegion(resource) }
    val sgInRegion = existingServerGroups.getOrDefault(current.location.region, emptyList()).filterNot { it.disabled }

    if (sgInRegion.size < 2) {
      log.error("Diff says this is not the only active server group, but now we say otherwise. " +
        "What is going on? Existing server groups: {}", existingServerGroups)
      throw ActiveServerGroupsException(resource.id, "No other active server group found to disable.")
    }

    val (rightImageASGs, wrongImageASGs) = sgInRegion
      .sortedBy { it.createdTime }
      .partition { it.image.appVersion == desiredVersion }

    val sgToDisable = when {
      wrongImageASGs.isNotEmpty() -> {
        log.debug("Disabling oldest server group with incorrect app version for {}", resource.id)
        wrongImageASGs.first()
      }
      rightImageASGs.size > 1 -> {
        log.debug("Disabling oldest server group with correct app version " +
          "(because there is more than one active server group with the correct image) for {}", resource.id)
        rightImageASGs.first()
      }
      else -> {
        log.error("Could not find a server group to disable, looking at: {}", wrongImageASGs + rightImageASGs)
        throw ActiveServerGroupsException(resource.id, "No other active server group found to disable.")
      }
    }
    log.debug("Disabling server group {} for {}: {}", sgToDisable.name, resource.id, sgToDisable)

    return mapOf(
      "type" to "disableServerGroup",
      "cloudProvider" to EC2_CLOUD_PROVIDER,
      "credentials" to desired.location.account,
      "moniker" to sgToDisable.moniker.orcaClusterMoniker,
      "region" to sgToDisable.region,
      "serverGroupName" to sgToDisable.name,
      "asgName" to sgToDisable.name
    )
  }

  override fun ResourceDiff<ServerGroup>.upsertServerGroupJob(resource: Resource<ClusterSpec>, startingRefId: Int, version: String?): Job =
    createServerGroupJobBase(startingRefId) + resource.spec.deployWith.toOrcaJobProperties("Amazon") +
      mapOf("metadata" to mapOf("resource" to resource.id))

  private fun ResourceDiff<ServerGroup>.createServerGroupJobBase(startingRefId: Int = 0): Job =
    with(desired) {
      mutableMapOf(
        "application" to moniker.app,
        "credentials" to location.account,
        "refId" to (startingRefId + 1).toString(),
        "requisiteStageRefIds" to when (startingRefId) {
          0 -> emptyList()
          else -> listOf(startingRefId.toString())
        },
        "capacity" to mapOf(
          "min" to capacity.min,
          "max" to capacity.max,
          "desired" to resolveDesiredCapacity()
        ),
        "targetHealthyDeployPercentage" to 100, // TODO: any reason to do otherwise?
        "cooldown" to health.cooldown.seconds,
        "enabledMetrics" to health.enabledMetrics,
        "healthCheckType" to health.healthCheckType.name,
        "healthCheckGracePeriod" to health.warmup.seconds,
        "instanceMonitoring" to launchConfiguration.instanceMonitoring,
        "ebsOptimized" to launchConfiguration.ebsOptimized,
        "iamRole" to launchConfiguration.iamRole,
        "terminationPolicies" to health.terminationPolicies.map(TerminationPolicy::name),
        "subnetType" to location.subnet,
        "availabilityZones" to mapOf(
          location.region to location.availabilityZones
        ),
        "keyPair" to launchConfiguration.keyPair,
        "suspendedProcesses" to scaling.suspendedProcesses,
        "securityGroups" to securityGroupIds,
        "stack" to moniker.stack,
        "freeFormDetails" to moniker.detail,
        "tags" to tags,
        "useAmiBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
        "copySourceCustomBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
        "virtualizationType" to "hvm", // TODO: any reason to do otherwise?
        "moniker" to moniker.orcaClusterMoniker,
        "amiName" to launchConfiguration.imageId,
        "reason" to "Diff detected at ${clock.instant().iso()}",
        "instanceType" to launchConfiguration.instanceType,
        "type" to "createServerGroup",
        "cloudProvider" to EC2_CLOUD_PROVIDER,
        "loadBalancers" to dependencies.loadBalancerNames,
        "targetGroups" to dependencies.targetGroups,
        "account" to location.account,
        "requireIMDSv2" to launchConfiguration.requireIMDSv2
      )
    }
      .also { job ->
        current?.apply {
          job["source"] = mapOf(
            "account" to location.account,
            "region" to location.region,
            "asgName" to moniker.serverGroup
          )
          job["copySourceCustomBlockDeviceMappings"] = true
          }

        // pass block device info so that keel can specify the volume type
        blockDeviceConfig.getBlockDevicesFor(
          desired.location.account,
          desired.moniker.app,
          desired.launchConfiguration.instanceType
        )?.let { blockDevices ->
          job["blockDevices"] = blockDevices.map {
            mapOf(
              "deviceName" to it.deviceName,
              "size" to it.size,
              "volumeType" to it.volumeType,
              "deleteOnTermination" to it.deleteOnTermination
            )
          }
        }
      }

  override fun Resource<ClusterSpec>.upsertServerGroupManagedRolloutJob(
    diffs: List<ResourceDiff<ServerGroup>>,
    version: String?
  ): Job =
    mapOf(
      "refId" to "1",
      "type" to "managedRollout",
      "input" to mapOf(
        "selectionStrategy" to spec.managedRollout?.selectionStrategy,
        "targets" to spec.generateRolloutTargets(diffs),
        "clusterDefinitions" to listOf(toManagedRolloutClusterDefinition(diffs))
      ),
      "reason" to "Diff detected at ${clock.instant().iso()}",
    )

  // todo eb: individual server group deploy strategy?
  // todo eb: scaling policies?
  private fun Resource<ClusterSpec>.toManagedRolloutClusterDefinition(diffs: List<ResourceDiff<ServerGroup>>) =
    with(spec) {
      val dependencies = resolveDependencies()

      mutableMapOf(
        "application" to moniker.app,
        "stack" to moniker.stack,
        "freeFormDetails" to moniker.detail,
        "loadBalancers" to dependencies.loadBalancerNames,
        "targetGroups" to dependencies.targetGroups,
        "securityGroups" to dependencies.securityGroupNames,
        "targetHealthyDeployPercentage" to 100, // TODO: any reason to do otherwise?
        "tags" to resolveTags(),
        "useAmiBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
        "copySourceCustomBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
        "virtualizationType" to "hvm", // TODO: any reason to do otherwise?
        "moniker" to moniker.orcaClusterMoniker,
        "suspendedProcesses" to resolveScaling().suspendedProcesses,
        "reason" to "Diff detected at ${clock.instant().iso()}",
        "cloudProvider" to EC2_CLOUD_PROVIDER,
        "account" to locations.account,
        "subnetType" to diffs.map { it.desired.location.subnet }.first(), //todo eb: why? yes?
        "capacity" to resolveCapacity(),
      ) + resolveHealth().toMapForOrca() +
        mapOf("overrides" to buildOverrides(diffs)) +
        spec.deployWith.toOrcaJobProperties("Amazon")
    }

  override fun ResourceDiff<ServerGroup>.rollbackServerGroupJob(
    resource: Resource<ClusterSpec>,
    rollbackServerGroup: ServerGroup
  ): Job =
    mutableMapOf(
      "rollbackType" to "EXPLICIT",
      "rollbackContext" to  mapOf(
        "rollbackServerGroupName" to current?.moniker?.serverGroup,
        "restoreServerGroupName" to rollbackServerGroup.moniker.serverGroup,
        "targetHealthyRollbackPercentage" to 100,
        "delayBeforeDisableSeconds" to 0
      ),
      "targetGroups" to desired.dependencies.targetGroups,
      "securityGroups" to desired.dependencies.securityGroupNames,
      "platformHealthOnlyShowOverride" to false,
      "reason" to "rollin' back",
      "type" to "rollbackServerGroup",
      "moniker" to current?.moniker?.orcaClusterMoniker,
      "region" to desired.location.region,
      "credentials" to desired.location.account,
      "cloudProvider" to EC2_CLOUD_PROVIDER,
      "user" to DEFAULT_SERVICE_ACCOUNT
    )

  fun Resource<ClusterSpec>.buildOverrides(diffs: List<ResourceDiff<ServerGroup>>): Map<String, Any?> {
    val overrides: MutableMap<String, Any?> = spec.overrides.toMutableMap()
    val launchConfigOverrides = diffs.generateLaunchConfigOverrides()
    val capacityForScalingOverrides = generateCapacityOverridesIfAutoscaling(diffs)
    diffs.forEach { diff ->
      val region = getDesiredRegion(diff)
      val existingOverride: MutableMap<String, Any?> = mapper.convertValue(overrides[region] ?: mutableMapOf<String, Any?>())
      val availabilityZones = mutableMapOf("availabilityZones" to mutableMapOf(region to diff.desired.location.availabilityZones))
      val regionalLaunchConfig: MutableMap<String, Any?> = mapper.convertValue(launchConfigOverrides[region] ?: mutableMapOf<String, Any?>())
      val capacity: MutableMap<String, Any?> = mapper.convertValue(capacityForScalingOverrides[region] ?: mutableMapOf<String, Any?>())
      overrides[region] = existingOverride + availabilityZones + regionalLaunchConfig + capacity
    }
    return overrides
  }

  private fun Health.toMapForOrca() =
    mutableMapOf(
      "cooldown" to cooldown.seconds,
      "enabledMetrics" to enabledMetrics,
      "healthCheckType" to healthCheckType.name,
      "healthCheckGracePeriod" to warmup.seconds,
      "terminationPolicies" to terminationPolicies.map(TerminationPolicy::name),
    )

  private fun LaunchConfiguration.toMapForOrca(): Map<String, Any?> =
    mutableMapOf(
      "instanceMonitoring" to instanceMonitoring,
      "ebsOptimized" to ebsOptimized,
      "iamRole" to iamRole,
      "amiName" to imageId,
      "keyPair" to keyPair,
      "instanceType" to instanceType,
      "requireIMDSv2" to requireIMDSv2,
    )

  /**
   * Creates a map of region to launch config settings so that the ami name is resolved by region.
   */
  private fun List<ResourceDiff<ServerGroup>>.generateLaunchConfigOverrides(): Map<String, Map<String, Any?>> =
    associate { getDesiredRegion(it) to it.desired.launchConfiguration.toMapForOrca() }

  // generates targets only for diff clusters
  private fun ClusterSpec.generateRolloutTargets(diffs: List<ResourceDiff<ServerGroup>>): List<Map<String, Any>> =
    diffs
      .map {
        mapper.convertValue(
          RolloutTarget(
            EC2_CLOUD_PROVIDER,
            RolloutLocation(
              locations.account,
              getDesiredRegion(it),
              emptyList() // todo eb: should this be availability zones? is this only if you want to stagger deployment by availibility?
            )
          )
        )
      }

  /**
   * If the cluster has autoscaling, we need to generate the right desired capacity
   * so that we don't drastically downsize it.
   * Here we generate the capacity in an override block to hand to the managed rollout stage.
   *
   * @return an override block keyed by region
   */
  private fun Resource<ClusterSpec>.generateCapacityOverridesIfAutoscaling(
    diffs: List<ResourceDiff<ServerGroup>>
  ): Map<String, Any> {
    val defaultCapacity = spec.resolveCapacity()
    return diffs.associate { diff ->
      val capacity: Map<String, Any>? = when(diff.desired.capacity) {
        is DefaultCapacity -> null
        is AutoScalingCapacity -> mapOf (
          "capacity" to DefaultCapacity(
            min = defaultCapacity.min,
            max = defaultCapacity.max,
            desired = diff.resolveDesiredCapacity()
          )
        )
      }
      getDesiredRegion(diff) to capacity
    }.filterNotNullValues()
  }


  /**
   * For server groups with scaling policies, the [ClusterSpec] will not include a desired value. so we use the higher
   * of the desired value the server group we're replacing uses, or the min. This means we won't catastrophically down-
   * size a server group by deploying it.
   */
  private fun ResourceDiff<ServerGroup>.resolveDesiredCapacity() =
    when (desired.capacity) {
      // easy case: spec supplied the desired value as there are no scaling policies in effect
      is DefaultCapacity -> desired.capacity.desired
      // scaling policies exist, so use a safe value
      is AutoScalingCapacity -> maxOf(current?.capacity?.desired ?: 0, desired.capacity.min)
    }

  override fun ResourceDiff<ServerGroup>.resizeServerGroupJob(): Job {
    val current = requireNotNull(current) {
      "Current server group must not be null when generating a resize job"
    }
    return mapOf(
      "refId" to "1",
      "type" to "resizeServerGroup",
      "capacity" to mapOf(
        "min" to desired.capacity.min,
        "max" to desired.capacity.max,
        "desired" to desired.capacity.desired
      ),
      "cloudProvider" to EC2_CLOUD_PROVIDER,
      "credentials" to desired.location.account,
      "moniker" to current.moniker.orcaClusterMoniker,
      "region" to current.location.region,
      "serverGroupName" to current.name
    )
  }

  /**
   * @return list of stages to remove or create scaling policies in-place on the
   * current serverGroup.
   *
   * Scaling policies are treated as immutable by keel once applied. If an existing
   * policy is modified, it will be deleted and reapplied via a single task.
   */
  override fun ResourceDiff<ServerGroup>.modifyScalingPolicyJob(startingRefId: Int): List<Job> {
    var (refId, stages) = toDeletePolicyJob(startingRefId)
    val newTargetPolicies = when (current) {
      null -> desired.scaling.targetTrackingPolicies
      else ->
        desired.scaling.targetTrackingPolicies
          .subtract(current!!.scaling.targetTrackingPolicies)
    }
    val newStepPolicies = when (current) {
      null -> desired.scaling.stepScalingPolicies
      else ->
        desired.scaling.stepScalingPolicies
          .subtract(current!!.scaling.stepScalingPolicies)
    }

    if (newTargetPolicies.isNotEmpty()) {
      val (newRef, jobs) = newTargetPolicies.toCreateJob(refId, current ?: desired)
      refId = newRef
      stages.addAll(jobs)
    }

    if (newStepPolicies.isNotEmpty()) {
      stages.addAll(newStepPolicies.toCreateJob(refId, current ?: desired))
    }

    return stages
  }

  private fun ResourceDiff<ServerGroup>.toDeletePolicyJob(startingRefId: Int): Pair<Int, MutableList<Job>> {
    var refId = startingRefId
    val stages: MutableList<Map<String, Any?>> = mutableListOf()
    if (current == null) {
      return refId to stages
    }
    val current = current!!
    val targetPoliciesToRemove = current.scaling.targetTrackingPolicies.filterNot {
      desired.scaling.targetTrackingPolicies.contains(it)
    }
    val stepPoliciesToRemove = current.scaling.stepScalingPolicies.filterNot {
      desired.scaling.stepScalingPolicies.contains(it)
    }
    val policyNamesToRemove = targetPoliciesToRemove.mapNotNull { it.name } +
      stepPoliciesToRemove.mapNotNull { it.name }
        .toSet()

    stages.addAll(
      policyNamesToRemove
        .map {
          refId++
          mapOf(
            "refId" to refId.toString(),
            "requisiteStageRefIds" to when (refId) {
              0, 1 -> listOf()
              else -> listOf((refId - 1).toString())
            },
            "type" to "deleteScalingPolicy",
            "policyName" to it,
            "cloudProvider" to EC2_CLOUD_PROVIDER,
            "credentials" to desired.location.account,
            "moniker" to current.moniker.orcaClusterMoniker,
            "region" to current.location.region,
            "serverGroupName" to current.moniker.serverGroup
          )
        }
    )

    return Pair(refId, stages)
  }

  private fun Set<TargetTrackingPolicy>.toCreateJob(startingRefId: Int, serverGroup: ServerGroup): Pair<Int, List<Job>> {
    var refId = startingRefId
    val stages = map {
      refId++
      mapOf(
        "refId" to refId.toString(),
        "requisiteStageRefIds" to when (refId) {
          0, 1 -> emptyList<String>()
          else -> listOf((refId - 1).toString())
        },
        "type" to "upsertScalingPolicy",
        "cloudProvider" to EC2_CLOUD_PROVIDER,
        "credentials" to serverGroup.location.account,
        "moniker" to serverGroup.moniker.orcaClusterMoniker,
        "region" to serverGroup.location.region,
        "estimatedInstanceWarmup" to (it.warmup ?: DEFAULT_AUTOSCALE_INSTANCE_WARMUP).seconds,
        "serverGroupName" to serverGroup.moniker.serverGroup,
        "targetTrackingConfiguration" to mapOf(
          "targetValue" to it.targetValue,
          "disableScaleIn" to it.disableScaleIn,
          "predefinedMetricSpecification" to when (val metricsSpec = it.predefinedMetricSpec) {
            null -> null
            else -> with(metricsSpec) {
              PredefinedMetricSpecificationModel(
                predefinedMetricType = type,
                resourceLabel = label
              )
            }
          },
          "customizedMetricSpecification" to when (val metricsSpec = it.customMetricSpec) {
            null -> null
            else -> with(metricsSpec) {
              CustomizedMetricSpecificationModel(
                metricName = name,
                namespace = namespace,
                statistic = statistic,
                unit = unit,
                dimensions = dimensions?.map { d ->
                  MetricDimensionModel(name = d.name, value = d.value)
                }
              )
            }
          }
        )
      )
    }

    return refId to stages
  }

  private fun Set<StepScalingPolicy>.toCreateJob(startingRefId: Int, serverGroup: ServerGroup): List<Job> {
    var refId = startingRefId
    return map {
      refId++
      mapOf(
        "refId" to refId.toString(),
        "requisiteStageRefIds" to when (refId) {
          0, 1 -> listOf<String>()
          else -> listOf((refId - 1).toString())
        },
        "type" to "upsertScalingPolicy",
        "cloudProvider" to EC2_CLOUD_PROVIDER,
        "credentials" to serverGroup.location.account,
        "moniker" to serverGroup.moniker.orcaClusterMoniker,
        "region" to serverGroup.location.region,
        "adjustmentType" to it.adjustmentType,
        "alarm" to mapOf(
          "region" to serverGroup.location.region,
          "actionsEnabled" to it.actionsEnabled,
          "comparisonOperator" to it.comparisonOperator,
          "dimensions" to it.dimensions,
          "evaluationPeriods" to it.evaluationPeriods,
          "period" to it.period.seconds,
          "threshold" to it.threshold,
          "namespace" to it.namespace,
          "metricName" to it.metricName,
          "statistic" to it.statistic
        ),
        "step" to mapOf(
          "estimatedInstanceWarmup" to (it.warmup ?: DEFAULT_AUTOSCALE_INSTANCE_WARMUP).seconds,
          "metricAggregationType" to it.metricAggregationType,
          "stepAdjustments" to it.stepAdjustments.map { adjustment ->
            StepAdjustmentModel(
              metricIntervalLowerBound = adjustment.lowerBound,
              metricIntervalUpperBound = adjustment.upperBound,
              scalingAdjustment = adjustment.scalingAdjustment
            )
          }
        )
      )
    }
  }

  private suspend fun CloudDriverService.getActiveServerGroups(resource: Resource<ClusterSpec>): Iterable<ServerGroup> {
    val existingServerGroups: Map<String, List<ClouddriverServerGroup>> = getExistingServerGroupsByRegion(resource)
    val activeServerGroups = getActiveServerGroups(
      account = resource.spec.locations.account,
      moniker = resource.spec.moniker,
      regions = resource.spec.locations.regions.map { it.name }.toSet(),
      serviceAccount = resource.serviceAccount
    ).map { activeServerGroup ->
      if (resource.spec.deployWith is NoStrategy) {
        // we only care about num enabled if there is a deploy strategy
        // if there is no deploy strategy, ignore the calculation so that there isn't a diff ever in this property.
        activeServerGroup.copy(onlyEnabledServerGroup = true)
      } else {
        val numEnabled = existingServerGroups
          .getOrDefault(activeServerGroup.location.region, emptyList())
          .filter { !it.disabled }
          .size

        when (numEnabled) {
          1 -> activeServerGroup.copy(onlyEnabledServerGroup = true)
          else -> activeServerGroup.copy(onlyEnabledServerGroup = false)
        }
      }

    }

    val allSame: Boolean = activeServerGroups.distinctBy { it.launchConfiguration.appVersion }.size == 1
    val unhealthyRegions = mutableListOf<String>()
    activeServerGroups.forEach { serverGroup ->
      if (serverGroup.instanceCounts?.isHealthy(resource.spec.deployWith.health, resource.spec.resolveCapacity(serverGroup.location.region)) == false) {
        unhealthyRegions.add(serverGroup.location.region)
      }
    }
    val healthy: Boolean = unhealthyRegions.isEmpty()
    eventPublisher.publishEvent(ResourceHealthEvent(resource, healthy, unhealthyRegions, resource.spec.locations.regions.size))

    if (allSame && healthy) {
      // // only publish a successfully deployed event if the server group is healthy
      val appVersion = activeServerGroups.first().launchConfiguration.appVersion
      if (appVersion != null) {
        notifyArtifactDeployed(resource, appVersion)
      }
    }

    return activeServerGroups
  }

  override suspend fun getExistingServerGroupsByRegion(resource: Resource<ClusterSpec>): Map<String, List<ClouddriverServerGroup>> {
    val existingServerGroups: MutableMap<String, MutableList<ClouddriverServerGroup>> = mutableMapOf()

    try {
      cloudDriverService
        .listServerGroups(
          user = resource.serviceAccount,
          app = resource.spec.application,
          account = resource.spec.locations.account,
          cluster = resource.spec.moniker.toString()
        )
        .serverGroups
        .forEach { sg ->
          val existing = existingServerGroups.getOrPut(sg.region, { mutableListOf() })
          existing.add(sg)
          existingServerGroups[sg.region] = existing
        }
    } catch (e: HttpException) {
      if (!e.isNotFound) {
        throw e
      }
    }
    return existingServerGroups
  }

  private suspend fun CloudDriverService.getActiveServerGroups(
    account: String,
    moniker: Moniker,
    regions: Set<String>,
    serviceAccount: String
  ): Iterable<ServerGroup> =
    coroutineScope {
      regions.map {
        async {
          try {
            activeServerGroup(
              user = serviceAccount,
              app = moniker.app,
              account = account,
              cluster = moniker.toString(),
              region = it,
              cloudProvider = EC2_CLOUD_PROVIDER
            )
              .toServerGroup()
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

  /**
   * Transforms CloudDriver response to our server group model.
   */
  private fun ActiveServerGroup.toServerGroup(): ServerGroup {
    val launchTemplateData = launchTemplate?.launchTemplateData
    return ServerGroup(
      name = name,
      location = Location(
        account = accountName,
        region = region,
        vpc = cloudDriverCache.networkBy(vpcId).name ?: error("VPC with id $vpcId has no name!"),
        subnet = subnet(cloudDriverCache),
        availabilityZones = zones
      ),
      launchConfiguration =
      LaunchConfiguration(
        imageId = image.imageId,
        appVersion = image.appVersion,
        baseImageName = image.baseImageName,
        instanceType = launchConfig?.instanceType ?: launchTemplateData!!.instanceType,
        ebsOptimized = launchConfig?.ebsOptimized ?: launchTemplateData!!.ebsOptimized,
        iamRole = launchConfig?.iamInstanceProfile ?: launchTemplateData!!.iamInstanceProfile.name,
        keyPair = launchConfig?.keyName ?: launchTemplateData!!.keyName,
        instanceMonitoring = launchConfig?.instanceMonitoring?.enabled
          ?: launchTemplateData!!.monitoring.enabled,

        // Because launchConfig.ramdiskId can be null, need to do launchTemplateData?. instead of launchTemplateData!!
        ramdiskId = (launchConfig?.ramdiskId ?: launchTemplateData?.ramDiskId).orNull(),
        requireIMDSv2 = launchTemplateData?.metadataOptions?.get("httpTokens") == "required"
      ),
      buildInfo = buildInfo?.toEc2Api(),
      capacity = capacity.let {
        when (scalingPolicies.isEmpty()) {
          true -> DefaultCapacity(
            it.min,
            it.max,
            checkNotNull(it.desired) { "desired capacity is required unless you specify scaling policies" })
          false -> AutoScalingCapacity(it.min, it.max, it.desired)
        }
      },
      dependencies = ClusterDependencies(
        loadBalancerNames = loadBalancers,
        securityGroupNames = securityGroupNames,
        targetGroups = targetGroups
      ),
      health = Health(
        enabledMetrics = asg.enabledMetrics.map { Metric.valueOf(it) }.toSet(),
        cooldown = asg.defaultCooldown.let(Duration::ofSeconds),
        warmup = asg.healthCheckGracePeriod.let(Duration::ofSeconds),
        healthCheckType = asg.healthCheckType.let { HealthCheckType.valueOf(it) },
        terminationPolicies = asg.terminationPolicies.map { TerminationPolicy.valueOf(it) }.toSet()
      ),
      scaling = Scaling(
        suspendedProcesses = asg.suspendedProcesses.map { ScalingProcess.valueOf(it.processName) }.toSet(),
        targetTrackingPolicies = scalingPolicies.toTargetTrackingPolicies(),
        stepScalingPolicies = scalingPolicies.toStepScalingPolicies()
      ),
      tags = asg.tags.associateBy(Tag::key, Tag::value).filterNot { it.key in DEFAULT_TAGS },
      image = image.toEc2Api(),
      instanceCounts = instanceCounts.toEc2Api()
    )
  }

  private fun List<MetricDimensionModel>?.toSpec(): Set<MetricDimension> =
    when (this) {
      null -> emptySet()
      else ->
        this.filter { it.name != "AutoScalingGroupName" }
          .map { MetricDimension(it.name, it.value) }
          .toSet()
    }

  private fun CustomizedMetricSpecificationModel?.toSpec() =
    when (this) {
      null -> null
      else -> CustomizedMetricSpecification(
        name = metricName,
        namespace = namespace,
        statistic = statistic,
        unit = unit,
        dimensions = dimensions.toSpec()
      )
    }

  private fun PredefinedMetricSpecificationModel?.toSpec() =
    when (this) {
      null -> null
      else -> PredefinedMetricSpecification(
        type = predefinedMetricType,
        label = resourceLabel
      )
    }

  private fun List<StepAdjustmentModel>?.toSteps() =
    when (this) {
      null -> emptySet()
      else ->
        map {
          StepAdjustment(
            scalingAdjustment = it.scalingAdjustment,
            lowerBound = it.metricIntervalLowerBound,
            upperBound = it.metricIntervalUpperBound
          )
        }
          .toSet()
    }

  private fun List<ScalingPolicy>.toTargetTrackingPolicies(): Set<TargetTrackingPolicy> =
    filter { it.targetTrackingConfiguration != null }
      .map {
        TargetTrackingPolicy(
          name = it.policyName,
          warmup = Duration.ofSeconds(it.estimatedInstanceWarmup.toLong()),
          targetValue = it.targetTrackingConfiguration!!.targetValue,
          disableScaleIn = it.targetTrackingConfiguration!!.disableScaleIn ?: false,
          predefinedMetricSpec = it.targetTrackingConfiguration!!.predefinedMetricSpecification.toSpec(),
          customMetricSpec = it.targetTrackingConfiguration!!.customizedMetricSpecification.toSpec()
        )
      }
      .toSet()

  private fun List<ScalingPolicy>.toStepScalingPolicies(): Set<StepScalingPolicy> =
    filter { it.targetTrackingConfiguration == null && it.adjustmentType != null }
      .map {
        val alarm = it.alarms.first()
        StepScalingPolicy(
          name = it.policyName,
          adjustmentType = it.adjustmentType!!,
          actionsEnabled = alarm.actionsEnabled,
          comparisonOperator = alarm.comparisonOperator,
          dimensions = alarm.dimensions.toSpec(),
          evaluationPeriods = alarm.evaluationPeriods,
          period = Duration.ofSeconds(alarm.period.toLong()),
          threshold = alarm.threshold,
          metricName = alarm.metricName,
          namespace = alarm.namespace,
          statistic = alarm.statistic,
          warmup = Duration.ofSeconds(it.estimatedInstanceWarmup.toLong()),
          metricAggregationType = it.metricAggregationType!!,
          stepAdjustments = it.stepAdjustments.toSteps()
        )
      }
      .toSet()

  internal val ServerGroup.moniker: Moniker
    get() = parseMoniker(name)

  private val ServerGroup.securityGroupIds: Collection<String>
    get() = dependencies
      .securityGroupNames
      // no need to specify these as Orca will auto-assign them
      .filter { it !in setOf("nf-datacenter") }
      .map {
        cloudDriverCache.securityGroupByName(location.account, location.region, it).id
      }

  private val ActiveServerGroup.securityGroupNames: Set<String>
    get() = securityGroups.map {
      cloudDriverCache.securityGroupById(accountName, region, it).name
    }
      .toSet()

  private fun Instant.iso() =
    atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_DATE_TIME)

  /**
   * Translates a ServerGroup object to a ClusterSpec.ServerGroupSpec with default values omitted for export.
   */
  private fun ServerGroup.exportSpec(account: String, application: String): ServerGroupSpec {
    val defaults = ServerGroupSpec(
      capacity = CapacitySpec(1, 1, 1),
      dependencies = ClusterDependencies(),
      health = Health().toSpecWithoutDefaults(),
      scaling = Scaling(),
      tags = emptyMap()
    )

    val thisSpec = ServerGroupSpec(
      launchConfiguration = launchConfiguration.exportSpec(account, location.region, application),
      capacity = CapacitySpec(capacity.min, capacity.max, if (scaling.hasScalingPolicies()) null else capacity.desired),
      dependencies = dependencies,
      health = health.toSpecWithoutDefaults(),
      scaling = if (scaling.hasScalingPolicies()) scaling else null,
      tags = tags
    )

    return checkNotNull(buildSpecFromDiff(defaults, thisSpec))
  }

  /**
   * Translates a Health object to a ClusterSpec.HealthSpec with default values omitted for export.
   */
  private fun Health.toSpecWithoutDefaults(): HealthSpec? {
    val default = Health()
    return buildSpecFromDiff(default, this)
  }

  /**
   * Translates a LaunchConfiguration object to a [LaunchConfigurationSpec] with default values omitted for export.
   */
  private fun LaunchConfiguration.exportSpec(
    account: String,
    region: String,
    application: String
  ): LaunchConfigurationSpec? {
    val defaults = LaunchConfigurationSpec(
      ebsOptimized = false,
      iamRole = LaunchConfiguration.defaultIamRoleFor(application),
      instanceMonitoring = false,
      keyPair = defaultKeypair(account, region)
    )
    val thisSpec: LaunchConfigurationSpec = mapper.convertValue(this)
    return buildSpecFromDiff(defaults, thisSpec)
  }

  private fun defaultKeypair(account: String, region: String) =
    cloudDriverCache.defaultKeyPairForAccount(account).replace(REGION_PLACEHOLDER, region)

  companion object {
    // these tags are auto-applied by CloudDriver so we should not consider them in a diff as they
    // will never be specified as part of desired state
    private val DEFAULT_TAGS = setOf(
      "spinnaker:application",
      "spinnaker:stack",
      "spinnaker:details"
    )

    private val OVERRIDABLE_SERVER_GROUP_PROPERTIES = setOf(
      "health",
      "launchConfiguration",
      "dependencies",
      "scaling",
      "capacity"
    )

    private const val REGION_PLACEHOLDER = "{{region}}"
  }
}

/**
 * Returns `null` if the string is empty or null. Otherwise returns the string.
 */
private fun String?.orNull(): String? = if (isNullOrBlank()) null else this
