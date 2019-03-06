package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.Cluster.Location
import com.netflix.spinnaker.keel.api.ec2.Cluster.Moniker
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCapacity
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import de.danielbechler.diff.ObjectDifferBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlinx.coroutines.CompletableDeferred
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.time.Clock
import java.util.*
import com.netflix.spinnaker.keel.clouddriver.model.Moniker as CloudDriverMoniker

internal object ClusterHandlerTests : JUnit5Minutests {

  val vpc = Network(CLOUD_PROVIDER, "vpc-1452353", "vpc0", "test", "us-west-2")
  val sg1 = SecurityGroupSummary("keel", "sg-325234532")
  val sg2 = SecurityGroupSummary("keel-elb", "sg-235425234")
  val subnet1 = Subnet("subnet-1", vpc.id, vpc.account, vpc.region, "${vpc.region}a", "internal (vpc0)")
  val subnet2 = Subnet("subnet-2", vpc.id, vpc.account, vpc.region, "${vpc.region}b", "internal (vpc0)")
  val subnet3 = Subnet("subnet-3", vpc.id, vpc.account, vpc.region, "${vpc.region}c", "internal (vpc0)")
  val spec = Cluster(
    moniker = Moniker("keel", "test"),
    location = Location(
      accountName = vpc.account,
      region = vpc.region,
      availabilityZones = setOf("us-west-2a", "us-west-2b", "us-west-2c"),
      subnet = vpc.name
    ),
    launchConfiguration = Cluster.LaunchConfiguration(
      imageId = "i-123543254134",
      instanceType = "r4.8xlarge",
      ebsOptimized = false,
      iamRole = "keelRole",
      keyPair = "keel-key-pair",
      instanceMonitoring = false
    ),
    capacity = Capacity(1, 6, 4),
    dependencies = Cluster.Dependencies(
      loadBalancerNames = setOf("keel-test-frontend"),
      securityGroupNames = setOf(sg1.name, sg2.name)
    )
  )
  val resource = Resource(
    SPINNAKER_API_V1,
    "cluster",
    ResourceMetadata(
      name = ResourceName("my-cluster"),
      uid = randomUID(),
      resourceVersion = 1234L
    ),
    spec
  )
  val activeServerGroupResponse = ClusterActiveServerGroup(
    "keel-test-v069",
    spec.location.region,
    spec.location.availabilityZones,
    LaunchConfig(
      spec.launchConfiguration.ramdiskId,
      spec.launchConfiguration.ebsOptimized,
      spec.launchConfiguration.imageId,
      spec.launchConfiguration.instanceType,
      spec.launchConfiguration.keyPair,
      spec.launchConfiguration.iamRole,
      InstanceMonitoring(spec.launchConfiguration.instanceMonitoring)
    ),
    AutoScalingGroup(
      "keel-test-v069",
      spec.health.cooldown.seconds,
      spec.health.healthCheckType.let(HealthCheckType::toString),
      spec.health.warmup.seconds,
      spec.scaling.suspendedProcesses.map(ScalingProcess::toString).toSet(),
      spec.health.enabledMetrics.map(Metric::toString).toSet(),
      spec.tags.map { Tag(it.key, it.value) }.toSet(),
      spec.health.terminationPolicies.map(TerminationPolicy::toString).toSet(),
      listOf(subnet1, subnet2, subnet3).map(Subnet::id).joinToString(",")
    ),
    vpc.id,
    spec.dependencies.targetGroups,
    spec.dependencies.loadBalancerNames,
    spec.capacity.let { ServerGroupCapacity(it.min, it.max, it.desired) },
    CLOUD_PROVIDER,
    setOf(sg1.id, sg2.id),
    spec.location.accountName,
    spec.moniker.run { CloudDriverMoniker(application, cluster, detail, stack, "69") }
  )

  val cloudDriverService = mock<CloudDriverService>()
  val cloudDriverCache = mock<CloudDriverCache>()
  val orcaService = mock<OrcaService>()
  val objectMapper = ObjectMapper().registerKotlinModule()

  val differ = ObjectDifferBuilder.buildDefault()

