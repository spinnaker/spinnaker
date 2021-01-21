package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.DeployHealth.AUTO
import com.netflix.spinnaker.keel.api.DeployHealth.NONE
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Highlander
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.StaggeredRegion
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.CustomizedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.InstanceCounts
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
import com.netflix.spinnaker.keel.api.ec2.VirtualMachineImage
import com.netflix.spinnaker.keel.api.ec2.byRegion
import com.netflix.spinnaker.keel.api.ec2.resolve
import com.netflix.spinnaker.keel.api.ec2.resolveCapacity
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeploying
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.CustomizedMetricSpecificationModel
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCollection
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.OrcaTaskLauncher
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.retrofit.RETROFIT_NOT_FOUND
import com.netflix.spinnaker.keel.test.resource
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import strikt.api.Assertion
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.containsKey
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isSuccess
import strikt.assertions.isTrue
import strikt.assertions.map
import java.time.Clock
import java.time.Duration
import java.util.UUID.randomUUID
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

@Suppress("MemberVisibilityCanBePrivate")
internal class ClusterHandlerTests : JUnit5Minutests {

  val cloudDriverService = mockk<CloudDriverService>()
  val cloudDriverCache = mockk<CloudDriverCache>()
  val orcaService = mockk<OrcaService>()
  val normalizers = emptyList<Resolver<ClusterSpec>>()
  val clock = Clock.systemUTC()!!
  val publisher: EventPublisher = mockk(relaxUnitFun = true)
  val repository = mockk<KeelRepository>()
  val taskLauncher = OrcaTaskLauncher(
    orcaService,
    repository,
    publisher
  )
  val clusterExportHelper = mockk<ClusterExportHelper>(relaxed = true)
  val blockDeviceConfig = BlockDeviceConfig(VolumeDefaultConfiguration())

  val vpcWest = Network(CLOUD_PROVIDER, "vpc-1452353", "vpc0", "test", "us-west-2")
  val vpcEast = Network(CLOUD_PROVIDER, "vpc-4342589", "vpc0", "test", "us-east-1")
  val sg1West = SecurityGroupSummary("keel", "sg-325234532", "vpc-1")
  val sg2West = SecurityGroupSummary("keel-elb", "sg-235425234", "vpc-1")
  val sg1East = SecurityGroupSummary("keel", "sg-279585936", "vpc-1")
  val sg2East = SecurityGroupSummary("keel-elb", "sg-610264122", "vpc-1")
  val subnet1West = Subnet("subnet-1", vpcWest.id, vpcWest.account, vpcWest.region, "${vpcWest.region}a", "internal (vpc0)")
  val subnet2West = Subnet("subnet-2", vpcWest.id, vpcWest.account, vpcWest.region, "${vpcWest.region}b", "internal (vpc0)")
  val subnet3West = Subnet("subnet-3", vpcWest.id, vpcWest.account, vpcWest.region, "${vpcWest.region}c", "internal (vpc0)")
  val subnet1East = Subnet("subnet-1", vpcEast.id, vpcEast.account, vpcEast.region, "${vpcEast.region}c", "internal (vpc0)")
  val subnet2East = Subnet("subnet-2", vpcEast.id, vpcEast.account, vpcEast.region, "${vpcEast.region}d", "internal (vpc0)")
  val subnet3East = Subnet("subnet-3", vpcEast.id, vpcEast.account, vpcEast.region, "${vpcEast.region}e", "internal (vpc0)")

  val targetTrackingPolicyName = "keel-test-target-tracking-policy"

  val spec = clusterSpec()

