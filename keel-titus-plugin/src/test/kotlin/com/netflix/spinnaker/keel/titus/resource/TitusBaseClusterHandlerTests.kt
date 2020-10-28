package com.netflix.spinnaker.keel.titus.resource

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.plugins.BaseClusterHandlerTests
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.titus.TITUS_CLUSTER_V1
import com.netflix.spinnaker.keel.titus.TitusClusterHandler
import com.netflix.spinnaker.keel.titus.byRegion
import com.netflix.spinnaker.keel.titus.resolve
import io.mockk.mockk
import io.mockk.spyk
import java.time.Clock

class TitusBaseClusterHandlerTests : BaseClusterHandlerTests<TitusClusterSpec, TitusServerGroup, TitusClusterHandler>() {
  private val cloudDriverService: CloudDriverService = mockk()
  private val cloudDriverCache: CloudDriverCache = mockk()
  private val orcaService: OrcaService = mockk()
  private val taskLauncher: TaskLauncher = mockk()
  private val clusterExportHelper: ClusterExportHelper = mockk()

  val metadata = mapOf("id" to "1234", "application" to "waffles", "serviceAccount" to "me@you.com" )

  val baseSpec = TitusClusterSpec(
    moniker = Moniker("waffles"),
    locations = SimpleLocations(
      account = "account",
      regions = setOf(SimpleRegionSpec("east"))
    ),
    container = DigestProvider(organization = "waffels", image = "butter", digest = "12345"),
    _defaults = TitusServerGroupSpec(
      capacity = Capacity(1,4,2)
    )
  )

  override fun getRegions(resource: Resource<TitusClusterSpec>): List<String> =
    resource.spec.locations.regions.map { it.name }.toList()

  override fun createSpyHandler(resolvers: List<Resolver<*>>, clock: Clock, eventPublisher: EventPublisher): TitusClusterHandler =
    spyk(TitusClusterHandler(
      cloudDriverService = cloudDriverService,
      cloudDriverCache = cloudDriverCache,
      orcaService = orcaService,
      clock = clock,
      taskLauncher = taskLauncher,
      eventPublisher = eventPublisher,
      resolvers = resolvers,
      clusterExportHelper = clusterExportHelper
    ))

  override fun getSingleRegionCluster(): Resource<TitusClusterSpec> {
    return Resource(
      kind = TITUS_CLUSTER_V1.kind,
      metadata = metadata,
      spec = baseSpec
    )
  }

  override fun getMultiRegionCluster(): Resource<TitusClusterSpec> {
    val spec = baseSpec.copy(
      locations = SimpleLocations(
        account = "account",
        regions = setOf(SimpleRegionSpec("east"),SimpleRegionSpec("east"))
      )
    )
    return Resource(
      kind = TITUS_CLUSTER_V1.kind,
      metadata = metadata,
      spec = spec
    )
  }

  override fun getDiffInMoreThanEnabled(): ResourceDiff<Map<String, TitusServerGroup>> {
    val currentServerGroups = getSingleRegionCluster().spec.resolve()
      .byRegion()
    val desiredServerGroups = getSingleRegionCluster().spec.resolve()
      .map { it.withDoubleCapacity().withManyEnabled() }.byRegion()
    return DefaultResourceDiff(desiredServerGroups, currentServerGroups)
  }

  override fun getDiffOnlyInEnabled(): ResourceDiff<Map<String, TitusServerGroup>> {
    val currentServerGroups = getSingleRegionCluster().spec.resolve()
      .byRegion()
    val desiredServerGroups = getSingleRegionCluster().spec.resolve()
      .map { it.withManyEnabled() }.byRegion()
    return DefaultResourceDiff(desiredServerGroups, currentServerGroups)
  }

  private fun TitusServerGroup.withDoubleCapacity(): TitusServerGroup =
    copy(
      capacity = Capacity(
        min = capacity.min * 2,
        max = capacity.max * 2,
        desired = capacity.desired!! * 2
      )
    )

  private fun TitusServerGroup.withManyEnabled(): TitusServerGroup =
    copy(
      onlyEnabledServerGroup = false
    )
}
