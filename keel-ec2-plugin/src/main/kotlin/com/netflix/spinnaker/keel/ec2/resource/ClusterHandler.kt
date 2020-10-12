package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.frigga.ami.AppVersion
import com.netflix.rocket.api.artifact.internal.debian.DebianArtifactParser
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.UNKNOWN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.HealthSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.CustomizedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.Location
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.MetricDimension
import com.netflix.spinnaker.keel.api.ec2.PredefinedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.ReferenceArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.ActiveServerGroupImage
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.Health
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.StepAdjustment
import com.netflix.spinnaker.keel.api.ec2.StepScalingPolicy
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.ec2.byRegion
import com.netflix.spinnaker.keel.api.ec2.resolve
import com.netflix.spinnaker.keel.api.ec2.resolveCapacity
import com.netflix.spinnaker.keel.api.plugins.ResolvableResourceHandler
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
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
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroup as ClouddriverServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.StepAdjustmentModel
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.clouddriver.model.subnet
import com.netflix.spinnaker.keel.core.orcaClusterMoniker
import com.netflix.spinnaker.keel.core.parseMoniker
import com.netflix.spinnaker.keel.core.serverGroup
import com.netflix.spinnaker.keel.diff.toIndividualDiffs
import com.netflix.spinnaker.keel.ec2.MissingAppVersionException
import com.netflix.spinnaker.keel.ec2.toEc2Api
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.exceptions.ActiveServerGroupsException
import com.netflix.spinnaker.keel.exceptions.ExportError
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.dependsOn
import com.netflix.spinnaker.keel.orca.restrictedExecutionWindow
import com.netflix.spinnaker.keel.orca.toOrcaJobProperties
import com.netflix.spinnaker.keel.orca.waitStage
import com.netflix.spinnaker.keel.plugin.buildSpecFromDiff
import com.netflix.spinnaker.keel.retrofit.isNotFound
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ClusterHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  private val taskLauncher: TaskLauncher,
  private val clock: Clock,
  override val eventPublisher: EventPublisher,
  resolvers: List<Resolver<*>>,
  private val clusterExportHelper: ClusterExportHelper
) : ResolvableResourceHandler<ClusterSpec, Map<String, ServerGroup>>(resolvers) {

  private val debianArtifactParser = DebianArtifactParser()

  private val mapper = configuredObjectMapper()

  override val supportedKind = EC2_CLUSTER_V1

  override suspend fun toResolvedType(resource: Resource<ClusterSpec>): Map<String, ServerGroup> =
    with(resource.spec) {
      resolve().byRegion()
    }

  override suspend fun current(resource: Resource<ClusterSpec>): Map<String, ServerGroup> =
    cloudDriverService
      .getActiveServerGroups(resource)
      .byRegion()

  override suspend fun upsert(
    resource: Resource<ClusterSpec>,
    resourceDiff: ResourceDiff<Map<String, ServerGroup>>
  ): List<Task> =
    coroutineScope {
      val diffs = resourceDiff
        .toIndividualDiffs()
        .filter { diff -> diff.hasChanges() }

      val deferred: MutableList<Deferred<Task>> = mutableListOf()
      val modifyDiffs = diffs.filter { it.isCapacityOrAutoScalingOnly() || it.isEnabledOnly() }
      val createDiffs = diffs - modifyDiffs

      if (modifyDiffs.isNotEmpty()) {
        deferred.addAll(
          modifyInPlace(resource, modifyDiffs)
        )
      }

      val version = diffs.first().desired.launchConfiguration.appVersion
        ?: throw MissingAppVersionException(resource.id)

      if (resource.spec.deployWith.isStaggered && createDiffs.isNotEmpty()) {
        val tasks = upsertStaggered(resource, createDiffs, version)
        return@coroutineScope tasks + deferred.map { it.await() }
      }

      deferred.addAll(
        upsertUnstaggered(resource, createDiffs, version)
      )

      if (createDiffs.isNotEmpty()) {
        notifyArtifactDeploying(resource, version)
      }

      return@coroutineScope deferred.map { it.await() }
    }

  private suspend fun modifyInPlace(
    resource: Resource<ClusterSpec>,
    diffs: List<ResourceDiff<ServerGroup>>
  ): List<Deferred<Task>> =
    coroutineScope {
      diffs.mapNotNull { diff ->
        val (job, description) = when {
          diff.isCapacityOnly() -> listOf(diff.resizeServerGroupJob()) to "Modify capacity of server group ${diff.desired.moniker} in " +
            "${diff.desired.location.account}/${diff.desired.location.region}"
          diff.isAutoScalingOnly() -> diff.modifyScalingPolicyJob() to "Modify auto-scaling of server group ${diff.desired.moniker} in " +
            "${diff.desired.location.account}/${diff.desired.location.region}"
          diff.isEnabledOnly() -> {
            val appVersion = diff.desired.launchConfiguration.appVersion ?: throw MissingAppVersionException(resource.id)
            val job = diff.disableOtherServerGroupJob(resource, appVersion)
            listOf(job) to "Disable extra active server group ${job["asgName"]} in " +
              "${diff.desired.location.account}/${diff.desired.location.region}"
          }
          else -> listOf(diff.resizeServerGroupJob()) + diff.modifyScalingPolicyJob(1) to "Modify capacity and auto-scaling of server group ${diff.desired.moniker} in " +
            "${diff.desired.location.account}/${diff.desired.location.region}"
        }

        if (job.isEmpty()) {
          null
        } else {
          log.info("Modifying server group in-place using task: {}", job)

          async {
            taskLauncher.submitJob(
              resource = resource,
              description = description,
              correlationId = "${resource.id}:${diff.desired.location.region}",
              stages = job
            )
          }
        }
      }
    }

  private suspend fun upsertUnstaggered(
    resource: Resource<ClusterSpec>,
    diffs: List<ResourceDiff<ServerGroup>>,
    version: String,
    dependsOn: String? = null
  ): List<Deferred<Task>> =
    coroutineScope {
      diffs.mapNotNull { diff ->
        val stages: MutableList<Map<String, Any?>> = mutableListOf()
        var refId = 0

        if (dependsOn != null) {
          stages.add(dependsOn(dependsOn))
          refId++
        }
        when {
          diff.shouldDeployAndModifyScalingPolicies() -> {
            stages.add(diff.createServerGroupJob(refId, resource))
            refId++
            stages.addAll(diff.modifyScalingPolicyJob(refId))
          }
          else -> stages.add(diff.createServerGroupJob(refId, resource))
        }

        if (stages.isEmpty()) {
          null
        } else {
          log.info("Upsert server group using task: {}", stages)

          async {
            taskLauncher.submitJob(
              resource = resource,
              description = "Deploy $version to server group ${diff.desired.moniker}  in " +
                "${diff.desired.location.account}/${diff.desired.location.region}",
              correlationId = "${resource.id}:${diff.desired.location.region}",
              stages = stages
            )
          }
        }
      }
    }

  private suspend fun upsertStaggered(
    resource: Resource<ClusterSpec>,
    diffs: List<ResourceDiff<ServerGroup>>,
    version: String
  ): List<Task> =
    coroutineScope {
      val regionalDiffs = diffs.associateBy { it.desired.location.region }
      val tasks: MutableList<Task> = mutableListOf()
      var priorExecutionId: String? = null
      val staggeredRegions = resource.spec.deployWith.stagger.map {
        it.region
      }
        .toSet()

      // If any, these are deployed in-parallel after all regions with a defined stagger
      val unstaggeredRegions = regionalDiffs.keys - staggeredRegions

      for (stagger in resource.spec.deployWith.stagger) {
        if (!regionalDiffs.containsKey(stagger.region)) {
          continue
        }

        val diff = regionalDiffs[stagger.region] as ResourceDiff<ServerGroup>
        val stages: MutableList<Map<String, Any?>> = mutableListOf()
        var refId = 0

        /**
         * Given regions staggered as [A, B, C], this makes the execution of the B
         * `createServerGroup` task dependent on the A task, and C dependent on B,
         * while preserving the unstaggered behavior of an orca task per region.
         */
        if (priorExecutionId != null) {
          stages.add(dependsOn(priorExecutionId))
          refId++
        }

        val stage = diff.createServerGroupJob(refId, resource).toMutableMap()

        refId++

        /**
         * If regions are staggered by time windows, add a `restrictedExecutionWindow`
         * to the `createServerGroup` stage.
         */
        if (stagger.hours != null) {
          val hours = stagger.hours!!.split("-").map { it.toInt() }
          stage.putAll(restrictedExecutionWindow(hours[0], hours[1]))
        }

        stages.add(stage)

        if (diff.shouldDeployAndModifyScalingPolicies()) {
          stages.addAll(diff.modifyScalingPolicyJob(refId))
        }

        if (stagger.pauseTime != null) {
          stages.add(
            waitStage(stagger.pauseTime!!, stages.size)
          )
        }

        val deferred = async {
          taskLauncher.submitJob(
            resource = resource,
            description = "Deploy $version to server group ${diff.desired.moniker} in " +
              "${diff.desired.location.account}/${diff.desired.location.region}",
            correlationId = "${resource.id}:${diff.desired.location.region}",
            stages = stages
          )
        }

        notifyArtifactDeploying(resource, version)

        val task = deferred.await()
        priorExecutionId = task.id
        tasks.add(task)
      }

      /**
       * `ClusterSpec.stagger` doesn't have to define a stagger for all of the regions clusters.
       * If a cluster deploys into 4 regions [A, B, C, D] but only defines a stagger for [A, B],
       * [C, D] will deploy in parallel after the completion of B and any pauseTime it defines.
       */
      if (unstaggeredRegions.isNotEmpty()) {
        val unstaggeredDiffs = regionalDiffs
          .filter { unstaggeredRegions.contains(it.key) }
          .map { it.value }

        tasks.addAll(
          upsertUnstaggered(resource, unstaggeredDiffs, version, priorExecutionId)
            .map { it.await() }
        )
      }

      return@coroutineScope tasks
    }

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
    val appversion = AppVersion.parseName(base.image?.appVersion).packageName

    val spec = ClusterSpec(
      moniker = exportable.moniker,
      imageProvider = if (appversion != null) {
        ReferenceArtifactImageProvider(reference = appversion)
      } else {
        null
      },
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

    val base = serverGroups.values.maxBy { it.capacity.desired ?: it.capacity.max }
      ?: throw ExportError("Unable to determine largest server group: $serverGroups")

    if (base.image == null) {
      throw ExportError("Server group ${base.name} doesn't have image information - unable to correctly export artifact.")
    }

    // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
    val artifactName = AppVersion.parseName(base.launchConfiguration.appVersion).packageName

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

  /**
   * This function attempts to use image description to guess what OS the image is.
   * If not found, it will throw an error.
   */
  fun guessBaseOsFrom(image: ActiveServerGroupImage?): String =
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
    resource
      .spec
      .locations
      .regions
      .map { it.name }
      .any { region ->
        orcaService
          .getCorrelatedExecutions("${resource.id}:$region")
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
  private fun ResourceDiff<ServerGroup>.isCapacityOnly(): Boolean =
    current != null && affectedRootPropertyTypes.all { it == Capacity::class.java }

  /**
   * @return `true` if the only changes in the diff are to scaling.
   */
  private fun ResourceDiff<ServerGroup>.isAutoScalingOnly(): Boolean =
    current != null &&
      affectedRootPropertyTypes.any { it == Scaling::class.java } &&
      affectedRootPropertyTypes.all { it == Capacity::class.java || it == Scaling::class.java } &&
      current!!.scaling.suspendedProcesses == desired.scaling.suspendedProcesses &&
      current!!.capacity.min == desired.capacity.min &&
      current!!.capacity.max == desired.capacity.max

  /**
   * @return true if the only difference is in the onlyEnabledServerGroup property
   */
  private fun ResourceDiff<ServerGroup>.isEnabledOnly(): Boolean =
    current != null &&
      affectedRootPropertyNames.all { it == "onlyEnabledServerGroup" } &&
      current!!.onlyEnabledServerGroup != desired.onlyEnabledServerGroup

  /**
   * @return `true` if [current] doesn't exist and desired includes a scaling policy.
   */
  private fun ResourceDiff<ServerGroup>.shouldDeployAndModifyScalingPolicies(): Boolean =
    (current == null && desired.scaling.hasScalingPolicies()) ||
      (current != null && !isCapacityOrAutoScalingOnly() && hasScalingPolicyDiff())

  /**
   * @return `true` if [current] exists and the diff impacts scaling policies or capacity only.
   */
  private fun ResourceDiff<ServerGroup>.isCapacityOrAutoScalingOnly(): Boolean =
    current != null &&
      affectedRootPropertyTypes.all { it == Capacity::class.java || it == Scaling::class.java } &&
      current!!.scaling.suspendedProcesses == desired.scaling.suspendedProcesses

  /**
   * @return `true` if [current] exists and the diff includes a scaling policy change.
   */
  private fun ResourceDiff<ServerGroup>.hasScalingPolicyDiff(): Boolean =
    current != null && affectedRootPropertyTypes.contains(Scaling::class.java) &&
      (
        current!!.scaling.targetTrackingPolicies != desired.scaling.targetTrackingPolicies ||
          current!!.scaling.stepScalingPolicies != desired.scaling.stepScalingPolicies
        )

  private fun ResourceDiff<ServerGroup>.disableOtherServerGroupJob(resource: Resource<ClusterSpec>, desiredVersion: String): Map<String, Any?> {
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
      "cloudProvider" to CLOUD_PROVIDER,
      "credentials" to desired.location.account,
      "moniker" to sgToDisable.moniker.orcaClusterMoniker,
      "region" to sgToDisable.region,
      "serverGroupName" to sgToDisable.name,
      "asgName" to sgToDisable.name
    )
  }

  private fun ResourceDiff<ServerGroup>.createServerGroupJob(refId: Int, resource: Resource<ClusterSpec>): Map<String, Any?> =
    createServerGroupJobBase(refId) + resource.spec.deployWith.toOrcaJobProperties("Amazon")

  private fun ResourceDiff<ServerGroup>.createServerGroupJobBase(startingRefId: Int = 0): Map<String, Any?> =
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
          "desired" to capacity.desired
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
        "cloudProvider" to CLOUD_PROVIDER,
        "loadBalancers" to dependencies.loadBalancerNames,
        "targetGroups" to dependencies.targetGroups,
        "account" to location.account
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
      }

  private fun ResourceDiff<ServerGroup>.resizeServerGroupJob(): Map<String, Any?> {
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
      "cloudProvider" to CLOUD_PROVIDER,
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
  private fun ResourceDiff<ServerGroup>.modifyScalingPolicyJob(startingRefId: Int = 0): List<Map<String, Any?>> {
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

  private fun ResourceDiff<ServerGroup>.toDeletePolicyJob(startingRefId: Int):
    Pair<Int, MutableList<Map<String, Any?>>> {
      var refId = startingRefId
      val stages: MutableList<Map<String, Any?>> = mutableListOf()
      if (current == null) {
        return Pair(refId, stages)
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
              "cloudProvider" to CLOUD_PROVIDER,
              "credentials" to desired.location.account,
              "moniker" to current.moniker.orcaClusterMoniker,
              "region" to current.location.region
            )
          }
          .toMutableList()
      )

      return Pair(refId, stages)
    }

  private fun Set<TargetTrackingPolicy>.toCreateJob(startingRefId: Int, serverGroup: ServerGroup):
    Pair<Int, List<Map<String, Any?>>> {
      var refId = startingRefId
      val stages = map {
        refId++
        mapOf(
          "refId" to refId.toString(),
          "requisiteStageRefIds" to when (refId) {
            0, 1 -> listOf()
            else -> listOf((refId - 1).toString())
          },
          "type" to "upsertScalingPolicy",
          "cloudProvider" to CLOUD_PROVIDER,
          "credentials" to serverGroup.location.account,
          "moniker" to serverGroup.moniker.orcaClusterMoniker,
          "region" to serverGroup.location.region,
          "estimatedInstanceWarmup" to it.warmup.seconds,
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

      return Pair(refId, stages)
    }

  private fun Set<StepScalingPolicy>.toCreateJob(startingRefId: Int, serverGroup: ServerGroup):
    List<Map<String, Any?>> {
      var refId = startingRefId
      return map {
        refId++
        mapOf(
          "refId" to refId.toString(),
          "requisiteStageRefIds" to when (refId) {
            0, 1 -> listOf()
            else -> listOf((refId - 1).toString())
          },
          "type" to "upsertScalingPolicy",
          "cloudProvider" to CLOUD_PROVIDER,
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
            "estimatedInstanceWarmup" to it.warmup.seconds,
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
      val numEnabled = existingServerGroups
        .getOrDefault(activeServerGroup.location.region, emptyList())
        .filter { !it.disabled }
        .size

      when (numEnabled) {
        1 -> activeServerGroup.copy(onlyEnabledServerGroup = true)
        else -> activeServerGroup.copy(onlyEnabledServerGroup = false)
      }
    }

    val allSame: Boolean = activeServerGroups.distinctBy { it.launchConfiguration.appVersion }.size == 1
    val unhealthyRegions = mutableListOf<String>()
    activeServerGroups.forEach {serverGroup ->
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

  private suspend fun getExistingServerGroupsByRegion(resource: Resource<ClusterSpec>): Map<String, List<ClouddriverServerGroup>> {
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
              cloudProvider = CLOUD_PROVIDER
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
  private fun ActiveServerGroup.toServerGroup() =
    ServerGroup(
      name = name,
      location = Location(
        account = accountName,
        region = region,
        vpc = cloudDriverCache.networkBy(vpcId).name ?: error("VPC with id $vpcId has no name!"),
        subnet = subnet(cloudDriverCache),
        availabilityZones = zones
      ),
      launchConfiguration = launchConfig.run {
        LaunchConfiguration(
          imageId = imageId,
          appVersion = image.appVersion,
          baseImageVersion = image.baseImageVersion,
          instanceType = instanceType,
          ebsOptimized = ebsOptimized,
          iamRole = iamInstanceProfile,
          keyPair = keyName,
          instanceMonitoring = instanceMonitoring.enabled,
          ramdiskId = ramdiskId.orNull()
        )
      },
      buildInfo = buildInfo?.toEc2Api(),
      capacity = capacity.let {
        when (scalingPolicies.isEmpty()) {
          true -> Capacity(it.min, it.max, it.desired)
          false -> Capacity(it.min, it.max)
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
          disableScaleIn = it.targetTrackingConfiguration!!.disableScaleIn,
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

  private val ServerGroup.moniker: Moniker
    get() = parseMoniker(name)

  private val ServerGroup.securityGroupIds: Collection<String>
    get() = dependencies
      .securityGroupNames
      // no need to specify these as Orca will auto-assign them, also the application security group
      // gets auto-created so may not exist yet
      .filter { it !in setOf("nf-infrastructure", "nf-datacenter", moniker.app) }
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
      capacity = Capacity(1, 1, 1),
      dependencies = ClusterDependencies(),
      health = Health().toSpecWithoutDefaults(),
      scaling = Scaling(),
      tags = emptyMap()
    )

    val thisSpec = ServerGroupSpec(
      launchConfiguration = launchConfiguration.exportSpec(account, location.region, application),
      capacity = capacity,
      dependencies = dependencies,
      health = health.toSpecWithoutDefaults(),
      scaling = if (!scaling.hasScalingPolicies()) {
        null
      } else {
        scaling
      },
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
