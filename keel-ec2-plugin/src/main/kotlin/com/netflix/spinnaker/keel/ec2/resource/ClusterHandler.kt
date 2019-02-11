package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.ClusterName
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.InstanceType
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.NOT_FOUND
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
      orcaService
        .orchestrate(OrchestrationRequest(
          "Upsert cluster ${spec.name} in ${spec.accountName}/${spec.region}",
          spec.application,
          "Upsert security cluster ${spec.name} in ${spec.accountName}/${spec.region}",
          listOf(Job(
            "createServerGroup",
            mutableMapOf(
              "application" to spec.application,
              "credentials" to spec.accountName,
              "strategy" to "redblack",
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
              "subnetType" to spec.vpcName,
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
              "base64UserData" to spec.base64UserData,
              "virtualizationType" to "hvm", // TODO: any reason to do otherwise?
              "moniker" to mapOf(
                "app" to spec.application,
                "stack" to spec.name.stack,
                "detail" to spec.name.detail,
                "cluster" to spec.name.toString()
              ),
              "amiName" to spec.imageId,
              "reason" to "Diff detected at ${clock.instant().iso()}",
              "instanceType" to spec.instanceType.value,
              "type" to "createServerGroup",
              "cloudProvider" to CLOUD_PROVIDER,
              "loadBalancers" to spec.loadBalancerNames,
              "targetGroups" to spec.targetGroups,
              "account" to spec.accountName
            )
          )),
          OrchestrationTrigger(resourceName.toString())
        ))
        .await()
    }
    log.info("Started task {} to upsert security group", taskRef.ref)
  }

  override fun delete(resourceName: ResourceName, spec: SecurityGroup) {
    TODO("not implemented")
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
              zones,
              vpcName,
              capacity.let { Capacity(it.min, it.max, it.desired) },
              launchConfig.instanceType.let(::InstanceType),
              launchConfig.ebsOptimized,
              launchConfig.ramdiskId,
              launchConfig.userData,
              asg.tags.associateBy(Tag::key, Tag::value),
              loadBalancers,
              securityGroupNames,
              targetGroups,
              launchConfig.instanceMonitoring.enabled,
              asg.enabledMetrics.map { Metric.valueOf(it) },
              asg.defaultCooldown.let(Duration::ofSeconds),
              asg.healthCheckGracePeriod.let(Duration::ofSeconds),
              asg.healthCheckType.let { HealthCheckType.valueOf(it) },
              launchConfig.iamInstanceProfile,
              launchConfig.keyName,
              asg.suspendedProcesses.map { ScalingProcess.valueOf(it) },
              asg.terminationPolicies.map { TerminationPolicy.valueOf(it) }
            )
          }
      }
    } catch (e: HttpException) {
      if (e.code() == NOT_FOUND.value()) {
        return null
      }
      throw e
    }
  }

  private val Cluster.securityGroupIds: Collection<String>
    get() = securityGroupNames.map {
      cloudDriverCache.securityGroupByName(accountName, region, it).id
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
