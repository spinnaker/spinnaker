package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.DependencyType.GENERIC_RESOURCE
import com.netflix.spinnaker.keel.api.DependencyType.LOAD_BALANCER
import com.netflix.spinnaker.keel.api.DependencyType.SECURITY_GROUP
import com.netflix.spinnaker.keel.api.DependencyType.TARGET_GROUP
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.kork.exceptions.SystemException
import org.jgrapht.alg.cycle.CycleDetector
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.traverse.TopologicalOrderIterator

/**
 * Helper class to perform a topological sort of the [environment]'s resources based on their dependency graph.
 */
class ResourceDependencySorter(private val environment: Environment) {
  fun sort(): List<Resource<*>> {
    val graph = DefaultDirectedGraph<Resource<*>, DefaultEdge>(DefaultEdge::class.java)

    // First, add resources as vertices
    environment.resources.forEach { resource ->
      graph.addVertex(resource)
    }

    // Then add dependencies as edges
    environment.resources.filter { it.spec is Dependent }.forEach { resource ->
      with(resource.spec as Dependent) {
        dependsOn.forEach { dependency ->
          val target = environment.findDependency(dependency)
          if (target != null) {
            graph.addEdge(resource, target)
          }
        }
      }
    }

    val cycleDetector = CycleDetector(graph)
    if (cycleDetector.detectCycles()) {
      throw SystemException("Unexpected cyclic dependency between resources detected for environment" +
        " ${environment.name} in application ${environment.application}"
      )
    }

    return TopologicalOrderIterator(graph).asSequence().toList()
  }

  private fun Environment.findDependency(dependency: Dependency) =
    // TODO: this is kinda yucky because we have to hard-code resource kinds and peek into specs.
    //  Maybe at some point we could revisit the model for dependencies and flip it around.
    //  For example, instead of saying “this cluster depends on this target group”, the target group
    //  could have a list of clusters that should be attached, or even a separate construct altogether
    //  modeling the link between the two. Under the covers we could still resolve it to the format Orca needs.
    resources.find {
      when (dependency.type) {
        GENERIC_RESOURCE -> it.name == dependency.name && it.kind == dependency.kind
        SECURITY_GROUP -> it.name == dependency.name && it.spec is SecurityGroupSpec
        LOAD_BALANCER -> it.name == dependency.name && it.spec is LoadBalancerSpec
        TARGET_GROUP -> it.spec is ApplicationLoadBalancerSpec
          && (it.spec as ApplicationLoadBalancerSpec).targetGroups.map { tg -> tg.name }.contains(dependency.name)
        else -> false
      }
    }
}

internal val Environment.resourcesSortedByDependencies: List<Resource<*>>
  get() = ResourceDependencySorter(this).sort()

