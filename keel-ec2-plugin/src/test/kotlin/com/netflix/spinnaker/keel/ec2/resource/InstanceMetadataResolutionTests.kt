package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.VirtualMachineImage
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.Capacity
import com.netflix.spinnaker.keel.clouddriver.model.IamInstanceProfile
import com.netflix.spinnaker.keel.clouddriver.model.InstanceCounts
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchTemplate
import com.netflix.spinnaker.keel.clouddriver.model.LaunchTemplateData
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCollection
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.test.resource
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.time.Clock
import java.time.Instant
import io.mockk.coEvery as every

internal class InstanceMetadataResolutionTests {

  private val cloudDriverService: CloudDriverService = mockk()
  private val cloudDriverCache: CloudDriverCache = mockk()

  private val handler = ClusterHandler(
    cloudDriverService = cloudDriverService,
    cloudDriverCache = cloudDriverCache,
    orcaService = mockk(),
    taskLauncher = mockk(),
    clock = Clock.systemDefaultZone(),
    eventPublisher = mockk(relaxUnitFun = true),
    resolvers = emptyList(),
    clusterExportHelper = mockk(),
    blockDeviceConfig = mockk(),
    artifactService = mockk()
  )

  private val baseResource = resource(
    kind = EC2_CLUSTER_V1_1.kind,
    spec = ClusterSpec(
      moniker = Moniker("fnord", "main"),
      locations = SubnetAwareLocations(
        account = "prod",
        regions = setOf(SubnetAwareRegionSpec("us-west-2")),
        subnet = "internal"
      ),
      _defaults = ClusterSpec.ServerGroupSpec(
        capacity = ClusterSpec.CapacitySpec(1, 10, 5),
        scaling = null,
        launchConfiguration = LaunchConfigurationSpec(
          image = VirtualMachineImage(
            id = "ami-001",
            appVersion = "fnord-001",
            baseImageName = "bionic-001"
          ),
          instanceType = "m5.large",
          keyPair = "fnordKey"
        )
      ),
    )
  )

  private val activeServerGroup = ActiveServerGroup(
    name = "fnord-main-v001",
    region = "us-west-2",
    zones = listOf("a", "b", "c").mapTo(mutableSetOf()) { "us-west-2${it}" },
    image = ActiveServerGroupImage(
      imageId = "ami-001",
      appVersion = "fnord-001",
      baseImageName = "bionic-001",
      name = "fnord-001",
      imageLocation = "test",
      description = "whatever"
    ),
    launchTemplate = LaunchTemplate(
      LaunchTemplateData(
        ebsOptimized = true,
        imageId = "ami-001",
        instanceType = "m5.large",
        keyName = "fnordKey",
        iamInstanceProfile = IamInstanceProfile("fnordInstanceProfile"),
        monitoring = InstanceMonitoring(false),
        ramDiskId = null
      )
    ),
    asg = AutoScalingGroup(
      "fnord-main-v001",
      60000L,
      "EC2",
      10000L,
      emptySet(),
      emptySet(),
      emptySet(),
      emptySet(),
      "us-west-2a"
    ),
    scalingPolicies = emptyList(),
    targetGroups = emptySet(),
    loadBalancers = emptySet(),
    cloudProvider = "aws",
    vpcId = "vpc-001",
    securityGroups = emptySet(),
    accountName = "prod",
    moniker = Moniker(
      "fnord",
      "main",
      null,
      sequence = 1
    ),
    capacity = Capacity(1, 6, 10),
    instanceCounts = InstanceCounts(1, 1, 0, 0, 0, 0),
    createdTime = Instant.now().toEpochMilli()
  )

  @BeforeEach
  fun setUpBoilerplate() {
    every { cloudDriverCache.networkBy("vpc-001") } returns Network("aws", "vpc-001", "vpc0", "prod", "us-west-2")
    every { cloudDriverCache.subnetBy("us-west-2a") } returns Subnet(
      "us-west-2a",
      "vpc-001",
      "prod",
      "us-west-2",
      "us-west-2a",
      "internal (vpc0)"
    )
    every {
      cloudDriverService.listServerGroups(any(), any(), any(), any(), any())
    } returns ServerGroupCollection("prod", emptySet())
  }

  @Test
  fun `IMDSv2 is recognized as off if no metadata options are present`() {
    every {
      cloudDriverService.activeServerGroup(any(), any(), any(), any(), any(), any())
    } returns activeServerGroup

    val current = runBlocking { handler.current(baseResource) }
    expectThat(current["us-west-2"]!!.launchConfiguration.requireIMDSv2).isFalse()
  }

  @Test
  fun `IMDSv2 is recognized as off if associated metadata options are optional`() {
    every {
      cloudDriverService.activeServerGroup(any(), any(), any(), any(), any(), any())
    } returns activeServerGroup.copy(
      launchTemplate = activeServerGroup.launchTemplate!!.copy(
        launchTemplateData = activeServerGroup.launchTemplate!!.launchTemplateData.copy(
          metadataOptions = mapOf("httpTokens" to "optional")
        )
      )
    )

    val current = runBlocking { handler.current(baseResource) }
    expectThat(current["us-west-2"]!!.launchConfiguration.requireIMDSv2).isFalse()
  }

  @Test
  fun `IMDSv2 is recognized as on if associated metadata options are required`() {
    every {
      cloudDriverService.activeServerGroup(any(), any(), any(), any(), any(), any())
    } returns activeServerGroup.copy(
      launchTemplate = activeServerGroup.launchTemplate!!.copy(
        launchTemplateData = activeServerGroup.launchTemplate!!.launchTemplateData.copy(
          metadataOptions = mapOf("httpTokens" to "required")
        )
      )
    )

    val current = runBlocking { handler.current(baseResource) }
    expectThat(current["us-west-2"]!!.launchConfiguration.requireIMDSv2).isTrue()
  }
}
