package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.VirtualMachineImage
import com.netflix.spinnaker.keel.api.ec2.byRegion
import com.netflix.spinnaker.keel.api.ec2.resolve
import com.netflix.spinnaker.keel.api.plugins.BaseClusterHandlerTests
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import io.mockk.mockk
import io.mockk.spyk
import java.time.Clock

class Ec2BaseClusterHandlerTests : BaseClusterHandlerTests<ClusterSpec, ServerGroup, ClusterHandler>() {
  private val cloudDriverService: CloudDriverService = mockk()
  private val cloudDriverCache: CloudDriverCache = mockk()
  private val orcaService: OrcaService = mockk()
  private val taskLauncher: TaskLauncher = mockk()
  private val clusterExportHelper: ClusterExportHelper = mockk()
  private val blockDeviceConfig : BlockDeviceConfig = mockk()

  val metadata = mapOf("id" to "1234", "application" to "waffles", "serviceAccount" to "me@you.com" )

  val baseSpec = ClusterSpec(
    moniker = Moniker("waffles"),
    artifactReference = "my-artfact",
    locations = SubnetAwareLocations(
      account = "account",
      regions = setOf(SubnetAwareRegionSpec("east")),
      subnet = "subnet-1"
    ),
    _defaults = ClusterSpec.ServerGroupSpec(
      launchConfiguration = LaunchConfigurationSpec(
        image = VirtualMachineImage("id-1", "my-app-1.2.3", "base-1"),
        instanceType = "m3.xl",
        keyPair = "keypair",
        ebsOptimized = false,
        instanceMonitoring = false,
        ramdiskId = "1"
      )
    )
  )

  override fun createSpyHandler(resolvers: List<Resolver<*>>, clock: Clock, eventPublisher: EventPublisher): ClusterHandler =
    spyk(ClusterHandler(
      cloudDriverService = cloudDriverService,
      cloudDriverCache = cloudDriverCache,
      orcaService = orcaService,
      clock = clock,
      taskLauncher = taskLauncher,
      eventPublisher = eventPublisher,
      resolvers = resolvers,
      clusterExportHelper = clusterExportHelper,
      blockDeviceConfig = blockDeviceConfig
    ))

  override fun getRegions(resource: Resource<ClusterSpec>): List<String> =
    resource.spec.locations.regions.map { it.name }.toList()

  override fun getSingleRegionCluster(): Resource<ClusterSpec> {
    return Resource(
      kind = EC2_CLUSTER_V1_1.kind,
      metadata = metadata,
      spec = baseSpec
    )
  }

  override fun getMultiRegionCluster(): Resource<ClusterSpec> {
    val spec = baseSpec.copy(
      locations = SubnetAwareLocations(
        account = "account",
        regions = setOf(SubnetAwareRegionSpec("east"), SubnetAwareRegionSpec("west")),
        subnet = "subnet-1"
      )
    )
    return Resource(
      kind = EC2_CLUSTER_V1_1.kind,
      metadata = metadata,
      spec = spec
    )
  }

  override fun getDiffInMoreThanEnabled(): ResourceDiff<Map<String, ServerGroup>> {
    val currentServerGroups = getSingleRegionCluster().spec.resolve()
      .byRegion()
    val desiredServerGroups = getSingleRegionCluster().spec.resolve()
      .map { it.withDoubleCapacity().withManyEnabled() }.byRegion()
    return DefaultResourceDiff(desiredServerGroups, currentServerGroups)
  }

  override fun getDiffOnlyInEnabled(): ResourceDiff<Map<String, ServerGroup>> {
    val currentServerGroups = getSingleRegionCluster().spec.resolve()
      .byRegion()
    val desiredServerGroups = getSingleRegionCluster().spec.resolve()
      .map { it.withManyEnabled() }.byRegion()
    return DefaultResourceDiff(desiredServerGroups, currentServerGroups)
  }

  private fun ServerGroup.withDoubleCapacity(): ServerGroup =
    copy(
      capacity = Capacity(
        min = capacity.min * 2,
        max = capacity.max * 2,
        desired = capacity.desired!! * 2
      )
    )

  private fun ServerGroup.withManyEnabled(): ServerGroup =
    copy(
      onlyEnabledServerGroup = false
    )
}
