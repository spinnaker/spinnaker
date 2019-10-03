package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ClusterRegion
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.Locations
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.Dependencies
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.IdImageProvider
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.ec2.byRegion
import com.netflix.spinnaker.keel.api.ec2.resolve
import com.netflix.spinnaker.keel.api.ec2.resolveCapacity
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
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
import com.netflix.spinnaker.keel.ec2.image.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.parseMoniker
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils.randomNumeric
import org.springframework.context.ApplicationEventPublisher
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.containsKey
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.map
import java.time.Clock
import java.util.UUID.randomUUID

internal class ClusterHandlerTests : JUnit5Minutests {

  val cloudDriverService = mockk<CloudDriverService>()
  val cloudDriverCache = mockk<CloudDriverCache>()
  val orcaService = mockk<OrcaService>()
  val imageResolver = mockk<ImageResolver>()
  val objectMapper = ObjectMapper().registerKotlinModule()
  val normalizers = emptyList<ResourceNormalizer<ClusterSpec>>()
  val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  val clock = Clock.systemDefaultZone()
  val environmentResolver: EnvironmentResolver = EnvironmentResolver(InMemoryDeliveryConfigRepository(clock))

  val vpcWest = Network(CLOUD_PROVIDER, "vpc-1452353", "vpc0", "test", "us-west-2")
  val vpcEast = Network(CLOUD_PROVIDER, "vpc-4342589", "vpc0", "test", "us-east-1")
  val sg1West = SecurityGroupSummary("keel", "sg-325234532")
  val sg2West = SecurityGroupSummary("keel-elb", "sg-235425234")
  val sg1East = SecurityGroupSummary("keel", "sg-279585936")
  val sg2East = SecurityGroupSummary("keel-elb", "sg-610264122")
  val subnet1West = Subnet("subnet-1", vpcWest.id, vpcWest.account, vpcWest.region, "${vpcWest.region}a", "internal (vpc0)")
  val subnet2West = Subnet("subnet-2", vpcWest.id, vpcWest.account, vpcWest.region, "${vpcWest.region}b", "internal (vpc0)")
  val subnet3West = Subnet("subnet-3", vpcWest.id, vpcWest.account, vpcWest.region, "${vpcWest.region}c", "internal (vpc0)")
  val subnet1East = Subnet("subnet-1", vpcEast.id, vpcEast.account, vpcEast.region, "${vpcEast.region}c", "internal (vpc0)")
  val subnet2East = Subnet("subnet-2", vpcEast.id, vpcEast.account, vpcEast.region, "${vpcEast.region}d", "internal (vpc0)")
  val subnet3East = Subnet("subnet-3", vpcEast.id, vpcEast.account, vpcEast.region, "${vpcEast.region}e", "internal (vpc0)")

  val spec = ClusterSpec(
    moniker = Moniker(app = "keel", stack = "test"),
    imageProvider = IdImageProvider(imageId = "ami-123543254134"),
    locations = Locations(
      accountName = vpcWest.account,
      regions = listOf(vpcWest, vpcEast).map { subnet ->
        ClusterRegion(
          region = subnet.region,
          subnet = subnet.name!!,
          availabilityZones = listOf("a", "b", "c").map { "${subnet.region}$it" }.toSet()
        )
      }.toSet()
    ),
    _defaults = ServerGroupSpec(
      launchConfiguration = LaunchConfigurationSpec(
        instanceType = "r4.8xlarge",
        ebsOptimized = false,
        iamRole = "keelRole",
        keyPair = "keel-key-pair",
        instanceMonitoring = false
      ),
      capacity = Capacity(1, 6, 4),
      dependencies = Dependencies(
        loadBalancerNames = setOf("keel-test-frontend"),
        securityGroupNames = setOf(sg1West.name, sg2West.name)
      )
    )
  )

  val serverGroups = spec.resolve(
    // TODO: make this rely on the imageResolver mock?
    ResolvedImages(
      "keel-0.252.0-h168.35fe253",
      listOf(vpcWest, vpcEast).associate { subnet ->
        subnet.region to "i-123543254134"
      }
    )
  )
  val serverGroupEast = serverGroups.first { it.location.region == "us-east-1" }
  val serverGroupWest = serverGroups.first { it.location.region == "us-west-2" }

  val resource = resource(
    apiVersion = SPINNAKER_API_V1,
    kind = "cluster",
    spec = spec
  )

  val activeServerGroupResponseEast = serverGroupEast.toCloudDriverResponse(vpcEast, listOf(subnet1East, subnet2East, subnet3East), listOf(sg1East, sg2East))
  val activeServerGroupResponseWest = serverGroupWest.toCloudDriverResponse(vpcWest, listOf(subnet1West, subnet2West, subnet3West), listOf(sg1West, sg2West))