  fun clusterSpec(instanceType: String = "r4.8xlarge") =
    ClusterSpec(
      moniker = Moniker(app = "keel", stack = "test"),
      locations = SubnetAwareLocations(
        account = vpcWest.account,
        vpc = "vpc0",
        subnet = subnet1West.purpose!!,
        regions = listOf(vpcWest, vpcEast).map { subnet ->
          SubnetAwareRegionSpec(
            name = subnet.region,
            availabilityZones = listOf("a", "b", "c").map { "${subnet.region}$it" }.toSet()
          )
        }.toSet()
      ),
      deployWith = RedBlack(
        stagger = listOf(
          StaggeredRegion(region = vpcWest.region, hours = "10-14", pauseTime = Duration.ofMinutes(30)),
          StaggeredRegion(region = vpcEast.region, hours = "16-02")
        )
      ),
      _defaults = ServerGroupSpec(
        launchConfiguration = LaunchConfigurationSpec(
          image = VirtualMachineImage(
            id = "ami-123543254134",
            appVersion = "keel-0.287.0-h208.fe2e8a1",
            baseImageVersion = "nflx-base-5.308.0-h1044.b4b3f78"
          ),
          instanceType = instanceType,
          ebsOptimized = false,
          iamRole = LaunchConfiguration.defaultIamRoleFor("keel"),
          keyPair = "nf-keypair-test-fake",
          instanceMonitoring = false
        ),
        capacity = Capacity(1, 6),
        scaling = Scaling(
          targetTrackingPolicies = setOf(
            TargetTrackingPolicy(
              name = targetTrackingPolicyName,
              targetValue = 560.0,
              disableScaleIn = true,
              customMetricSpec = CustomizedMetricSpecification(
                name = "RPS per instance",
                namespace = "SPIN/ACH",
                statistic = "Average"
              )
            )
          )
        ),
        dependencies = ClusterDependencies(
          loadBalancerNames = setOf("keel-test-frontend"),
          securityGroupNames = setOf(sg1West.name, sg2West.name)
        )
      )
    )

  val serverGroups = spec.resolve()
  val serverGroupEast = serverGroups.first { it.location.region == "us-east-1" }
  val serverGroupWest = serverGroups.first { it.location.region == "us-west-2" }

  val resource = resource(
    kind = EC2_CLUSTER_V1_1.kind,
    spec = spec
  )

  val activeServerGroupResponseEast = serverGroupEast.toCloudDriverResponse(vpcEast, listOf(subnet1East, subnet2East, subnet3East), listOf(sg1East, sg2East))
  val activeServerGroupResponseWest = serverGroupWest.toCloudDriverResponse(vpcWest, listOf(subnet1West, subnet2West, subnet3West), listOf(sg1West, sg2West))

  val exportable = Exportable(
    cloudProvider = "aws",
    account = spec.locations.account,
    user = "fzlem@netflix.com",
    moniker = spec.moniker,
    regions = spec.locations.regions.map { it.name }.toSet(),
    kind = EC2_CLUSTER_V1_1.kind
  )

