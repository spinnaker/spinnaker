package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.CapacitySpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.PredefinedMetricSpecification
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.TargetTrackingPolicy
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
import com.netflix.spinnaker.keel.clouddriver.model.PredefinedMetricSpecificationModel
import com.netflix.spinnaker.keel.clouddriver.model.ScalingPolicy
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCollection
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.clouddriver.model.TargetTrackingConfiguration
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.test.resource
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.path.NodePath
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue
import java.time.Clock
import java.time.Instant
import io.mockk.coEvery as every

class CapacityAndScalingPoliciesDiffingTests {

  val cloudDriverService: CloudDriverService = mockk()
  val cloudDriverCache: CloudDriverCache = mockk()

  val handler = ClusterHandler(
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

  val baseResource = resource(
    kind = EC2_CLUSTER_V1_1.kind,
    spec = ClusterSpec(
      moniker = Moniker("fnord", "main"),
      locations = SubnetAwareLocations(
        account = "prod",
        regions = setOf(SubnetAwareRegionSpec("us-west-2")),
        subnet = "internal"
      ),
      _defaults = ServerGroupSpec(
        capacity = CapacitySpec(1, 10, 5),
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

  val activeServerGroup = ActiveServerGroup(
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

  val desiredCapacityPath = NodePath.startBuilding()
    .mapKey("us-west-2")
    .propertyName("capacity")
    .propertyName("desired")
    .build()

  val Assertion.Builder<DefaultResourceDiff<Map<String, ServerGroup>>>.desiredCapacity: Assertion.Builder<DiffNode?>
    get() = get { diff.getChild(desiredCapacityPath) }

  fun generateDiff(resource: Resource<ClusterSpec>): DefaultResourceDiff<Map<String, ServerGroup>> =
    runBlocking {
      val desired = handler.desired(resource)
      val current = handler.current(resource)
      DefaultResourceDiff(desired, current)
    }

  fun Resource<ClusterSpec>.withCapacity(capacity: CapacitySpec): Resource<ClusterSpec> =
    copy(
      spec = spec.run {
        copy(_defaults = defaults.run {
          copy(capacity = capacity)
        })
      }
    )

  fun Resource<ClusterSpec>.withNoScalingPolicies(): Resource<ClusterSpec> =
    copy(
      spec = spec.run {
        copy(_defaults = defaults.run {
          copy(scaling = null)
        })
      }
    )

  fun Resource<ClusterSpec>.withAScalingPolicy(): Resource<ClusterSpec> =
    copy(
      spec = spec.run {
        copy(_defaults = defaults.run {
          copy(
            scaling = Scaling(
              targetTrackingPolicies = setOf(
                TargetTrackingPolicy(
                  targetValue = 20.0,
                  predefinedMetricSpec = PredefinedMetricSpecification(type = "ASGAverageCPUUtilization")
                )
              )
            )
          )
        })
      }
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
  fun `no delta is generated if a cluster with no scaling policies has the same desired capacity as the actual state`() {
    val resource = baseResource
      .withCapacity(CapacitySpec(1, 10, 5))
      .withNoScalingPolicies()

    every {
      cloudDriverService.activeServerGroup(any(), any(), any(), any(), any(), any())
    } returns activeServerGroup.copy(
      capacity = Capacity(1, 10, 5),
      scalingPolicies = emptyList()
    )

    expectThat(generateDiff(resource)) {
      desiredCapacity.isNull()
    }
  }

  @Test
  fun `a delta is generated if a cluster with no scaling policies has a different desired capacity to the actual state`() {
    val resource = baseResource
      .withCapacity(CapacitySpec(1, 10, 5))
      .withNoScalingPolicies()

    every {
      cloudDriverService.activeServerGroup(any(), any(), any(), any(), any(), any())
    } returns activeServerGroup.copy(
      capacity = Capacity(1, 10, 6),
      scalingPolicies = emptyList()
    )

    expectThat(generateDiff(resource)) {
      desiredCapacity.isNotNull().get(DiffNode::hasChanges).isTrue()
    }
  }

  @Test
  fun `no delta is generated for a cluster with scaling policies based on the actual desired capacity`() {
    val resource = baseResource
      .withCapacity(CapacitySpec(1, 10, null))
      .withAScalingPolicy()

    every {
      cloudDriverService.activeServerGroup(any(), any(), any(), any(), any(), any())
    } returns activeServerGroup.copy(
      capacity = Capacity(1, 10, 6),
      scalingPolicies = listOf(
        ScalingPolicy(
          autoScalingGroupName = "fnord-main-v001",
          policyName = "fnord-main-v001-policy-b85343cb-2ae7-4c18-b134-41e9d94c3ea5",
          policyType = "TargetTrackingScaling",
          estimatedInstanceWarmup = 300,
          targetTrackingConfiguration = TargetTrackingConfiguration(
            targetValue = 20.0,
            predefinedMetricSpecification = PredefinedMetricSpecificationModel(predefinedMetricType = "ASGAverageCPUUtilization")
          ),
          alarms = emptyList()
        )
      )
    )

    expectThat(generateDiff(resource)) {
      desiredCapacity.isNull()
      // the current value _has_ a desired capacity, even though the diff ignored it
      get { current?.get("us-west-2")?.capacity?.desired } isEqualTo 6
    }
  }

}
