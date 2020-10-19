package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.StaggeredRegion
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.CustomizedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
import com.netflix.spinnaker.keel.api.ec2.VirtualMachineImage
import com.netflix.spinnaker.keel.api.ec2.resolve
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCollection
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaTaskLauncher
import com.netflix.spinnaker.keel.test.resource
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isNull
import java.time.Clock
import io.mockk.coEvery as every

@Suppress("MemberVisibilityCanBePrivate")
internal class LaunchConfigTests {

  @ParameterizedTest
  @EnumSource(LaunchInfo::class)
  fun `empty ramdisk id string is converted to null`(launchInfo: LaunchInfo) {
    setup(ramdiskId = "", launchInfo=launchInfo)

    // code under test
    val currentState = runBlocking { clusterHandler.current(resource) }

    expectThat(serverGroup(currentState).launchConfiguration.ramdiskId).isNull()
  }

  //
  // Everything below is just fixture and mocking setup
  //


  /**
   * Given the output of ClusterHandler.current, return the ServerGroup object
   *
   * Assumes there is only one server group, will error otherwise
   */
  fun serverGroup(currentState : Map<String, ServerGroup>) : ServerGroup {
    expectThat(currentState).hasSize(1)
    return currentState.values.iterator().next()
  }

  fun clusterSpec(vpc: Network, subnet: Subnet, ramdiskId: String?) =
    ClusterSpec(
      moniker = Moniker(app = "keel", stack = "test"),
      locations = SubnetAwareLocations(
        account = vpc.account,
        vpc = "vpc0",
        subnet = subnet.purpose!!,
        regions = listOf(vpc).map { sn ->
          SubnetAwareRegionSpec(
            name = sn.region,
            availabilityZones = listOf("a", "b", "c").map { "${sn.region}$it" }.toSet()
          )
        }.toSet()
      ),
      deployWith = RedBlack(
        stagger = listOf(
          StaggeredRegion(region = vpc.region, hours = "16-02")
        )
      ),
      _defaults = serverGroupSpec(ramdiskId)
    )

  fun serverGroupSpec(ramdiskId: String?) =
    ClusterSpec.ServerGroupSpec(
      launchConfiguration = launchConfigurationSpec(ramdiskId=ramdiskId),
      capacity = Capacity(1, 6),
      scaling = Scaling(
        targetTrackingPolicies = setOf(
          TargetTrackingPolicy(
            name = "keel-test-target-tracking-policy",
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
        securityGroupNames = setOf(sg1.name, sg2.name)
      )
    )

  fun launchConfigurationSpec(ramdiskId : String?) =
    LaunchConfigurationSpec(
      image = VirtualMachineImage(
        id = "ami-123543254134",
        appVersion = "keel-0.287.0-h208.fe2e8a1",
        baseImageVersion = "nflx-base-5.308.0-h1044.b4b3f78"
      ),
      instanceType = "r4.8xlarge",
      ebsOptimized = false,
      iamRole = ServerGroup.LaunchConfiguration.defaultIamRoleFor("keel"),
      keyPair = "nf-keypair-test-fake",
      instanceMonitoring = false,
      ramdiskId = ramdiskId
    )

  val sg1 = SecurityGroupSummary("keel", "sg-325234532", "vpc-1")
  val sg2 = SecurityGroupSummary("keel-elb", "sg-235425234", "vpc-1")

  val vpc = Network(CLOUD_PROVIDER, "vpc-1452353", "vpc0", "test", "us-west-2")
  val subnet = Subnet("subnet-1", vpc.id, vpc.account, vpc.region, "${vpc.region}a", "internal (vpc0)")

  lateinit var spec : ClusterSpec
  lateinit var resource : Resource<ClusterSpec>

  val cloudDriverService = mockk<CloudDriverService>()
  val cloudDriverCache = mockk<CloudDriverCache>()
  val clusterExportHelper = mockk<ClusterExportHelper>(relaxed = true)


  val clusterHandler = ClusterHandler(
    cloudDriverService,
    cloudDriverCache,
    mockk(),
    mockk<OrcaTaskLauncher>(),
    Clock.systemUTC(),
    mockk(relaxUnitFun = true),
    emptyList<Resolver<ClusterSpec>>(),
    clusterExportHelper
  )

  fun setup(ramdiskId : String?, launchInfo: LaunchInfo) {
    spec = clusterSpec(vpc, subnet, ramdiskId)
    resource = resource(
      kind = EC2_CLUSTER_V1.kind,
      spec = spec
    )

    val serverGroup = spec.resolve().first()
    val activeServerGroupResponse = serverGroup.toCloudDriverResponse(
      vpc=vpc,
      subnets=listOf(subnet),
      securityGroups=listOf(sg1, sg2),
      launchInfo=launchInfo)

    with(cloudDriverCache) {
      every { networkBy(vpc.id) } returns vpc
      every { subnetBy(subnet.id) } returns subnet
      every { securityGroupById(vpc.account, vpc.region, sg1.id) } returns sg1
      every { securityGroupById(vpc.account, vpc.region, sg2.id) } returns sg2
    }

    every { cloudDriverService.activeServerGroup(any(), any()) } returns activeServerGroupResponse
    every { cloudDriverService.listServerGroups(any(), any(), any(), any()) } returns
      ServerGroupCollection(vpc.account, setOf(activeServerGroupResponse.toAllServerGroupsResponse()))

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
