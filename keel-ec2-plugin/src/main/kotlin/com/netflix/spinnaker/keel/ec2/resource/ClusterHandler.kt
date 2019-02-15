package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.ClusterName
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.ec2.AmazonResourceHandler
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
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
) : AmazonResourceHandler<Cluster> {
  override fun current(spec: Cluster, request: Resource<Cluster>): Cluster? =
    runBlocking {
      cloudDriverService.getCluster(spec)
    }

  override fun converge(resourceName: ResourceName, spec: Cluster) {
    val taskRef = runBlocking {
      val job = mutableMapOf(
        "application" to spec.application,
        "credentials" to spec.accountName,
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
        "cooldown" to spec.cooldown.seconds,
        "enabledMetrics" to spec.enabledMetrics,
        "healthCheckType" to spec.healthCheckType.name,
        "healthCheckGracePeriod" to spec.healthCheckGracePeriod.seconds,
        "instanceMonitoring" to spec.instanceMonitoring,
        "ebsOptimized" to spec.ebsOptimized,
        "iamRole" to spec.iamRole,
        "terminationPolicies" to spec.terminationPolicies.map(TerminationPolicy::name),
        "subnetType" to spec.subnet,
        "availabilityZones" to mapOf(
          spec.region to spec.availabilityZones
        ),
        "keyPair" to spec.keyPair,
        "suspendedProcesses" to spec.suspendedProcesses,
        "securityGroups" to spec.securityGroupIds,
        "stack" to spec.name.stack,
        "freeFormDetails" to spec.name.detail,
        "tags" to spec.tags,
        "useAmiBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
        "copySourceCustomBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
        "virtualizationType" to "hvm", // TODO: any reason to do otherwise?
        "moniker" to mapOf(
          "app" to spec.application,
          "stack" to spec.name.stack,
          "detail" to spec.name.detail,
          "cluster" to spec.name.toString()
        ),
        "amiName" to spec.imageId,
        "reason" to "Diff detected at ${clock.instant().iso()}",
        "instanceType" to spec.instanceType,
        "type" to "createServerGroup",
        "cloudProvider" to CLOUD_PROVIDER,
        "loadBalancers" to spec.loadBalancerNames,
        "targetGroups" to spec.targetGroups,
        "account" to spec.accountName
      )

      cloudDriverService.getAncestorServerGroupName(spec)
        ?.let { ancestorServerGroup ->
          job["source"] = mapOf(
            "account" to spec.accountName,
            "region" to spec.region,
            "asgName" to ancestorServerGroup
          )
          job["copySourceCustomBlockDeviceMappings"] = true
        }

      log.info("Upserting cluster using task: {}", job)

      orcaService
        .orchestrate(OrchestrationRequest(
          "Upsert cluster ${spec.name} in ${spec.accountName}/${spec.region}",
          spec.application,
          "Upsert cluster ${spec.name} in ${spec.accountName}/${spec.region}",
          listOf(Job("createServerGroup", job)),
          OrchestrationTrigger(resourceName.toString())
        ))
        .await()
    }
    log.info("Started task {} to upsert cluster", taskRef.ref)
  }

  override fun delete(resourceName: ResourceName, spec: SecurityGroup) {
    TODO("not implemented")
  }

  private suspend fun CloudDriverService.getAncestorServerGroupName(spec: Cluster): String? =
    try {
      activeServerGroup(
        spec.application,
        spec.accountName,
        spec.name.toString(),
        spec.region,
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
        activeServerGroup(spec.application, spec.accountName, spec.name.toString(), spec.region, CLOUD_PROVIDER)
          .await()
          .run {
            Cluster(
              moniker.app,
              moniker.let { ClusterName(it.app, it.stack, it.detail) },
              launchConfig.imageId,
              accountName,
              region,
              subnet,
              zones,
              launchConfig.instanceType,
              launchConfig.ebsOptimized,
              capacity.let { Capacity(it.min, it.max, it.desired) },
              launchConfig.ramdiskId.orNull(),
              launchConfig.iamInstanceProfile,
              launchConfig.keyName,
              loadBalancers,
              securityGroupNames,
              targetGroups,
              launchConfig.instanceMonitoring.enabled,
              asg.enabledMetrics.map { Metric.valueOf(it) },
              asg.defaultCooldown.let(Duration::ofSeconds),
              asg.healthCheckGracePeriod.let(Duration::ofSeconds),
              asg.healthCheckType.let { HealthCheckType.valueOf(it) },
              asg.suspendedProcesses.map { ScalingProcess.valueOf(it) },
              asg.terminationPolicies.map { TerminationPolicy.valueOf(it) },
              asg.tags.associateBy(Tag::key, Tag::value)
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
    get() = securityGroupNames.map {
      cloudDriverCache.securityGroupByName(accountName, region, it).id
    }

  private val ClusterActiveServerGroup.subnet: String
    get() = asg.vpczoneIdentifier.substringBefore(",").let { subnetId ->
      cloudDriverCache
        .subnetBy(subnetId)
        .purpose ?: throw IllegalStateException("Subnet $subnetId has no purpose!")
    }

  private val ClusterActiveServerGroup.vpcName: String?
    get() = cloudDriverCache.networkBy(vpcId).name

  private val ClusterActiveServerGroup.securityGroupNames: Collection<String>
    get() = securityGroups.map {
      cloudDriverCache.securityGroupById(accountName, region, it).name
    }

  private fun Instant.iso() =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

/**
 * Returns `null` if the string is empty or null. Otherwise returns the string.
 */
private fun String?.orNull(): String? = if (isNullOrBlank()) null else this
