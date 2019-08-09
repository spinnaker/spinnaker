package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.ec2.cluster.Cluster
import com.netflix.spinnaker.keel.api.ec2.cluster.Dependencies
import com.netflix.spinnaker.keel.api.ec2.cluster.Health
import com.netflix.spinnaker.keel.api.ec2.cluster.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.api.ec2.cluster.Scaling
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceConflict
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.retrofit.isNotFound
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
  private val imageResolver: ImageResolver,
  private val clock: Clock,
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>
) : ResolvableResourceHandler<ClusterSpec, Cluster> {

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    group = apiVersion.group,
    singular = "cluster",
    plural = "clusters"
  ) to ClusterSpec::class.java

  override fun generateName(spec: ClusterSpec) = ResourceName(
    "ec2:cluster:${spec.location.accountName}:${spec.location.region}:${spec.moniker.name}"
  )

  override suspend fun desired(resource: Resource<ClusterSpec>): Cluster =
    with(resource.spec) {
      val imageId = imageResolver.resolveImageId(resource)
      Cluster(
        moniker = moniker,
        location = location,
        launchConfiguration = launchConfiguration.generateLaunchConfiguration(imageId),
        capacity = capacity,
        dependencies = dependencies,
        health = health,
        scaling = scaling,
        tags = tags
      )
    }

  override suspend fun current(resource: Resource<ClusterSpec>): Cluster? =
    cloudDriverService.getCluster(resource.spec, resource.serviceAccount)

  override suspend fun upsert(
    resource: Resource<ClusterSpec>,
    resourceDiff: ResourceDiff<Cluster>
  ): List<TaskRef> {
    val spec = resourceDiff.desired
    val job = when {
      resourceDiff.isCapacityOnly() -> spec.resizeServerGroupJob(resource.serviceAccount)
      else -> spec.createServerGroupJob(resource.serviceAccount)
    }

    log.info("Upserting cluster using task: {}", job)

    return orcaService
      .orchestrate(
        resource.serviceAccount,
        OrchestrationRequest(
          "Upsert cluster ${spec.moniker.name} in ${spec.location.accountName}/${spec.location.region}",
          spec.moniker.app,
          "Upsert cluster ${spec.moniker.name} in ${spec.location.accountName}/${spec.location.region}",
          listOf(Job(job["type"].toString(), job)),
          OrchestrationTrigger(resource.name.toString())
        ))
      .also { log.info("Started task {} to upsert cluster", it.ref) }
      // TODO: ugleee
      .let { listOf(TaskRef(it.ref)) }
  }

  override suspend fun actuationInProgress(name: ResourceName) =
    orcaService
      .getCorrelatedExecutions(name.value)
      .isNotEmpty()

  /**
   * @return `true` if the only changes in the diff are to capacity.
   */
  private fun ResourceDiff<Cluster>.isCapacityOnly(): Boolean =
    current != null && affectedRootPropertyTypes.all { it == Capacity::class.java }

  private suspend fun Cluster.createServerGroupJob(serviceAccount: String): Map<String, Any?> =
    mutableMapOf(
      "application" to moniker.app,
      "credentials" to location.accountName,
      // <things to do with the strategy>
      // TODO: this will be parameterizable ultimately
      "strategy" to "redblack",
      "delayBeforeDisableSec" to 0,
      "delayBeforeScaleDownSec" to 0,
      "maxRemainingAsgs" to 2,
      "rollback" to mapOf(
        "onFailure" to false
      ),
      "scaleDown" to false,
      // </things to do with the strategy>
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
      "moniker" to mapOf(
        "app" to moniker.app,
        "stack" to moniker.stack,
        "detail" to moniker.detail,
        "cluster" to moniker.name
      ),
      "amiName" to launchConfiguration.imageId,
      "reason" to "Diff detected at ${clock.instant().iso()}",
      "instanceType" to launchConfiguration.instanceType,
      "type" to "createServerGroup",
      "cloudProvider" to CLOUD_PROVIDER,
      "loadBalancers" to dependencies.loadBalancerNames,
      "targetGroups" to dependencies.targetGroups,
      "account" to location.accountName
    ).also { job ->
      cloudDriverService.getAncestorServerGroup(this, serviceAccount)
        ?.let { ancestorServerGroup ->
          job["source"] = mapOf(
            "account" to location.accountName,
            "region" to location.region,
            "asgName" to ancestorServerGroup.asg.autoScalingGroupName
          )
          job["copySourceCustomBlockDeviceMappings"] = true
        }
    }

  private suspend fun Cluster.resizeServerGroupJob(serviceAccount: String): Map<String, Any?> =
    cloudDriverService.getAncestorServerGroup(this, serviceAccount)
      ?.let { currentServerGroup ->
        mapOf(
          "type" to "resizeServerGroup",
          "capacity" to mapOf(
            "min" to capacity.min,
            "max" to capacity.max,
            "desired" to capacity.desired
          ),
          "cloudProvider" to currentServerGroup.cloudProvider,
          "credentials" to location.accountName,
          "moniker" to mapOf(
            "app" to currentServerGroup.moniker.app,
            "stack" to currentServerGroup.moniker.stack,
            "detail" to currentServerGroup.moniker.detail,
            "cluster" to currentServerGroup.moniker.cluster,
            "sequence" to currentServerGroup.moniker.sequence
          ),
          "region" to currentServerGroup.region,
          "serverGroupName" to currentServerGroup.asg.autoScalingGroupName
        )
      }
      ?: throw ResourceConflict("Could not find current server group for cluster ${moniker.name} in ${location.accountName} / ${location.region}")

  override suspend fun delete(resource: Resource<ClusterSpec>) {
    TODO("not implemented")
  }

  private suspend fun CloudDriverService.getAncestorServerGroup(spec: Cluster, serviceAccount: String): ClusterActiveServerGroup? =
    try {
      activeServerGroup(
        serviceAccount,
        spec.moniker.app,
        spec.location.accountName,
        spec.moniker.name,
        spec.location.region,
        CLOUD_PROVIDER
      )
    } catch (e: HttpException) {
      if (e.isNotFound) {
        null
      } else {
        throw e
      }
    }

  private suspend fun CloudDriverService.getCluster(spec: ClusterSpec, serviceAccount: String): Cluster? {
    try {
      return activeServerGroup(
        serviceAccount,
        spec.moniker.app,
        spec.location.accountName,
        spec.moniker.name,
        spec.location.region,
        CLOUD_PROVIDER
      )
        .run {
          Cluster(
            moniker = Moniker(app = moniker.app, stack = moniker.stack, detail = moniker.detail),
            location = Location(
              accountName,
              region,
              subnet,
              zones
            ),
            launchConfiguration = launchConfig.run {
              LaunchConfiguration(
                imageId,
                instanceType,
                ebsOptimized,
                iamInstanceProfile,
                keyName,
                instanceMonitoring.enabled,
                ramdiskId.orNull()
              )
            },
            capacity = capacity.let { Capacity(it.min, it.max, it.desired) },
            dependencies = Dependencies(
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
              suspendedProcesses = asg.suspendedProcesses.map { ScalingProcess.valueOf(it) }.toSet()
            ),
            tags = asg.tags.associateBy(Tag::key, Tag::value).filterNot { it.key in DEFAULT_TAGS }
          )
        }
    } catch (e: HttpException) {
      if (e.isNotFound) {
        return null
      }
      throw e
    }
  }

  private val Cluster.securityGroupIds: Collection<String>
    get() = dependencies
      .securityGroupNames
      // no need to specify these as Orca will auto-assign them, also the application security group
      // gets auto-created so may not exist yet
      .filter { it !in setOf("nf-infrastructure", "nf-datacenter", moniker.app) }
      .map {
        cloudDriverCache.securityGroupByName(location.accountName, location.region, it).id
      }

  private val ClusterActiveServerGroup.subnet: String
    get() = asg.vpczoneIdentifier.substringBefore(",").let { subnetId ->
      cloudDriverCache
        .subnetBy(subnetId)
        .purpose ?: throw IllegalStateException("Subnet $subnetId has no purpose!")
    }

  private val ClusterActiveServerGroup.securityGroupNames: Set<String>
    get() = securityGroups.map {
      cloudDriverCache.securityGroupById(accountName, region, it).name
    }
      .toSet()

  private fun Instant.iso() =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME)

  companion object {
    // these tags are auto-applied by CloudDriver so we should not consider them in a diff as they
    // will never be specified as part of desired state
    private val DEFAULT_TAGS = setOf(
      "spinnaker:application",
      "spinnaker:stack",
      "spinnaker:details"
    )
  }
}

/**
 * Returns `null` if the string is empty or null. Otherwise returns the string.
 */
private fun String?.orNull(): String? = if (isNullOrBlank()) null else this
