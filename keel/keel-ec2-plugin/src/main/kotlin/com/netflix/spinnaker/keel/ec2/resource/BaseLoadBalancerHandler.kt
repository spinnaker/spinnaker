package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerSpec
import com.netflix.spinnaker.keel.api.plugins.ResolvableResourceHandler
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.orca.OrcaService

/**
 * Base implementation with shared behavior for EC2 load balancer handlers.
 */
abstract class BaseLoadBalancerHandler<SPEC : LoadBalancerSpec, MODEL>(
  private val cloudDriverCache: CloudDriverCache,
  private val taskLauncher: TaskLauncher,
  resolvers: List<Resolver<*>>
) : ResolvableResourceHandler<SPEC, Map<String, MODEL>>(resolvers) {

  override suspend fun delete(resource: Resource<SPEC>): List<Task> {
    val currentState = current(resource)
    val regions = currentState?.keys
      ?: return emptyList()
    with(resource.spec.locations) {
      val stages = regions.map { region ->
        val vpc = cloudDriverCache.networkBy(vpc!!, account, region)
        mapOf(
          "type" to "deleteLoadBalancer",
          "loadBalancerName" to resource.name,
          "loadBalancerType" to if (resource.spec is ApplicationLoadBalancerSpec) "application" else "classic",
          // This is a misnomer: you can only pass a single region since it needs to match the vpcID below.  ¯\_(ツ)_/¯
          "regions" to listOf(region),
          "credentials" to account,
          "vpcId" to vpc.id,
          "user" to resource.serviceAccount,
        )
      }
      return if (stages.isEmpty()) {
        emptyList()
      } else {
        listOf(
          taskLauncher.submitJob(
            resource = resource,
            description = "Delete load balancer ${resource.name} in account $account (${regions.joinToString()})",
            correlationId = "${resource.id}:delete",
            stages = stages
          )
        )
      }
    }
  }
}