  private fun ServerGroup.toCloudDriverResponse(
    vpc: Network,
    subnets: List<Subnet>,
    securityGroups: List<SecurityGroupSummary>
  ): ActiveServerGroup =
    randomNumeric(3).padStart(3, '0').let { sequence ->
      ActiveServerGroup(
        "$name-v$sequence",
        location.region,
        location.availabilityZones,
        ActiveServerGroupImage(
          launchConfiguration.imageId,
          launchConfiguration.appVersion
        ),
        LaunchConfig(
          launchConfiguration.ramdiskId,
          launchConfiguration.ebsOptimized,
          launchConfiguration.imageId,
          launchConfiguration.instanceType,
          launchConfiguration.keyPair,
          launchConfiguration.iamRole,
          InstanceMonitoring(launchConfiguration.instanceMonitoring)
        ),
        AutoScalingGroup(
          "$name-v$sequence",
          health.cooldown.seconds,
          health.healthCheckType.let(HealthCheckType::toString),
          health.warmup.seconds,
          scaling.suspendedProcesses.map(ScalingProcess::toString).toSet(),
          health.enabledMetrics.map(Metric::toString).toSet(),
          tags.map { Tag(it.key, it.value) }.toSet(),
          health.terminationPolicies.map(TerminationPolicy::toString).toSet(),
          subnets.map(Subnet::id).joinToString(",")
        ),
        vpc.id,
        dependencies.targetGroups,
        dependencies.loadBalancerNames,
        capacity.let { ServerGroupCapacity(it.min, it.max, it.desired) },
        CLOUD_PROVIDER,
        securityGroups.map(SecurityGroupSummary::id).toSet(),
        location.accountName,
        parseMoniker("$name-v$sequence")
      )
    }