  fun tests() = rootContext<ClusterHandler> {
    fixture {
      ClusterHandler(cloudDriverService, cloudDriverCache, orcaService, Clock.systemDefaultZone(), objectMapper)
    }

    before {
      cloudDriverCache.apply {
        whenever(networkBy(vpc.id)) doReturn vpc
        whenever(subnetBy(subnet1.id)) doReturn subnet1
        whenever(subnetBy(subnet2.id)) doReturn subnet2
        whenever(subnetBy(subnet3.id)) doReturn subnet3
        whenever(securityGroupById(spec.location.accountName, spec.location.region, sg1.id)) doReturn sg1
        whenever(securityGroupById(spec.location.accountName, spec.location.region, sg2.id)) doReturn sg2
        whenever(securityGroupByName(spec.location.accountName, spec.location.region, sg1.name)) doReturn sg1
        whenever(securityGroupByName(spec.location.accountName, spec.location.region, sg2.name)) doReturn sg2

        whenever(orcaService.orchestrate(any())) doReturn CompletableDeferred(TaskRefResponse("/tasks/${UUID.randomUUID()}"))
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
        expectThat(current(resource)).isNull()
      }

      test("annealing a diff creates a new server group") {
        upsert(resource)

        argumentCaptor<OrchestrationRequest>().apply {
          verify(orcaService).orchestrate(capture())

          expectThat(firstValue.job.first()) {
            get("type").isEqualTo("createServerGroup")
          }
        }
      }
    }

    context("the cluster has active server groups") {
      before {
        whenever(cloudDriverService.activeServerGroup()) doReturn CompletableDeferred(activeServerGroupResponse)
      }

      derivedContext<Cluster?>("fetching the current cluster state") {
        deriveFixture {
          current(resource)
        }

        test("the current model is converted to a cluster") {
          expectThat(this).isNotNull()
        }

        test("the cluster name is derived correctly") {
          expectThat(this).isNotNull().get { moniker }.isEqualTo(spec.moniker)
        }
      }

      context("the diff is only in capacity") {

        val diff = differ.compare(resource.spec.withDoubleCapacity(), resource.spec)

        test("annealing resizes the current server group") {
          upsert(resource, diff)

          argumentCaptor<OrchestrationRequest>().apply {
            verify(orcaService).orchestrate(capture())

            expectThat(firstValue.job.first()) {
              get("type").isEqualTo("resizeServerGroup")
              get("capacity").isEqualTo(
                mapOf(
                  "min" to spec.capacity.min,
                  "max" to spec.capacity.max,
                  "desired" to spec.capacity.desired
                )
              )
              get("serverGroupName").isEqualTo(activeServerGroupResponse.asg.autoScalingGroupName)
            }
          }
        }
      }

      context("the diff is something other than just capacity") {

        val diff = differ.compare(resource.spec.withDoubleCapacity().withDifferentInstanceType(), resource.spec)

        test("annealing clones the current server group") {
          upsert(resource, diff)

          argumentCaptor<OrchestrationRequest>().apply {
            verify(orcaService).orchestrate(capture())

            expectThat(firstValue.job.first()) {
              get("type").isEqualTo("createServerGroup")
              get("source").isEqualTo(
                mapOf(
                  "account" to activeServerGroupResponse.accountName,
                  "region" to activeServerGroupResponse.region,
                  "asgName" to activeServerGroupResponse.asg.autoScalingGroupName
                )
              )
            }
          }
        }
      }
    }
  }

  private fun CloudDriverService.activeServerGroup() = activeServerGroup(
    spec.moniker.application,
    spec.location.accountName,
    spec.moniker.cluster,
    spec.location.region,
    CLOUD_PROVIDER
  )


}

private fun Cluster.withDoubleCapacity(): Cluster =
  copy(
    capacity = Capacity(
      min = capacity.min * 2,
      max = capacity.max * 2,
      desired = capacity.desired * 2
    )
  )

private fun Cluster.withDifferentInstanceType(): Cluster =
  copy(
    launchConfiguration = launchConfiguration.copy(
      instanceType = "r4.16xlarge"
    )
  )
