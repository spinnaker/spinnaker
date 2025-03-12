package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.ec2.Capacity.DefaultCapacity
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.titus.TITUS_CLUSTER_V1
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.environments.DependentEnvironmentFinder
import com.netflix.spinnaker.keel.persistence.FeatureRolloutRepository
import com.netflix.spinnaker.keel.rollout.RolloutAwareResolverTests
import com.netflix.spinnaker.keel.titus.optics.titusClusterSpecAccountLens
import com.netflix.spinnaker.keel.titus.optics.titusClusterSpecStackLens
import strikt.api.Assertion
import strikt.assertions.get
import strikt.assertions.hasEntry
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull
import java.util.UUID

internal class InstanceMetadataServiceResolverTests :
  RolloutAwareResolverTests<TitusClusterSpec, Map<String, TitusServerGroup>, InstanceMetadataServiceResolver>() {

  override fun createResolver(
    dependentEnvironmentFinder: DependentEnvironmentFinder,
    resourceToCurrentState: suspend (Resource<TitusClusterSpec>) -> Map<String, TitusServerGroup>,
    featureRolloutRepository: FeatureRolloutRepository,
    eventPublisher: EventPublisher
  ) = InstanceMetadataServiceResolver(
    dependentEnvironmentFinder,
    resourceToCurrentState,
    featureRolloutRepository,
    eventPublisher
  )

  override val kind = TITUS_CLUSTER_V1.kind

  override val spec = TitusClusterSpec(
    moniker = Moniker(
      app = "fnord"
    ),
    locations = SimpleLocations(
      account = "prod",
      regions = setOf(
        SimpleRegionSpec(name = "us-west-2")
      )
    ),
    container = ReferenceProvider(reference = "fnord-docker")
  )

  override val previousEnvironmentSpec =
    titusClusterSpecAccountLens.set(titusClusterSpecStackLens.set(spec, "test"), "test")

  override val nonExistentResolvedResource = emptyMap<String, TitusServerGroup>()

  override fun TitusClusterSpec.withFeatureApplied() =
    titusClusterSpecImdsRequireTokenLens.set(this, "true")

  override fun TitusClusterSpec.withFeatureNotApplied() =
    titusClusterSpecImdsRequireTokenLens.set(this, "false")

  override fun TitusClusterSpec.toResolvedType(featureActive: Boolean) =
    locations.regions.map(SimpleRegionSpec::name).associateWith { region ->
      TitusServerGroup(
        name = "${moniker}-v001",
        location = Location(
          account = locations.account,
          region = region
        ),
        capacity = DefaultCapacity(1, 1, 1),
        capacityGroup = "capacityGroup",
        container = DigestProvider("fnord", "docker", UUID.randomUUID().toString()),
        id = UUID.randomUUID().toString(),
        containerAttributes = if (featureActive) mapOf("titusParameter.agent.imds.requireToken" to "true") else emptyMap()
      )
    }

  override fun Assertion.Builder<Resource<TitusClusterSpec>>.featureIsApplied() =
    apply {
      get { spec.defaults.containerAttributes }
        .isNotNull()
        .hasEntry("titusParameter.agent.imds.requireToken", "true")
    }

  override fun Assertion.Builder<Resource<TitusClusterSpec>>.featureIsNotApplied(): Assertion.Builder<Resource<TitusClusterSpec>> =
    apply {
      get { spec.defaults.containerAttributes }
        .isNotNull()
        .get("titusParameter.agent.imds.requireToken") isNotEqualTo "true" // it can be false or it can be not present
    }
}
