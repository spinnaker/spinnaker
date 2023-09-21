package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Highlander
import com.netflix.spinnaker.keel.api.ManagedRolloutConfig
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.SelectionStrategy
import com.netflix.spinnaker.keel.api.StaggeredRegion
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
import com.netflix.spinnaker.keel.core.serverGroup
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.igor.artifact.ArtifactService
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import io.mockk.mockk
import io.mockk.spyk
import org.springframework.core.env.Environment
import java.time.Clock
import java.time.Duration

class Ec2BaseClusterHandlerTests : BaseClusterHandlerTests<ClusterSpec, ServerGroup, ClusterHandler>() {
  private val cloudDriverService: CloudDriverService = mockk()
  private val cloudDriverCache: CloudDriverCache = mockk()
  private val orcaService: OrcaService = mockk()
  private val clusterExportHelper: ClusterExportHelper = mockk()
  private val springEnv: Environment = mockk(relaxed = true)
  private val blockDeviceConfig : BlockDeviceConfig = BlockDeviceConfig(springEnv, VolumeDefaultConfiguration())
  val artifactService = mockk<ArtifactService>()

  val metadata = mapOf("id" to "1234", "application" to "waffles", "serviceAccount" to "me@you.com" )

  val launchConfigurationSpec = LaunchConfigurationSpec(
    image = VirtualMachineImage("id-1", "my-app-1.2.3", "base-1"),
    instanceType = "m3.xl",
    keyPair = "keypair",
    ebsOptimized = false,
    instanceMonitoring = false,
    ramdiskId = "1"
  )

  val baseSpec = ClusterSpec(
    moniker = Moniker("waffles"),
    artifactReference = "my-artfact",
    locations = SubnetAwareLocations(
      account = "account",
      regions = setOf(SubnetAwareRegionSpec("east")),
      subnet = "subnet-1"
    ),
    _defaults = ClusterSpec.ServerGroupSpec(
      launchConfiguration = launchConfigurationSpec
    ),
    deployWith = Highlander(),
    managedRollout = ManagedRolloutConfig(enabled = false)
  )

