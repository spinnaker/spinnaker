package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.diff.toIndividualDiffs

/**
 * Common cluster functionality.
 * This class contains abstracted logic where we want all clusters to make the
 * same decision, or abstracted logic involving coordinating resources or
 * publishing events.
 */
abstract class BaseClusterHandler<SPEC: ComputeResourceSpec<*>, RESOLVED: Any>(
  resolvers: List<Resolver<*>>,
  protected open val taskLauncher: TaskLauncher
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

  override suspend fun delete(resource: Resource<SPEC>): List<Task> {
    val serverGroupsByRegion = getExistingServerGroupsByRegion(resource)
    val regions = serverGroupsByRegion.keys
    val stages = serverGroupsByRegion.flatMap { (region, serverGroups) ->
      serverGroups.map { serverGroup ->
        mapOf(
          "type" to "destroyServerGroup",
          "asgName" to serverGroup.name,
          "moniker" to serverGroup.moniker,
          "serverGroupName" to serverGroup.name,
          "region" to region,
          "credentials" to resource.spec.locations.account,
          "cloudProvider" to cloudProvider,
          "user" to resource.serviceAccount,
          // the following 3 properties just say "halt this branch of the pipeline"
          "completeOtherBranchesThenFail" to false,
          "continuePipeline" to false,
          "failPipeline" to false,
        )
      }
    }
    return if (stages.isEmpty()) {
      emptyList()
    } else {
      listOf(
        taskLauncher.submitJob(
          resource = resource,
          description = "Delete cluster ${resource.name} in account ${resource.spec.locations.account}" +
            " (${regions.joinToString()})",
          correlationId = "${resource.id}:delete",
          stages = stages
        )
      )
    }
  }

  protected abstract suspend fun getExistingServerGroupsByRegion(resource: Resource<SPEC>): Map<String, List<ServerGroupIdentity>>

  protected abstract val cloudProvider: String

  /**
   * gets current state of the resource and returns the current image, by region.
   */
  abstract suspend fun getImage(resource: Resource<SPEC>): CurrentImages
}
