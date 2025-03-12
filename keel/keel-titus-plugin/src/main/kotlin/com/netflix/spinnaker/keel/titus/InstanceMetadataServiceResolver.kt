package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.titus.TITUS_CLUSTER_V1
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.environments.DependentEnvironmentFinder
import com.netflix.spinnaker.keel.optics.mapValueLens
import com.netflix.spinnaker.keel.optics.resourceSpecLens
import com.netflix.spinnaker.keel.persistence.FeatureRolloutRepository
import com.netflix.spinnaker.keel.rollout.RolloutAwareResolver
import com.netflix.spinnaker.keel.titus.optics.titusClusterSpecContainerAttributesLens

class InstanceMetadataServiceResolver(
  dependentEnvironmentFinder: DependentEnvironmentFinder,
  resourceToCurrentState: suspend (Resource<TitusClusterSpec>) -> Map<String, TitusServerGroup>,
  featureRolloutRepository: FeatureRolloutRepository,
  eventPublisher: EventPublisher
) : RolloutAwareResolver<TitusClusterSpec, Map<String, TitusServerGroup>>(
  dependentEnvironmentFinder,
  resourceToCurrentState,
  featureRolloutRepository,
  eventPublisher
) {
  override val supportedKind = TITUS_CLUSTER_V1
  override val featureName = "imdsv2"

  override fun isExplicitlySpecified(resource: Resource<TitusClusterSpec>) =
    titusClusterResourceImdsRequireTokenLens.get(resource) != null

  override fun isAppliedTo(actualResource: Map<String, TitusServerGroup>) =
    actualResource.values.all { it.containerAttributes["titusParameter.agent.imds.requireToken"] == "true" }

  override fun activate(resource: Resource<TitusClusterSpec>) =
    titusClusterResourceImdsRequireTokenLens.set(resource, "true")

  override fun deactivate(resource: Resource<TitusClusterSpec>) =
    titusClusterResourceImdsRequireTokenLens.set(resource, null)

  override val Map<String, TitusServerGroup>.exists: Boolean
    get() = isNotEmpty()
}

/**
 * Lens for getting/setting the IMDSv2 flag in [TitusServerGroupSpec.containerAttributes].
 */
val titusClusterSpecImdsRequireTokenLens =
  titusClusterSpecContainerAttributesLens + mapValueLens("titusParameter.agent.imds.requireToken")

/**
 * Composed lens that lets us get/set the deeply nested IMDSv2 flag in [TitusServerGroupSpec.containerAttributes]
 * directly from the [Resource].
 */
private val titusClusterResourceImdsRequireTokenLens =
  resourceSpecLens<TitusClusterSpec>() + titusClusterSpecImdsRequireTokenLens
