package com.netflix.spinnaker.keel.ec2.asset

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.InstanceType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCapacity
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.ec2.resource.ClusterHandler
import com.netflix.spinnaker.keel.orca.OrcaService
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlinx.coroutines.CompletableDeferred
import strikt.api.expectThat
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.util.*

internal object ClusterHandlerTest : JUnit5Minutests {

  val vpc = Network(CLOUD_PROVIDER, "vpc-1452353", "vpc0", "test", "us-west-2")
  val sg1 = SecurityGroupSummary("keel", "sg-325234532")
  val sg2 = SecurityGroupSummary("keel-elb", "sg-235425234")
  val spec = Cluster(
    application = "keel",
    name = "keel-test",
    imageId = "i-123543254134",
    accountName = vpc.account,
    region = vpc.region,
    availabilityZones = listOf("us-west-2a", "us-west-2b", "us-west-2c"),
    vpcName = vpc.name,
    capacity = Capacity(1, 6, 4),
    instanceType = InstanceType("r4.8xlarge"),
    ebsOptimized = false,
    iamRole = "keelRole",
    keyPair = "keel-key-pair",
    loadBalancerNames = listOf("keel-test-frontend"),
    securityGroupNames = listOf(sg1.name, sg2.name),
    instanceMonitoring = false
  )
  val request = Resource(
    SPINNAKER_API_V1,
    "cluster",
    ResourceMetadata(
      name = ResourceName("my-cluster"),
      uid = UUID.randomUUID(),
      resourceVersion = 1234L
    ),
    spec
  )
  val activeServerGroupResponse = ClusterActiveServerGroup(
    spec.name,
    spec.region,
    spec.availabilityZones,
    LaunchConfig(
      spec.ramdiskId,
      spec.ebsOptimized,
      spec.imageId,
      spec.base64UserData,
      spec.instanceType.value,
      spec.keyPair,
      spec.iamRole,
      InstanceMonitoring(spec.instanceMonitoring)
    ),
    AutoScalingGroup(
      "keel-test-v069",
      spec.cooldown.seconds,
      spec.healthCheckType.let(HealthCheckType::toString),
      spec.healthCheckGracePeriod.seconds,
      spec.suspendedProcesses.map(ScalingProcess::toString),
      spec.enabledMetrics.map(Metric::toString),
      spec.tags.map { Tag(it.key, it.value) },
      spec.terminationPolicies.map(TerminationPolicy::toString)
    ),
    vpc.id,
    spec.targetGroups,
    spec.loadBalancerNames,
    spec.capacity.let { ServerGroupCapacity(it.min, it.max, it.desired) },
    listOf(sg1.id, sg2.id),
    spec.accountName,
    Moniker(app = spec.application, cluster = spec.name, stack = "test")
  )

  val cloudDriverService = mock<CloudDriverService>()
  val cloudDriverCache = mock<CloudDriverCache>()
  val orcaService = mock<OrcaService>()

  fun tests() = rootContext<ClusterHandler> {
    fixture {
      ClusterHandler(cloudDriverService, cloudDriverCache, orcaService)
    }

    before {
      cloudDriverCache.apply {
        whenever(networkBy(vpc.id)) doReturn vpc
        whenever(securityGroupSummaryBy(spec.accountName, spec.region, sg1.id)) doReturn sg1
        whenever(securityGroupSummaryBy(spec.accountName, spec.region, sg2.id)) doReturn sg2
      }
    }

    after {
      reset(cloudDriverService, cloudDriverCache, orcaService)
    }

    test("the current model is null if the cluster does not exist or has no active server groups") {
      whenever(cloudDriverService.activeServerGroup()) doThrow RETROFIT_NOT_FOUND

      expectThat(current(spec, request)).isNull()
    }

    test("the current model is converted to a cluster if it has active server groups") {
      whenever(cloudDriverService.activeServerGroup()) doReturn CompletableDeferred(activeServerGroupResponse)

      expectThat(current(spec, request)).isNotNull()
    }
  }

  private fun CloudDriverService.activeServerGroup() = activeServerGroup(
    spec.application,
    spec.accountName,
    spec.name,
    spec.region,
    CLOUD_PROVIDER
  )
}
