package com.netflix.spinnaker.keel.ec2.asset

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.ClusterName
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
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRef
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlinx.coroutines.CompletableDeferred
import strikt.api.expectThat
import strikt.assertions.hasEntry
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.time.Clock
import java.util.*

internal object ClusterHandlerTest : JUnit5Minutests {

  val vpc = Network(CLOUD_PROVIDER, "vpc-1452353", "vpc0", "test", "us-west-2")
  val sg1 = SecurityGroupSummary("keel", "sg-325234532")
  val sg2 = SecurityGroupSummary("keel-elb", "sg-235425234")
  val spec = Cluster(
    application = "keel",
    name = ClusterName(application = "keel", stack = "test"),
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
    spec.name.toString(),
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
    Moniker(app = spec.name.application, cluster = spec.name.toString(), stack = spec.name.stack)
  )

  val cloudDriverService = mock<CloudDriverService>()
  val cloudDriverCache = mock<CloudDriverCache>()
  val orcaService = mock<OrcaService>()

  fun tests() = rootContext<ClusterHandler> {
    fixture {
      ClusterHandler(cloudDriverService, cloudDriverCache, orcaService, Clock.systemDefaultZone())
    }

    before {
      cloudDriverCache.apply {
        whenever(networkBy(vpc.id)) doReturn vpc
        whenever(securityGroupById(spec.accountName, spec.region, sg1.id)) doReturn sg1
        whenever(securityGroupById(spec.accountName, spec.region, sg2.id)) doReturn sg2
        whenever(securityGroupByName(spec.accountName, spec.region, sg1.name)) doReturn sg1
        whenever(securityGroupByName(spec.accountName, spec.region, sg2.name)) doReturn sg2
      }
    }

    after {
      reset(cloudDriverService, cloudDriverCache, orcaService)
    }

    context("the cluster does not exist or has no active server groups") {
      before {
        whenever(cloudDriverService.activeServerGroup()) doThrow RETROFIT_NOT_FOUND
      }

      test("the current model is null") {
        expectThat(current(spec, request)).isNull()
      }

      test("annealing a diff creates a new server group") {
        whenever(orcaService.orchestrate(any())) doReturn CompletableDeferred(TaskRefResponse(TaskRef("1")))

        converge(request.metadata.name, spec)

        argumentCaptor<OrchestrationRequest>().apply {
          verify(orcaService).orchestrate(capture())

          expectThat(firstValue.job.first()["type"]).isEqualTo("createServerGroup")
        }
      }
    }

    context("the cluster has active server groups") {
      before {
        whenever(cloudDriverService.activeServerGroup()) doReturn CompletableDeferred(activeServerGroupResponse)
      }

      test("the current model is converted to a cluster") {
        expectThat(current(spec, request)).isNotNull()
      }

      test("annealing a diff clones the current server group") {
        whenever(orcaService.orchestrate(any())) doReturn CompletableDeferred(TaskRefResponse(TaskRef("1")))

        converge(request.metadata.name, spec)

        argumentCaptor<OrchestrationRequest>().apply {
          verify(orcaService).orchestrate(capture())

          expectThat(firstValue.job.first()["source"])
            .isA<Map<String, Any>>()
            .and {
              hasEntry("account", activeServerGroupResponse.accountName)
              hasEntry("region", activeServerGroupResponse.region)
              hasEntry("asgName", activeServerGroupResponse.asg.autoScalingGroupName)
            }
        }
      }
    }
  }

  private fun CloudDriverService.activeServerGroup() = activeServerGroup(
    spec.application,
    spec.accountName,
    spec.name.toString(),
    spec.region,
    CLOUD_PROVIDER
  )
}
