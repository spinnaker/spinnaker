package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.Dependencies
import com.netflix.spinnaker.keel.api.ec2.Health
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.Location
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.ec2.moniker
import com.netflix.spinnaker.keel.api.ec2.resolve
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.image.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.retrofit.isNotFound
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
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
  private val publisher: ApplicationEventPublisher,
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>
) : ResolvableResourceHandler<ClusterSpec, Set<ServerGroup>> {

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    group = apiVersion.group,
    singular = "cluster",
    plural = "clusters"
  ) to ClusterSpec::class.java

  override suspend fun desired(resource: Resource<ClusterSpec>): Set<ServerGroup> =
    with(resource.spec) {
      val resolvedImages = imageResolver.resolveImageId(resource)
      resolve(resolvedImages)
    }

  override suspend fun current(resource: Resource<ClusterSpec>): Set<ServerGroup> =
    cloudDriverService.getServerGroups(resource)

  override suspend fun upsert(
    resource: Resource<ClusterSpec>,
    resourceDiff: ResourceDiff<Set<ServerGroup>>
  ): List<Task> =
    coroutineScope {
      resourceDiff
        .toIndividualDiffs()
        .filter { diff -> diff.hasChanges() }
        .map { diff ->
          val spec = diff.desired
          val job = when {
            diff.isCapacityOnly() -> diff.resizeServerGroupJob()
            else -> diff.createServerGroupJob()
          }

          log.info("Upserting server group using task: {}", job)
          val description = "Upsert server group ${spec.moniker.name} in ${spec.location.accountName}/${spec.location.region}"

          async {
            orcaService
              .orchestrate(
                resource.serviceAccount,
                OrchestrationRequest(
                  description,
                  spec.moniker.app,
                  description,
                  listOf(Job(job["type"].toString(), job)),
                  OrchestrationTrigger(resource.id.toString())
                ))
              .let {
                log.info("Started task {} to upsert server group", it.ref)
                Task(id = it.taskId, name = description)
              }
          }
        }
        .map { it.await() }
    }

  private fun ResourceDiff<Set<ServerGroup>>.toIndividualDiffs() =
    desired
      .map { desired ->
        ResourceDiff(desired, current?.find { it.location.region == desired.location.region })
      }

  override suspend fun actuationInProgress(id: ResourceId) =
    orcaService
      .getCorrelatedExecutions(id.value)
      .isNotEmpty()

  /**
   * @return `true` if the only changes in the diff are to capacity.
   */
  private fun ResourceDiff<ServerGroup>.isCapacityOnly(): Boolean =
    current != null && affectedRootPropertyTypes.all { it == Capacity::class.java }

  private fun ResourceDiff<ServerGroup>.createServerGroupJob(): Map<String, Any?> =
    with(desired) {
      mutableMapOf(
        "application" to moniker.app,
        "credentials" to location.accountName,
        // <things to do with the strategy>
        // TODO: this will be parameterizable ultimately
        "strategy" to "redblack",
        "delayBeforeDisableSec" to 0,
        "delayBeforeScaleDownSec" to 0,
        "maxRemainingAsgs" to 2,
        // the 2hr default timeout sort-of? makes sense in an imperative
        // pipeline world where maybe within 2 hours the environment around
        // the instances will fix itself and the stage will succeed. Since
        // we are telling the red/black strategy to roll back on failure,
        // this will leave us in a position where we will instead keep
        // reattempting to clone the server group because the rollback
        // on failure of instances to come up will leave us in a non
        // converged state...
        "stageTimeoutMs" to Duration.ofMinutes(30).toMillis(),
        "rollback" to mapOf(
          "onFailure" to true
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
      )
    }
      .also { job ->
        current?.also { ancestor ->
          job["source"] = mapOf(
            "account" to ancestor.location.accountName,
            "region" to ancestor.location.region,
            "asgName" to ancestor.moniker.serverGroup
          )
          job["copySourceCustomBlockDeviceMappings"] = true
        }
      }

  private fun ResourceDiff<ServerGroup>.resizeServerGroupJob(): Map<String, Any?> {
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
      "credentials" to desired.location.accountName,
      "moniker" to mapOf(
        "app" to current.moniker.app,
        "stack" to current.moniker.stack,
        "detail" to current.moniker.detail,
        "cluster" to current.moniker.name,
        "sequence" to current.moniker.sequence
      ),
      "region" to current.location.region,
      "serverGroupName" to current.name
    )
  }

  override suspend fun delete(resource: Resource<ClusterSpec>) {
    TODO("not implemented")
  }

  private suspend fun CloudDriverService.getServerGroups(resource: Resource<ClusterSpec>): Set<ServerGroup> =
    coroutineScope {
      resource.spec.locations.regions.map {
        async {
          try {
            activeServerGroup(
              resource.serviceAccount,
              resource.spec.moniker.app,
              resource.spec.locations.accountName,
              resource.spec.moniker.name,
              it.region,
              CLOUD_PROVIDER
            )
              .toServerGroup()
              .also {
                publisher.publishEvent(ArtifactVersionDeployed(
                  resourceId = resource.id,
                  imageId = it.launchConfiguration.imageId
                ))
              }
          } catch (e: HttpException) {
            if (!e.isNotFound) {
              throw e
            }
            null
          }
        }
      }
        .mapNotNull { it.await() }
        .toSet()
//        .also { them ->
//          if (them.map { it.launchConfiguration.appVersion }.size == 1) {
//            publisher.publishEvent(ArtifactVersionDeployed(
//              resourceId = resource.id,
//              imageId = it.launchConfiguration.imageId
//            ))
//          }
//        }
    }

  /**
   * Transforms CloudDriver response to our server group model.
   */
  private fun ActiveServerGroup.toServerGroup() =
    ServerGroup(
      name = name,
      location = Location(
        accountName,
        region,
        subnet,
        zones
      ),
      launchConfiguration = launchConfig.run {
        LaunchConfiguration(
          imageId = imageId,
          appVersion = image.appVersion,
          instanceType = instanceType,
          ebsOptimized = ebsOptimized,
          iamRole = iamInstanceProfile,
          keyPair = keyName,
          instanceMonitoring = instanceMonitoring.enabled,
          ramdiskId = ramdiskId.orNull()
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

  private val ServerGroup.securityGroupIds: Collection<String>
    get() = dependencies
      .securityGroupNames
      // no need to specify these as Orca will auto-assign them, also the application security group
      // gets auto-created so may not exist yet
      .filter { it !in setOf("nf-infrastructure", "nf-datacenter", moniker.app) }
      .map {
        cloudDriverCache.securityGroupByName(location.accountName, location.region, it).id
      }

  private val ActiveServerGroup.subnet: String
    get() = asg.vpczoneIdentifier.substringBefore(",").let { subnetId ->
      cloudDriverCache
        .subnetBy(subnetId)
        .purpose ?: throw IllegalStateException("Subnet $subnetId has no purpose!")
    }

  private val ActiveServerGroup.securityGroupNames: Set<String>
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
