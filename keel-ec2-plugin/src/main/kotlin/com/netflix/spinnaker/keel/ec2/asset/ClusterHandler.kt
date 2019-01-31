package com.netflix.spinnaker.keel.ec2.asset

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.InstanceType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.ec2.AmazonAssetHandler
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.orca.OrcaService
import org.springframework.http.HttpStatus.NOT_FOUND
import retrofit.RetrofitError
import java.time.Duration

class ClusterHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService
) : AmazonAssetHandler<Cluster> {
  override fun current(spec: Cluster, request: Asset<Cluster>): Cluster? =
    cloudDriverService.getCluster(spec)

  override fun converge(assetName: AssetName, spec: Cluster) {
    TODO("not implemented")
  }

  override fun delete(assetName: AssetName, spec: SecurityGroup) {
    TODO("not implemented")
  }

  private fun CloudDriverService.getCluster(spec: Cluster): Cluster? {
    try {
      return activeServerGroup(spec.application, spec.accountName, spec.name, spec.region, CLOUD_PROVIDER)
        .let { response ->
          Cluster(
            response.moniker.app,
            response.moniker.cluster!!,
            response.launchConfig.imageId,
            response.accountName,
            response.region,
            response.zones,
            response.vpcId.let { cloudDriverCache.networkBy(it).name },
            response.capacity.let { Capacity(it.min, it.max, it.desired) },
            InstanceType(response.launchConfig.instanceType),
            response.launchConfig.ebsOptimized,
            response.launchConfig.ramdiskId,
            response.launchConfig.userData,
            response.asg.tags.associateBy(Tag::key, Tag::value),
            response.loadBalancers,
            response.let { asg -> asg.securityGroups.map { cloudDriverCache.securityGroupSummaryBy(response.accountName, asg.region, it).name } },
            response.targetGroups,
            response.launchConfig.instanceMonitoring.enabled,
            response.asg.enabledMetrics.map { Metric.valueOf(it) },
            Duration.ofSeconds(response.asg.defaultCooldown),
            Duration.ofSeconds(response.asg.healthCheckGracePeriod),
            HealthCheckType.valueOf(response.asg.healthCheckType),
            response.launchConfig.iamInstanceProfile,
            response.launchConfig.keyName,
            response.asg.suspendedProcesses.map { ScalingProcess.valueOf(it) },
            response.asg.terminationPolicies.map { TerminationPolicy.valueOf(it) }
          )
        }
    } catch (e: RetrofitError) {
      if (e.response.status == NOT_FOUND.value()) {
        return null
      }
      throw e
    }
  }
}
