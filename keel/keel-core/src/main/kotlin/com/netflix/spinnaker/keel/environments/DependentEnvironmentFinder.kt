package com.netflix.spinnaker.keel.environments

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.events.ResourceState
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import org.springframework.stereotype.Component

/**
 * Used to find resources in a previous environment based on the chain of depends-on constraints.
 */
@Component
class DependentEnvironmentFinder(private val deliveryConfigRepository: DeliveryConfigRepository) {
  /**
   * Finds resources of the same kind as [resource] in any previous environment (via depends-on constraints). If no
   * previous environment exists (i.e. [resource]'s environment has no depends-on constraint) or
   */
  fun <SPEC : ResourceSpec> resourcesOfSameKindInDependentEnvironments(resource: Resource<SPEC>): Collection<Resource<SPEC>> {
    val dependentEnvironmentNames = dependentEnvironmentNames(resource)
    return if (dependentEnvironmentNames.isEmpty()) {
      emptyList()
    } else {
      deliveryConfigRepository.deliveryConfigFor(resource.id)
        .environments
        .filter { it.name in dependentEnvironmentNames }
        .flatMap { it.resources }
        .filter { it.kind == resource.kind } as Collection<Resource<SPEC>>
    }
  }

  fun resourceStatusesInDependentEnvironments(resource: Resource<*>): Map<String, ResourceState> {
    val dependentEnvironmentNames = dependentEnvironmentNames(resource)
    return if (dependentEnvironmentNames.isEmpty()) {
      emptyMap()
    } else {
      val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resource.id)
      return deliveryConfig
        .environments
        .map { it.name }
        .filter { it in dependentEnvironmentNames }
        .map { deliveryConfigRepository.resourceStatusesInEnvironment(deliveryConfig.name, it) }
        .reduce(Map<String, ResourceState>::plus)
    }
  }

  private fun <SPEC : ResourceSpec> dependentEnvironmentNames(resource: Resource<SPEC>): List<String> {
    val environment = deliveryConfigRepository.environmentFor(resource.id)
    return environment
      .constraints
      .filterIsInstance<DependsOnConstraint>()
      .map(DependsOnConstraint::environment)
  }
}