  override fun createSpyHandler(
    resolvers: List<Resolver<*>>,
    clock: Clock,
    eventPublisher: EventPublisher,
    taskLauncher: TaskLauncher,
  ): ClusterHandler =
    spyk(ClusterHandler(
      cloudDriverService = cloudDriverService,
      cloudDriverCache = cloudDriverCache,
      orcaService = orcaService,
      clock = clock,
      taskLauncher = taskLauncher,
      eventPublisher = eventPublisher,
      resolvers = resolvers,
      clusterExportHelper = clusterExportHelper,
      blockDeviceConfig = blockDeviceConfig,
      artifactService = artifactService
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

  override fun getMultiRegionStaggeredDeployCluster(): Resource<ClusterSpec> {
    val spec = baseSpec.copy(
      locations = SubnetAwareLocations(
        account = "account",
        regions = setOf(SubnetAwareRegionSpec("east"), SubnetAwareRegionSpec("west")),
        subnet = "subnet-1"
      ),
      deployWith = RedBlack(
        stagger = listOf(
          StaggeredRegion(
            region = "east",
            pauseTime = Duration.ofMinutes(1)
          )
        )
      )
    )
    return Resource(
      kind = EC2_CLUSTER_V1_1.kind,
      metadata = metadata,
      spec = spec
    )
  }

  override fun getMultiRegionManagedRolloutCluster(): Resource<ClusterSpec> {
    val spec = baseSpec.copy(
      locations = SubnetAwareLocations(
        account = "account",
        regions = setOf(SubnetAwareRegionSpec("east"), SubnetAwareRegionSpec("west")),
        subnet = "subnet-1"
      ),
      managedRollout = ManagedRolloutConfig(enabled = true, selectionStrategy = SelectionStrategy.ALPHABETICAL)
    )
    return Resource(
      kind = EC2_CLUSTER_V1_1.kind,
      metadata = metadata,
      spec = spec
    )
  }

  override fun getDiffInMoreThanEnabled(resource: Resource<ClusterSpec>): ResourceDiff<Map<String, ServerGroup>> {
    val currentServerGroups = resource.spec.resolve()
      .byRegion()
    val desiredServerGroups = resource.spec.resolve()
      .map { it.withDoubleCapacity().withManyEnabled() }.byRegion()
    return DefaultResourceDiff(desiredServerGroups, currentServerGroups)
  }

  override fun getDiffOnlyInEnabled(resource: Resource<ClusterSpec>): ResourceDiff<Map<String, ServerGroup>> {
    val currentServerGroups = resource.spec.resolve()
      .byRegion()
    val desiredServerGroups = resource.spec.resolve()
      .map { it.withManyEnabled() }.byRegion()
    return DefaultResourceDiff(desiredServerGroups, currentServerGroups)
  }

  override fun getDiffInCapacity(resource: Resource<ClusterSpec>): ResourceDiff<Map<String, ServerGroup>> {
    val current = resource.spec.resolve().byRegion()
    val desired = resource.spec.resolve().map { it.withDoubleCapacity() }.byRegion()
    return DefaultResourceDiff(desired, current)
  }

  override fun getDiffInImage(resource: Resource<ClusterSpec>, version: String?): ResourceDiff<Map<String, ServerGroup>> {
    val current = resource.spec.resolve().byRegion()
    val desired = resource.spec.resolve().map { it.withADifferentImage(version ?: "112233") }.byRegion()
    return DefaultResourceDiff(desired, current)
  }

  override fun getCreateAndModifyDiff(resource: Resource<ClusterSpec>): ResourceDiff<Map<String, ServerGroup>> {
    val current = resource.spec.resolve().byRegion()
    val desired = resource.spec.resolve().map {
      when(it.location.region) {
        "east" -> it.withADifferentImage("1.2.3")
        else -> it.withDoubleCapacity()
      }
    }.byRegion()
    return DefaultResourceDiff(desired, current)
  }

  override fun getDiffForRollback(
    resource: Resource<ClusterSpec>,
    version: String,
    currentMoniker: Moniker
  ): ResourceDiff<Map<String, ServerGroup>> {
    val current = resource.spec.resolve().map { it.withMoniker(currentMoniker) }.byRegion()
    val desired = resource.spec.resolve().map { it.withADifferentImage(version) }.byRegion()
    return DefaultResourceDiff(desired, current)
  }

  override fun getDiffForRollbackPlusCapacity(
    resource: Resource<ClusterSpec>,
    version: String,
    currentMoniker: Moniker
  ): ResourceDiff<Map<String, ServerGroup>> {
    val current = resource.spec.resolve().map { it.withMoniker(currentMoniker) }.byRegion()
    val desired = resource.spec.resolve().map { it.withADifferentImage(version).withZeroCapacity() }.byRegion()
    return DefaultResourceDiff(desired, current)
  }

  override fun getRollbackServerGroupsByRegion(
    resource: Resource<ClusterSpec>,
    version: String,
    rollbackMoniker: Moniker
  ): Map<String, List<ServerGroup>> =
    resource
      .spec
      .resolve()
      .map { it.withADifferentImage(version).withMoniker(rollbackMoniker) }
      .associate { it.location.region to listOf(it) }

  override fun getRollbackServerGroupsByRegionZeroCapacity(
    resource: Resource<ClusterSpec>,
    version: String,
    rollbackMoniker: Moniker
  ): Map<String, List<ServerGroup>> =
    resource
      .spec
      .resolve()
      .map { it.withADifferentImage(version).withMoniker(rollbackMoniker).withZeroCapacity() }
      .associate { it.location.region to listOf(it) }

  private fun ServerGroup.withDoubleCapacity(): ServerGroup =
    copy(
      capacity = Capacity.DefaultCapacity(
        min = capacity.min * 2,
        max = capacity.max * 2,
        desired = capacity.desired * 2
      )
    )

  private fun ServerGroup.withZeroCapacity(): ServerGroup =
    copy(
      capacity = Capacity.DefaultCapacity(
        min = 0,
        max = 0,
        desired = 0
      )
    )

  private fun ServerGroup.withManyEnabled(): ServerGroup =
    copy(
      onlyEnabledServerGroup = false
    )

  private fun ServerGroup.withMoniker(moniker: Moniker): ServerGroup =
    copy(name = moniker.serverGroup)

  private fun ServerGroup.withADifferentImage(version: String): ServerGroup =
    copy(launchConfiguration = launchConfiguration.copy(
      appVersion = version,
      imageId = "id-$version",
    ))
}
