package com.netflix.spinnaker.keel.api.ec2.export

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.VirtualMachineImage
import com.netflix.spinnaker.keel.api.ec2.CustomizedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.LaunchConfiguration.Companion.defaultIamRoleFor
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.ec2.resolve
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.core.api.Capacity
import com.netflix.spinnaker.keel.core.api.ClusterDependencies
import com.netflix.spinnaker.keel.core.api.RedBlack
import com.netflix.spinnaker.keel.core.api.StaggeredRegion
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.ec2.resource.ClusterHandler
import com.netflix.spinnaker.keel.ec2.resource.toCloudDriverResponse
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.OrcaTaskLauncher
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.test.combinedMockRepository
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.isNull

internal class ClusterExportTests : JUnit5Minutests {

  val cloudDriverService = mockk<CloudDriverService>()
  val cloudDriverCache = mockk<CloudDriverCache>()
  val orcaService = mockk<OrcaService>()
  val normalizers = emptyList<Resolver<ClusterSpec>>()
  val clock = Clock.systemUTC()
  val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  val deliveryConfigRepository: InMemoryDeliveryConfigRepository = mockk()
  val combinedRepository = combinedMockRepository(deliveryConfigRepository = deliveryConfigRepository)
  val taskLauncher = OrcaTaskLauncher(
    orcaService,
    combinedRepository,
    publisher
  )

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

  val spec = ClusterSpec(
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
        instanceType = "r4.8xlarge",
        ebsOptimized = false,
        iamRole = defaultIamRoleFor("keel"),
        keyPair = "nf-keypair-test-fake",
        instanceMonitoring = false
      ),
      capacity = Capacity(min = 1, max = 6),
      scaling = Scaling(
        targetTrackingPolicies = setOf(TargetTrackingPolicy(
          name = targetTrackingPolicyName,
          targetValue = 560.0,
          disableScaleIn = true,
          customMetricSpec = CustomizedMetricSpecification(
            name = "RPS per instance",
            namespace = "SPIN/ACH",
            statistic = "Average"
          )
        ))
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
    kind = SPINNAKER_EC2_API_V1.qualify("cluster"),
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
    kind = SPINNAKER_EC2_API_V1.qualify("cluster")
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
        normalizers
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

      coEvery { orcaService.orchestrate(resource.serviceAccount, any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
      every { deliveryConfigRepository.environmentFor(any()) } returns Environment("test")
    }

    after {
      clearAllMocks()
    }

    context("exporting same clusters different regions") {
      before {
        coEvery { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
      }

      test("no overrides") {
        val cluster = runBlocking {
          export(exportable)
        }
        expectThat(cluster) {
          get { locations.regions }.hasSize(2)
          get { overrides }.hasSize(0)
          get { defaults.scaling!!.targetTrackingPolicies }.hasSize(1)
          get { defaults.health }.isNull()
          get { deployWith }.isA<RedBlack>()
        }
      }
    }

    context("exporting clusters with capacity difference between regions") {
      before {
        coEvery { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
          .withDifferentSize()
      }

      test("override only in capacity") {
        val cluster = runBlocking {
          export(exportable)
        }
        expectThat(cluster) {
          get { locations.regions }.hasSize(2)
          get { overrides }.hasSize(1)
          get { overrides }.get { "us-east-1" }.get { "capacity" }.isNotEmpty()
          get { defaults.scaling!!.targetTrackingPolicies }.hasSize(1)
          get { spec.defaults.health }.isNull()
        }
      }
    }

    context("exporting clusters that are significantly different between regions") {
      before {
        coEvery { cloudDriverService.activeServerGroup(any(), "us-east-1") } returns activeServerGroupResponseEast
        coEvery { cloudDriverService.activeServerGroup(any(), "us-west-2") } returns activeServerGroupResponseWest
          .withNonDefaultHealthProps()
          .withNonDefaultLaunchConfigProps()
      }
      test("export omits properties with default values from complex fields") {
        val exported = runBlocking {
          export(exportable)
        }
        expect {
          that(exported.locations.vpc).isNull()
          that(exported.locations.subnet).isNull()
          that(exported.defaults.health).isNotNull()

          that(exported.defaults.health!!) {
            get { cooldown }.isNull()
            get { warmup }.isNull()
            get { healthCheckType }.isNull()
            get { enabledMetrics }.isNull()
            get { terminationPolicies }.isEqualTo(setOf(TerminationPolicy.NewestInstance))
          }
          that(exported.defaults.launchConfiguration).isNotNull()
          that(exported.defaults.launchConfiguration!!) {
            get { ebsOptimized }.isNull()
            get { instanceMonitoring }.isNull()
            get { ramdiskId }.isNull()
            get { instanceType }.isNotNull()
            get { iamRole }.isNotNull()
            get { keyPair }.isNotNull()
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

private fun ActiveServerGroup.withNonDefaultHealthProps(): ActiveServerGroup =
  copy(
    asg = asg.copy(terminationPolicies = setOf(TerminationPolicy.NewestInstance.name)
    )
  )

private fun ActiveServerGroup.withNonDefaultLaunchConfigProps(): ActiveServerGroup =
  copy(
    launchConfig = launchConfig.copy(iamInstanceProfile = "NotTheDefaultInstanceProfile", keyName = "not-the-default-key")
  )

private fun ActiveServerGroup.withDifferentSize(): ActiveServerGroup =
  copy(
    capacity = Capacity(min = 1, max = 10)
  )
