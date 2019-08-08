package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.ec2.cluster.Cluster
import com.netflix.spinnaker.keel.api.ec2.cluster.Dependencies
import com.netflix.spinnaker.keel.api.ec2.cluster.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.api.ec2.image.IdImageProvider
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCapacity
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.time.Clock
import java.util.UUID

internal class ClusterHandlerTests : JUnit5Minutests {

  val vpc = Network(CLOUD_PROVIDER, "vpc-1452353", "vpc0", "test", "us-west-2")
  val sg1 = SecurityGroupSummary("keel", "sg-325234532")
  val sg2 = SecurityGroupSummary("keel-elb", "sg-235425234")
  val subnet1 = Subnet("subnet-1", vpc.id, vpc.account, vpc.region, "${vpc.region}a", "internal (vpc0)")
  val subnet2 = Subnet("subnet-2", vpc.id, vpc.account, vpc.region, "${vpc.region}b", "internal (vpc0)")
  val subnet3 = Subnet("subnet-3", vpc.id, vpc.account, vpc.region, "${vpc.region}c", "internal (vpc0)")
  val spec = ClusterSpec(
    moniker = Moniker(app = "keel", stack = "test"),
    location = Location(
      accountName = vpc.account,
      region = vpc.region,
      availabilityZones = setOf("us-west-2a", "us-west-2b", "us-west-2c"),
      subnet = vpc.name
    ),
    launchConfiguration = LaunchConfigurationSpec(
      imageProvider = IdImageProvider(imageId = "i-123543254134"),
      instanceType = "r4.8xlarge",
      ebsOptimized = false,
      iamRole = "keelRole",
      keyPair = "keel-key-pair",
      instanceMonitoring = false
    ),
    capacity = Capacity(1, 6, 4),
    dependencies = Dependencies(
      loadBalancerNames = setOf("keel-test-frontend"),
      securityGroupNames = setOf(sg1.name, sg2.name)
    )
  )

  val cluster = Cluster(
    moniker = spec.moniker,
    location = spec.location,
    launchConfiguration = spec.launchConfiguration.generateLaunchConfiguration("i-123543254134"),
    capacity = spec.capacity,
    dependencies = spec.dependencies
  )

  val resource = Resource(
    SPINNAKER_API_V1,
    "cluster",
    mapOf(
      "name" to "my-cluster",
      "uid" to randomUID(),
      "serviceAccount" to "keel@spinnaker",
      "application" to "keel"
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
      (spec.launchConfiguration.imageProvider as IdImageProvider).imageId,
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
    spec.moniker.run { Moniker(app = app, cluster = cluster, detail = detail, stack = stack, sequence = "69") }
  )

  val cloudDriverService = mockk<CloudDriverService>()
  val cloudDriverCache = mockk<CloudDriverCache>()
  val orcaService = mockk<OrcaService>()
  val imageService = mockk<ImageService>()
  val dynamicConfigService = mockk<DynamicConfigService>()
  val objectMapper = ObjectMapper().registerKotlinModule()

  val normalizers = emptyList<ResourceNormalizer<Cluster>>()

  fun tests() = rootContext<ClusterHandler> {
    fixture {
      ClusterHandler(
        cloudDriverService,
        cloudDriverCache,
        orcaService,
        imageService,
        dynamicConfigService,
        Clock.systemDefaultZone(),
        objectMapper,
        normalizers
      )
    }

    before {
      with(cloudDriverCache) {
        every { networkBy(vpc.id) } returns vpc
        every { subnetBy(subnet1.id) } returns subnet1
        every { subnetBy(subnet2.id) } returns subnet2
        every { subnetBy(subnet3.id) } returns subnet3
        every { securityGroupById(spec.location.accountName, spec.location.region, sg1.id) } returns sg1
        every { securityGroupById(spec.location.accountName, spec.location.region, sg2.id) } returns sg2
        every { securityGroupByName(spec.location.accountName, spec.location.region, sg1.name) } returns sg1
        every { securityGroupByName(spec.location.accountName, spec.location.region, sg2.name) } returns sg2
      }

      coEvery { orcaService.orchestrate(any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
    }

    after {
      confirmVerified(orcaService)
    }

    context("the cluster does not exist or has no active server groups") {
      before {
        coEvery { cloudDriverService.activeServerGroup() } throws RETROFIT_NOT_FOUND
      }

      test("the current model is null") {
        val current = runBlocking {
          current(resource)
        }
        expectThat(current).isNull()
      }

      test("annealing a diff creates a new server group") {
        runBlocking {
          upsert(resource, ResourceDiff(cluster, null))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate(capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("createServerGroup")
        }
      }
    }

    context("the cluster has active server groups") {
      before {
        coEvery { cloudDriverService.activeServerGroup() } returns activeServerGroupResponse
      }

      derivedContext<Cluster?>("fetching the current cluster state") {
        deriveFixture {
          val current = runBlocking {
            current(resource)
          }
          current
        }

        test("the current model is converted to a cluster") {
          expectThat(this).isNotNull()
        }

        test("the cluster name is derived correctly") {
          expectThat(this).isNotNull().get { moniker }.isEqualTo(spec.moniker)
        }
      }

      context("the diff is only in capacity") {

        val modified = cluster.withDoubleCapacity()
        val diff = ResourceDiff(cluster, modified)

        test("annealing resizes the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(capture(slot)) }

          expectThat(slot.captured.job.first()) {
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

      context("the diff is something other than just capacity") {

        val modified = cluster.withDoubleCapacity().withDifferentInstanceType()
        val diff = ResourceDiff(cluster, modified)

        test("annealing clones the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(capture(slot)) }

          expectThat(slot.captured.job.first()) {
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

  private suspend fun CloudDriverService.activeServerGroup() = activeServerGroup(
    spec.moniker.app,
    spec.location.accountName,
    spec.moniker.name,
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