  fun tests() = rootContext<ClusterHandler> {
    fixture {
      ClusterHandler(
        cloudDriverService,
        cloudDriverCache,
        orcaService,
        imageResolver,
        environmentResolver,
        clock,
        publisher,
        objectMapper,
        normalizers
      )
    }

    before {
      with(cloudDriverCache) {
        every { networkBy(vpcWest.id) } returns vpcWest
        every { subnetBy(subnet1West.id) } returns subnet1West
        every { subnetBy(subnet2West.id) } returns subnet2West
        every { subnetBy(subnet3West.id) } returns subnet3West
        every { securityGroupById(vpcWest.account, vpcWest.region, sg1West.id) } returns sg1West
        every { securityGroupById(vpcWest.account, vpcWest.region, sg2West.id) } returns sg2West
        every { securityGroupByName(vpcWest.account, vpcWest.region, sg1West.name) } returns sg1West
        every { securityGroupByName(vpcWest.account, vpcWest.region, sg2West.name) } returns sg2West

        every { networkBy(vpcEast.id) } returns vpcEast
        every { subnetBy(subnet1East.id) } returns subnet1East
        every { subnetBy(subnet2East.id) } returns subnet2East
        every { subnetBy(subnet3East.id) } returns subnet3East
        every { securityGroupById(vpcEast.account, vpcEast.region, sg1East.id) } returns sg1East
        every { securityGroupById(vpcEast.account, vpcEast.region, sg2East.id) } returns sg2East
        every { securityGroupByName(vpcEast.account, vpcEast.region, sg1East.name) } returns sg1East
        every { securityGroupByName(vpcEast.account, vpcEast.region, sg2East.name) } returns sg2East
      }

      coEvery { orcaService.orchestrate("keel@spinnaker", any()) } returns TaskRefResponse("/tasks/${randomUUID()}")
    }

    after {
      confirmVerified(orcaService)
      clearAllMocks()
    }

    context("the cluster does not exist or has no active server groups") {
      before {
        coEvery { cloudDriverService.activeServerGroup("us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup("us-west-2") } throws RETROFIT_NOT_FOUND
      }

      test("the current model is null") {
        val current = runBlocking {
          current(resource)
        }
        expectThat(current)
          .hasSize(1)
          .not()
          .containsKey("us-west-2")
      }

      test("annealing a diff creates a new server group") {
        runBlocking {
          upsert(resource, ResourceDiff(serverGroups.byRegion(), emptyMap()))
        }

        val slot = slot<OrchestrationRequest>()
        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("createServerGroup")
        }
      }
    }

    context("the cluster has active server groups") {
      before {
        coEvery { cloudDriverService.activeServerGroup("us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup("us-west-2") } returns activeServerGroupResponseWest
      }

      // TODO: test for multiple server group response
      derivedContext<Map<String, ServerGroup>>("fetching the current server group state") {
        deriveFixture {
          runBlocking {
            current(resource)
          }
        }

        test("the current model is converted to a set of server group") {
          expectThat(this).isNotEmpty()
        }

        test("the server group name is derived correctly") {
          expectThat(values)
            .map { it.name }
            .containsExactlyInAnyOrder(
              activeServerGroupResponseEast.name,
              activeServerGroupResponseWest.name
            )
        }

        test("an event is fired if all server groups have the same artifact version") {
          verify { publisher.publishEvent(ofType<ArtifactVersionDeployed>()) }
        }
      }
    }

    context("the cluster has active server groups with different app versions") {
      before {
        coEvery { cloudDriverService.activeServerGroup("us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup("us-west-2") } returns activeServerGroupResponseWest.withOlderAppVersion()

        runBlocking {
          current(resource)
        }
      }

      test("no event is fired indicating an app version is deployed") {
        verify(exactly = 0) { publisher.publishEvent(ofType<ArtifactVersionDeployed>()) }
      }
    }

    context("a diff has been detected") {
      context("the diff is only in capacity") {

        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing resizes the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("resizeServerGroup")
            get("capacity").isEqualTo(
              spec.resolveCapacity("us-west-2").let {
                mapOf(
                  "min" to it.min,
                  "max" to it.max,
                  "desired" to it.desired
                )
              }
            )
            get("serverGroupName").isEqualTo(activeServerGroupResponseWest.asg.autoScalingGroupName)
          }
        }
      }

      context("the diff is something other than just capacity") {

        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity().withDifferentInstanceType()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing clones the current server group") {
          runBlocking {
            upsert(resource, diff)
          }

          val slot = slot<OrchestrationRequest>()
          coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("createServerGroup")
            get("source").isEqualTo(
              mapOf(
                "account" to activeServerGroupResponseWest.accountName,
                "region" to activeServerGroupResponseWest.region,
                "asgName" to activeServerGroupResponseWest.asg.autoScalingGroupName
              )
            )
          }
        }
      }

      context("multiple server groups have a diff") {

        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name).withDifferentInstanceType(),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = ResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        before {
          runBlocking {
            upsert(resource, diff)
          }
        }

        test("annealing launches one task per server group") {
          val tasks = mutableListOf<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(any(), capture(tasks)) }

          expectThat(tasks)
            .hasSize(2)
            .map { it.job.first()["type"] }
            .containsExactlyInAnyOrder("createServerGroup", "resizeServerGroup")
        }

        test("each task has a distinct correlation id") {
          val tasks = mutableListOf<OrchestrationRequest>()
          coVerify { orcaService.orchestrate(any(), capture(tasks)) }

          expectThat(tasks)
            .hasSize(2)
            .map { it.trigger.correlationId }
            .containsDistinctElements()
        }
      }
    }
  }

  private suspend fun CloudDriverService.activeServerGroup(region: String) = activeServerGroup(
    serviceAccount = "keel@spinnaker",
    app = spec.moniker.app,
    account = spec.locations.accountName,
    cluster = spec.moniker.name,
    region = region,
    cloudProvider = CLOUD_PROVIDER
  )
}

private fun <E, T : Iterable<E>> Assertion.Builder<T>.containsDistinctElements() =
  assert("contains distinct elements") { subject ->
    val duplicates = subject
      .associateWith { elem -> subject.count { it == elem } }
      .filterValues { it > 1 }
      .keys
    when (duplicates.size) {
      0 -> pass()
      1 -> fail(duplicates.first(), "The element %s occurs more than once")
      else -> fail(duplicates, "The elements %s occur more than once")
    }
  }

private fun ServerGroup.withDoubleCapacity(): ServerGroup =
  copy(
    capacity = Capacity(
      min = capacity.min * 2,
      max = capacity.max * 2,
      desired = capacity.desired * 2
    )
  )

private fun ServerGroup.withDifferentInstanceType(): ServerGroup =
  copy(
    launchConfiguration = launchConfiguration.copy(
      instanceType = "r4.16xlarge"
    )
  )

private fun ActiveServerGroup.withOlderAppVersion(): ActiveServerGroup =
  copy(
    image = image.copy(
      imageId = "ami-573e1b2650a5",
      appVersion = "keel-0.251.0-h167.9ea0465"
    ),
    launchConfig = launchConfig.copy(
      imageId = "ami-573e1b2650a5"
    )
  )