  fun tests() = rootContext<ClusterHandler> {
    fixture {
      ClusterHandler(
        cloudDriverService,
        cloudDriverCache,
        orcaService,
        taskLauncher,
        clock,
        publisher,
        normalizers,
        clusterExportHelper,
        blockDeviceConfig
      )
    }

    before {
      with(cloudDriverCache) {
        every { defaultKeyPairForAccount("test") } returns "nf-keypair-test-{{region}}"

        every { networkBy(vpcWest.id) } returns vpcWest
        every { subnetBy(subnet1West.id) } returns subnet1West
        every { subnetBy(subnet2West.id) } returns subnet2West
        every { subnetBy(subnet3West.id) } returns subnet3West
        every { subnetBy(vpcWest.account, vpcWest.region, subnet1West.purpose!!) } returns subnet1West
        every { securityGroupById(vpcWest.account, vpcWest.region, sg1West.id) } returns sg1West
        every { securityGroupById(vpcWest.account, vpcWest.region, sg2West.id) } returns sg2West
        every { securityGroupByName(vpcWest.account, vpcWest.region, sg1West.name) } returns sg1West
        every { securityGroupByName(vpcWest.account, vpcWest.region, sg2West.name) } returns sg2West
        every { availabilityZonesBy(vpcWest.account, vpcWest.id, subnet1West.purpose!!, vpcWest.region) } returns
          setOf(subnet1West.availabilityZone)

        every { networkBy(vpcEast.id) } returns vpcEast
        every { subnetBy(subnet1East.id) } returns subnet1East
        every { subnetBy(subnet2East.id) } returns subnet2East
        every { subnetBy(subnet3East.id) } returns subnet3East
        every { subnetBy(vpcEast.account, vpcEast.region, subnet1East.purpose!!) } returns subnet1East
        every { securityGroupById(vpcEast.account, vpcEast.region, sg1East.id) } returns sg1East
        every { securityGroupById(vpcEast.account, vpcEast.region, sg2East.id) } returns sg2East
        every { securityGroupByName(vpcEast.account, vpcEast.region, sg1East.name) } returns sg1East
        every { securityGroupByName(vpcEast.account, vpcEast.region, sg2East.name) } returns sg2East
        every { availabilityZonesBy(vpcEast.account, vpcEast.id, subnet1East.purpose!!, vpcEast.region) } returns
          setOf(subnet1East.availabilityZone)
      }

      every { orcaService.orchestrate(resource.serviceAccount, any()) } returns TaskRefResponse("/tasks/${randomUUID()}")
      every { repository.environmentFor(any()) } returns Environment("test")
      every {
        clusterExportHelper.discoverDeploymentStrategy("aws", "test", "keel", any())
      } returns RedBlack()
    }

    after {
      clearAllMocks()
    }

    context("no server groups exist and the cluster doesn't exist") {
      before {
        every { cloudDriverService.activeServerGroup(any(), "us-east-1") } throws RETROFIT_NOT_FOUND
        every { cloudDriverService.activeServerGroup(any(), "us-west-2") } throws RETROFIT_NOT_FOUND
        every { cloudDriverService.listServerGroups(any(), any(), any(), any()) } throws RETROFIT_NOT_FOUND
      }

      test("the current model can be calculated correctly as no server groups") {
        val current = runBlocking {
          current(resource)
        }
        expectThat(current)
          .hasSize(0)
      }
    }

    context("the cluster does not exist or has no active server groups") {
      before {
        every { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        every { cloudDriverService.activeServerGroup(any(), "us-west-2") } throws RETROFIT_NOT_FOUND
        every { cloudDriverService.listServerGroups(any(), any(), any(), any()) } returns
          ServerGroupCollection(vpcEast.account, setOf(activeServerGroupResponseEast.toAllServerGroupsResponse()))
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

      test("annealing a staggered cluster with simple capacity doesn't attempt to upsertScalingPolicy") {
        val slot = slot<OrchestrationRequest>()
        every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

        runBlocking {
          upsert(
            resource,
            DefaultResourceDiff(
              serverGroups.map {
                it.copy(scaling = Scaling(), capacity = Capacity(2, 2, 2))
              }.byRegion(),
              emptyMap()
            )
          )
        }

        // slot will only contain the last orchestration request made, which should
        // always be for the second staggered region (east).
        expectThat(slot.captured.job.size).isEqualTo(2)
        expectThat(slot.captured.job.first()) {
          // east is waiting for west
          get("type").isEqualTo("dependsOnExecution")
        }
        expectThat(slot.captured.job[1]) {
          get("type").isEqualTo("createServerGroup")
          get("refId").isEqualTo("2")
          get("requisiteStageRefIds")
            .isA<List<String>>()
            .isEqualTo(listOf("1"))
          get("availabilityZones")
            .isA<Map<String, Set<String>>>()
            .hasSize(1)
            .containsKey("us-east-1")
          get("restrictExecutionDuringTimeWindow").isEqualTo(true)
        }
      }

      test("annealing a diff creates staggered server groups with scaling policies upserted in the same orchestration") {
        val slot = slot<OrchestrationRequest>()
        every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

        runBlocking {
          upsert(resource, DefaultResourceDiff(serverGroups.byRegion(), emptyMap()))
        }

        expectThat(slot.captured.job.size).isEqualTo(3)
        expectThat(slot.captured.job.first()) {
          // east is waiting for west
          get("type").isEqualTo("dependsOnExecution")
        }
        expectThat(slot.captured.job[1]) {
          get("type").isEqualTo("createServerGroup")
          get("refId").isEqualTo("2")
          get("requisiteStageRefIds")
            .isA<List<String>>()
            .isEqualTo(listOf("1"))
          get("availabilityZones")
            .isA<Map<String, Set<String>>>()
            .hasSize(1)
            .containsKey("us-east-1")
          get("restrictExecutionDuringTimeWindow").isEqualTo(true)
        }
        expectThat(slot.captured.job[2]) {
          get("type").isEqualTo("upsertScalingPolicy")
          get("refId").isEqualTo("3")
          get("requisiteStageRefIds")
            .isA<List<String>>()
            .isEqualTo(listOf("2"))
          get("restrictExecutionDuringTimeWindow").isNull()
        }
      }
    }

    context("the cluster has healthy active server groups") {
      before {
        every { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        every { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
        every { cloudDriverService.listServerGroups(any(), any(), any(), any()) } returns
          ServerGroupCollection(
            vpcEast.account,
            setOf(activeServerGroupResponseEast.toAllServerGroupsResponse(), activeServerGroupResponseWest.toAllServerGroupsResponse()))
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

    context("the cluster has unhealthy active server groups") {
      before {
        val instanceCounts = InstanceCounts(1, 0, 0, 1, 0, 0)
        val east = serverGroupEast.toCloudDriverResponse(
          vpc = vpcEast,
          subnets = listOf(subnet1East, subnet2East, subnet3East),
          securityGroups = listOf(sg1East, sg2East),
          instanceCounts = instanceCounts
        )
        val west = serverGroupWest.toCloudDriverResponse(
          vpc = vpcWest,
          subnets = listOf(subnet1West, subnet2West, subnet3West),
          securityGroups = listOf(sg1West, sg2West),
          instanceCounts = instanceCounts
        )
        every { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns east
        every { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns west
        every { cloudDriverService.listServerGroups(any(), any(), any(), any()) } returns
          ServerGroupCollection(
            vpcEast.account,
            setOf(east.toAllServerGroupsResponse(), west.toAllServerGroupsResponse()))
      }

      derivedContext<Map<String, ServerGroup>>("fetching the current server group state") {
        deriveFixture {
          runBlocking {
            current(resource)
          }
        }

        test("a deployed event is not fired") {
          verify(exactly = 0) { publisher.publishEvent(ofType<ArtifactVersionDeployed>()) }
        }
      }
    }

    context("the cluster has active server groups with different app versions") {
      before {
        val west = activeServerGroupResponseWest.withOlderAppVersion()
        every { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        every { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns west
        every { cloudDriverService.listServerGroups(any(), any(), any(), any()) } returns
          ServerGroupCollection(
            vpcEast.account,
            setOf(activeServerGroupResponseEast.toAllServerGroupsResponse(), west.toAllServerGroupsResponse()))

        runBlocking {
          current(resource)
        }
      }

      test("no event is fired indicating an app version is deployed") {
        verify(exactly = 0) { publisher.publishEvent(ofType<ArtifactVersionDeployed>()) }
      }
    }

    context("the cluster has active server groups with missing app version tag in one region") {
      before {
        val west = activeServerGroupResponseWest.withMissingAppVersion()
        every { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        every { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns west
        every { cloudDriverService.listServerGroups(any(), any(), any(), any()) } returns
          ServerGroupCollection(
            vpcEast.account,
            setOf(activeServerGroupResponseEast.toAllServerGroupsResponse(), west.toAllServerGroupsResponse()))
      }

      test("app version is null in the region with missing tag") {
        val current = runBlocking {
          current(resource)
        }
        expectThat(current).containsKey("us-west-2")
        expectThat(current["us-west-2"]!!.launchConfiguration.appVersion).isNull()
      }

      test("no exception is thrown") {
        expectCatching {
          current(resource)
        }.isSuccess()
      }

      test("no event is fired indicating an app version is deployed") {
        runBlocking {
          current(resource)
        }
        verify(exactly = 0) { publisher.publishEvent(ofType<ArtifactVersionDeployed>()) }
      }

      test("applying the diff creates a server group in the region with missing tag") {
        val slot = slot<OrchestrationRequest>()
        every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withMissingAppVersion()
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )
        runBlocking {
          upsert(resource, diff)
        }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("createServerGroup")
          get { get("availabilityZones") }.isA<Map<String, String>>().containsKey("us-west-2")
        }
      }
    }

    context("the cluster has too many enabled server groups in one region") {
      val east = serverGroupEast.toMultiServerGroupResponse(vpc = vpcEast, subnets = listOf(subnet1East, subnet2East, subnet3East), securityGroups = listOf(sg1East, sg2East), allEnabled = true)
      val west = serverGroupWest.toMultiServerGroupResponse(vpc = vpcWest, subnets = listOf(subnet1West, subnet2West, subnet3West), securityGroups = listOf(sg1West, sg2West))

      before {
        every { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        every { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
        every { cloudDriverService.listServerGroups(any(), any(), any(), any()) } returns
          ServerGroupCollection(vpcEast.account, east + west)
      }

      test("applying the diff creates a disable job for the oldest server group") {
        val slot = slot<OrchestrationRequest>()
        every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name, onlyEnabledServerGroup = false),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name)
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )
        runBlocking {
          upsert(resource, diff)
        }

        expectThat(slot.captured.job.first()) {
          get("type").isEqualTo("disableServerGroup")
          get("asgName").isEqualTo(east.sortedBy { it.createdTime }.first().name)
        }
      }
    }

    context("will we take action?") {
      val east = serverGroupEast.toMultiServerGroupResponse(vpc = vpcEast, subnets = listOf(subnet1East, subnet2East, subnet3East), securityGroups = listOf(sg1East, sg2East), allEnabled = true)
      val west = serverGroupWest.toMultiServerGroupResponse(vpc = vpcWest, subnets = listOf(subnet1West, subnet2West, subnet3West), securityGroups = listOf(sg1West, sg2West))


      before {
        every { cloudDriverService.listServerGroups(any(), any(), any(), any()) } returns
          ServerGroupCollection(vpcEast.account, east + west)
      }

      context("there is a diff in more than just enabled/disabled") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name, onlyEnabledServerGroup = false).withDoubleCapacity(),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )
        test("we will take action"){
          val response = runBlocking { willTakeAction(resource, diff) }
          expectThat(response.willAct).isTrue()
        }
      }

      context("there is a diff only in enabled/disabled") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name, onlyEnabledServerGroup = false),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name)
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )
        context("active server group is healthy") {
          before {
            every { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
            every { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
          }
          test("we will take action"){
            val response = runBlocking { willTakeAction(resource, diff) }
            expectThat(response.willAct).isTrue()
          }
        }

        context("active server group is not healthy") {
          before {
            every { cloudDriverService.listServerGroups(any(), any(), any(), any())} returns ServerGroupCollection(
              vpcEast.account,
              setOf(
                activeServerGroupResponseEast.toAllServerGroupsResponse(false),
                activeServerGroupResponseWest.toAllServerGroupsResponse(false)
              )
            )
            every { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns serverGroupEast.toCloudDriverResponse(vpcEast, listOf(subnet1East, subnet2East, subnet3East), listOf(sg1East, sg2East), null, InstanceCounts(1,0,1,0,0,0))
            every { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
          }
          test("we won't take action"){
            val response = runBlocking { willTakeAction(resource, diff) }
            expectThat(response.willAct).isFalse()
          }
        }
      }
    }


    context("a diff has been detected") {
      context("the diff is only in capacity") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing resizes the current server group with no stagger") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          runBlocking {
            upsert(resource, diff)
          }

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

      context("the diff is only in scaling policies missing from current") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withNoScalingPolicies()
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing only upserts scaling policies on the current server group") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          val metricSpec = serverGroupWest.scaling.targetTrackingPolicies.first().customMetricSpec!!
          runBlocking {
            upsert(resource, diff)
          }

          expectThat(slot.captured.job.size).isEqualTo(1)
          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("upsertScalingPolicy")
            get("targetTrackingConfiguration")
              .isA<Map<String, Any?>>()
              .get {
                expectThat(get("targetValue"))
                  .isA<Double>()
                  .isEqualTo(560.0)
                expectThat(get("disableScaleIn"))
                  .isA<Boolean>()
                  .isTrue()
                expectThat(get("customizedMetricSpecification"))
                  .isA<CustomizedMetricSpecificationModel>()
                  .isEqualTo(
                    CustomizedMetricSpecificationModel(
                      metricName = metricSpec.name,
                      namespace = metricSpec.namespace,
                      statistic = metricSpec.statistic
                    )
                  )
              }
          }
        }
      }

      context("the diff is only that deployed scaling policies are no longer desired") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withNoScalingPolicies()
        )
        val diff = DefaultResourceDiff(
          modified.byRegion(),
          serverGroups.byRegion()
        )

        test("annealing only deletes policies from the current server group") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          runBlocking {
            upsert(resource, diff)
          }

          expectThat(slot.captured.job.size).isEqualTo(1)
          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("deleteScalingPolicy")
            get("policyName").isEqualTo(targetTrackingPolicyName)
          }
        }
      }

