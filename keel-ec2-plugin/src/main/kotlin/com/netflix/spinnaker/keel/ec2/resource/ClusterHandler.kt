package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.Cluster.Dependencies
import com.netflix.spinnaker.keel.api.ec2.Cluster.Health
import com.netflix.spinnaker.keel.api.ec2.Cluster.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.Cluster.Location
import com.netflix.spinnaker.keel.api.ec2.Cluster.Moniker
import com.netflix.spinnaker.keel.api.ec2.Cluster.Scaling
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.retrofit.isNotFound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
  private val clock: Clock
) : ResourceHandler<Cluster> {

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "cluster",
    "clusters"
  ) to Cluster::class.java

  override fun current(resource: Resource<Cluster>) =
    runBlocking {
      cloudDriverService.getCluster(resource.spec)
    }

  override fun upsert(resource: Resource<Cluster>) {
    val taskRef = runBlocking {
      val spec = resource.spec
      val job = mutableMapOf(
        "application" to spec.moniker.application,
        "credentials" to spec.location.accountName,
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
          "min" to spec.capacity.min,
          "max" to spec.capacity.max,
          "desired" to spec.capacity.desired
        ),
        "targetHealthyDeployPercentage" to 100, // TODO: any reason to do otherwise?
        "cooldown" to spec.health.cooldown.seconds,
        "enabledMetrics" to spec.health.enabledMetrics,
        "healthCheckType" to spec.health.healthCheckType.name,
        "healthCheckGracePeriod" to spec.health.warmup.seconds,
        "instanceMonitoring" to spec.launchConfiguration.instanceMonitoring,
        "ebsOptimized" to spec.launchConfiguration.ebsOptimized,
        "iamRole" to spec.launchConfiguration.iamRole,
        "terminationPolicies" to spec.health.terminationPolicies.map(TerminationPolicy::name),
        "subnetType" to spec.location.subnet,
        "availabilityZones" to mapOf(
          spec.location.region to spec.location.availabilityZones
        ),
        "keyPair" to spec.launchConfiguration.keyPair,
        "suspendedProcesses" to spec.scaling.suspendedProcesses,
        "securityGroups" to spec.securityGroupIds,
        "stack" to spec.moniker.stack,
        "freeFormDetails" to spec.moniker.detail,
        "tags" to spec.tags,
        "useAmiBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
        "copySourceCustomBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
        "virtualizationType" to "hvm", // TODO: any reason to do otherwise?
        "moniker" to mapOf(
          "app" to spec.moniker.application,
          "stack" to spec.moniker.stack,
          "detail" to spec.moniker.detail,
          "cluster" to spec.moniker.cluster
        ),
        "amiName" to spec.launchConfiguration.imageId,
        "reason" to "Diff detected at ${clock.instant().iso()}",
        "instanceType" to spec.launchConfiguration.instanceType,
        "type" to "createServerGroup",
        "cloudProvider" to CLOUD_PROVIDER,
        "loadBalancers" to spec.dependencies.loadBalancerNames,
        "targetGroups" to spec.dependencies.targetGroups,
        "account" to spec.location.accountName
      )

      cloudDriverService.getAncestorServerGroupName(spec)
        ?.let { ancestorServerGroup ->
          job["source"] = mapOf(
            "account" to spec.location.accountName,
            "region" to spec.location.region,
            "asgName" to ancestorServerGroup
          )
          job["copySourceCustomBlockDeviceMappings"] = true
        }

      log.info("Upserting cluster using task: {}", job)

      orcaService
        .orchestrate(OrchestrationRequest(
          "Upsert cluster ${spec.moniker.cluster} in ${spec.location.accountName}/${spec.location.region}",
          spec.moniker.application,
          "Upsert cluster ${spec.moniker.cluster} in ${spec.location.accountName}/${spec.location.region}",
          listOf(Job("createServerGroup", job)),
          OrchestrationTrigger(resource.metadata.name.toString())
        ))
        .await()
    }
    log.info("Started task {} to upsert cluster", taskRef.ref)
  }

  override fun delete(resource: Resource<Cluster>) {
    TODO("not implemented")
  }

  private suspend fun CloudDriverService.getAncestorServerGroupName(spec: Cluster): String? =
    try {
      activeServerGroup(
        spec.moniker.application,
        spec.location.accountName,
        spec.moniker.cluster,
        spec.location.region,
        CLOUD_PROVIDER
      )
        .await()
        .asg
        .autoScalingGroupName
    } catch (e: HttpException) {
      if (e.isNotFound) {
        null
      } else {
        throw e
      }
    }

  private suspend fun CloudDriverService.getCluster(spec: Cluster): Cluster? {
    try {
      return withContext(Dispatchers.Default) {
        activeServerGroup(
          spec.moniker.application,
          spec.location.accountName,
          spec.moniker.cluster,
          spec.location.region,
          CLOUD_PROVIDER
        )
          .await()
          .run {
            Cluster(
              moniker = Moniker(moniker.app, moniker.stack, moniker.detail),
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
      }
    } catch (e: HttpException) {
      if (e.isNotFound) {
        return null
      }
      throw e
    }
  }

  private val Cluster.securityGroupIds: Collection<String>
    get() = dependencies.securityGroupNames.map {
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

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

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
