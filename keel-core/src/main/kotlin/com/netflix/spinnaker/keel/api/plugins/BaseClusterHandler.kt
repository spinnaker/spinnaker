package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.diff.toIndividualDiffs

/**
 * Common cluster functionality.
 * This class contains abstracted logic where we want all clusters to make the
 * same decision, or abstracted logic involving coordinating resources or
 * publishing events.
 */
abstract class BaseClusterHandler<SPEC: ResourceSpec, RESOLVED: Any>(
  resolvers: List<Resolver<*>>
) : ResolvableResourceHandler<SPEC, Map<String, RESOLVED>>(resolvers) {

  /**
   * parses the desired region from a resource diff
   */
  abstract fun getDesiredRegion(diff: ResourceDiff<RESOLVED>): String

  /**
   * returns true if the diff is only in whether there are too many clusters enabled
   */
  abstract fun ResourceDiff<RESOLVED>.isEnabledOnly(): Boolean

  /**
   * returns a list of regions where the active server group is unhealthy
   */
  abstract fun getUnhealthyRegionsForActiveServerGroup(resource: Resource<SPEC>): List<String>

  override suspend fun willTakeAction(
    resource: Resource<SPEC>,
    resourceDiff: ResourceDiff<Map<String, RESOLVED>>
  ): ActionDecision {
    // we can't take any action if there is more than one active server group
    //  AND the current active server group is unhealthy
    val potentialInactionableRegions = mutableListOf<String>()
    val inactionableRegions = mutableListOf<String>()
    resourceDiff.toIndividualDiffs().forEach { diff ->
      if (diff.hasChanges() && diff.isEnabledOnly()) {
        potentialInactionableRegions.add(getDesiredRegion(diff))
      }
    }
    if (potentialInactionableRegions.isNotEmpty()) {
      val unhealthyRegions = getUnhealthyRegionsForActiveServerGroup(resource)
      inactionableRegions.addAll(potentialInactionableRegions.intersect(unhealthyRegions))

    }
    if (inactionableRegions.isNotEmpty()) {
      return ActionDecision(
        willAct = false,
        message = "There is more than one server group enabled " +
          "but the latest is not healthy in ${inactionableRegions.joinToString(" and ")}. " +
          "Spinnaker cannot resolve the problem at this time. " +
          "Manual intervention might be required."
      )
    }
    return ActionDecision(willAct = true)
  }
}