      context("only an existing scaling policy has been modified") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(
            name = activeServerGroupResponseWest.name,
            scaling = serverGroupWest.scaling.copy(
              targetTrackingPolicies = setOf(
                serverGroupWest.scaling.targetTrackingPolicies
                  .first()
                  .copy(targetValue = 42.0)
              )
            )
          )
        )
        val diff = DefaultResourceDiff(
          modified.byRegion(),
          serverGroups.byRegion()
        )

        test("the modified policy is applied in two phases via one task") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          runBlocking {
            upsert(resource, diff)
          }

          expectThat(slot.captured.job.size).isEqualTo(2)
          expectThat(slot.captured.job.first()) {
            get("refId").isEqualTo("1")
            get("requisiteStageRefIds")
              .isA<List<String>>()
              .isEqualTo(emptyList())
            get("type").isEqualTo("deleteScalingPolicy")
            get("policyName").isEqualTo(targetTrackingPolicyName)
          }
          expectThat(slot.captured.job[1]) {
            get("refId").isEqualTo("2")
            get("requisiteStageRefIds")
              .isA<List<String>>()
              .isEqualTo(listOf("1"))
            get("type").isEqualTo("upsertScalingPolicy")
            get("targetTrackingConfiguration")
              .isA<Map<String, Any?>>()["targetValue"]
              .isEqualTo(42.0)
          }
        }
      }

      context("the diff is only in capacity and scaling policies") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name)
            .withDoubleCapacity()
            .withNoScalingPolicies()
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing resizes and modifies scaling policies in-place on the current server group") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          runBlocking {
            upsert(resource, diff)
          }

          expectThat(slot.captured.job.size).isEqualTo(2)
          expectThat(slot.captured.job.first()) {
            get("refId").isEqualTo("1")
            get("type").isEqualTo("resizeServerGroup")
          }
          expectThat(slot.captured.job[1]) {
            get("refId").isEqualTo("2")
            get("type").isEqualTo("upsertScalingPolicy")
            get("targetTrackingConfiguration")
              .isA<Map<String, Any?>>()["targetValue"]
              .isEqualTo(560.0)
          }
        }
      }

      context("the diff is something other than just capacity or scaling policies") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name)
            .withDoubleCapacity()
            .withDifferentInstanceType()
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("an artifact deploying event fires when upserting the cluster") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          runBlocking {
            upsert(resource, diff)
          }

          verify { publisher.publishEvent(ArtifactVersionDeploying(resource.id, "keel-0.287.0-h208.fe2e8a1")) }
        }

        test("annealing clones the current server group") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          runBlocking {
            upsert(resource, diff)
          }

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

        test("the default deploy strategy is used") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          val deployWith = RedBlack()
          runBlocking {
            upsert(resource, diff)
          }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("redblack")
            get("delayBeforeDisableSec").isEqualTo(deployWith.delayBeforeDisable?.seconds)
            get("delayBeforeScaleDownSec").isEqualTo(deployWith.delayBeforeScaleDown?.seconds)
            get("rollback").isA<Map<String, Any?>>().get("onFailure").isEqualTo(deployWith.rollbackOnFailure)
            get("scaleDown").isEqualTo(deployWith.resizePreviousToZero)
            get("maxRemainingAsgs").isEqualTo(deployWith.maxServerGroups)
          }
        }

        test("the deploy strategy is configured") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          val deployWith = RedBlack(
            resizePreviousToZero = true,
            delayBeforeDisable = Duration.ofMinutes(1),
            delayBeforeScaleDown = Duration.ofMinutes(5),
            maxServerGroups = 3
          )
          runBlocking {
            upsert(resource.copy(spec = resource.spec.copy(deployWith = deployWith)), diff)
          }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("redblack")
            get("delayBeforeDisableSec").isEqualTo(deployWith.delayBeforeDisable?.seconds)
            get("delayBeforeScaleDownSec").isEqualTo(deployWith.delayBeforeScaleDown?.seconds)
            get("rollback").isA<Map<String, Any?>>().get("onFailure").isEqualTo(deployWith.rollbackOnFailure)
            get("scaleDown").isEqualTo(deployWith.resizePreviousToZero)
            get("maxRemainingAsgs").isEqualTo(deployWith.maxServerGroups)
          }
        }

        test("the cluster does not use discovery-based health during deployment") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          val deployWith = RedBlack(health = NONE)
          runBlocking {
            upsert(resource.copy(spec = resource.spec.copy(deployWith = deployWith)), diff)
          }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("redblack")
            get("interestingHealthProviderNames").isA<List<String>>().containsExactly("Amazon")
          }
        }

        test("the cluster uses discovery-based health during deployment") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          val deployWith = RedBlack(health = AUTO)
          runBlocking {
            upsert(resource.copy(spec = resource.spec.copy(deployWith = deployWith)), diff)
          }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("redblack")
            get("interestingHealthProviderNames").isNull()
          }
        }

        test("a different deploy strategy is used") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          runBlocking {
            upsert(resource.copy(spec = resource.spec.copy(deployWith = Highlander())), diff)
          }

          expectThat(slot.captured.job.first()) {
            get("strategy").isEqualTo("highlander")
            not().containsKey("delayBeforeDisableSec")
            not().containsKey("delayBeforeScaleDownSec")
            not().containsKey("rollback")
            not().containsKey("scaleDown")
            not().containsKey("maxRemainingAsgs")
          }
        }
      }

      context("multiple server groups have a diff") {
        val modified = setOf(
          serverGroupEast.copy(name = activeServerGroupResponseEast.name).withDifferentInstanceType(),
          serverGroupWest.copy(name = activeServerGroupResponseWest.name).withDoubleCapacity()
        )
        val diff = DefaultResourceDiff(
          serverGroups.byRegion(),
          modified.byRegion()
        )

        test("annealing launches one task per server group") {
          val tasks = mutableListOf<OrchestrationRequest>()
          every { orcaService.orchestrate(any(), capture(tasks)) } answers { TaskRefResponse(ULID().nextULID()) }

          runBlocking {
            upsert(resource, diff)
          }

          expectThat(tasks)
            .hasSize(2)
            .map { it.job.first()["type"] }
            .containsExactlyInAnyOrder("createServerGroup", "resizeServerGroup")
        }

        test("each task has a distinct correlation id") {
          val tasks = mutableListOf<OrchestrationRequest>()
          every { orcaService.orchestrate(any(), capture(tasks)) } answers { TaskRefResponse(ULID().nextULID()) }

          runBlocking {
            upsert(resource, diff)
          }

          expectThat(tasks)
            .hasSize(2)
            .map { it.trigger.correlationId }
            .containsDistinctElements()
        }
      }

      context("nothing currently deployed, desired state is single region deployment") {
        fun diff(instanceType: String) =
          DefaultResourceDiff(
              desired=clusterSpec(instanceType).resolve().filter {it.location.region == "us-west-2"}.byRegion(),
              current=emptyMap()
            )

        test("supported instance type for setting EBS volume type") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          val instanceType = "m5.large"
          runBlocking {
            upsert(resource, diff(instanceType))
          }

          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("createServerGroup")
            get("blockDevices")
              .isNotNull()
              .isA<List<Map<String, Any>>>()
              .hasSize(1)
              .all {
                get("volumeType").isEqualTo("gp2")
                get("size").isEqualTo(40)
              }
          }
        }

        test("unsupported instance type for setting EBS volume type") {
          val slot = slot<OrchestrationRequest>()
          every { orcaService.orchestrate(resource.serviceAccount, capture(slot)) } answers { TaskRefResponse(ULID().nextULID()) }

          val instanceType = "c1.medium"
          runBlocking {
            upsert(resource, diff(instanceType))
          }

          expectThat(slot.captured.job.first()) {
            get("type").isEqualTo("createServerGroup")
            get("blockDevices").isNull()
          }
        }
      }
    }
  }

  private suspend fun CloudDriverService.activeServerGroup(user: String, region: String) = activeServerGroup(
    user = user,
    app = spec.moniker.app,
    account = spec.locations.account,
    cluster = spec.moniker.toString(),
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
      desired = when (capacity.desired) {
        null -> null
        else -> capacity.desired!! * 2
      }
    )
  )

private fun ServerGroup.withNoScalingPolicies(): ServerGroup =
  copy(scaling = Scaling(), capacity = capacity.copy(desired = capacity.max))

private fun ServerGroup.withDifferentInstanceType(): ServerGroup =
  copy(
    launchConfiguration = launchConfiguration.copy(
      instanceType = "r4.16xlarge"
    )
  )

private fun ServerGroup.withMissingAppVersion(): ServerGroup =
  copy(
    launchConfiguration = launchConfiguration.copy(
      appVersion = null
    )
  )

private fun ActiveServerGroup.withOlderAppVersion(): ActiveServerGroup =
  copy(
    image = image.copy(
      imageId = "ami-573e1b2650a5",
      appVersion = "keel-0.251.0-h167.9ea0465"
    ),
    launchConfig = launchConfig?.copy(
      imageId = "ami-573e1b2650a5"
    )
  )

private fun ActiveServerGroup.withMissingAppVersion(): ActiveServerGroup =
  copy(
    image = image.copy(
      imageId = "ami-573e1b2650a5",
      appVersion = null
    )
  )
