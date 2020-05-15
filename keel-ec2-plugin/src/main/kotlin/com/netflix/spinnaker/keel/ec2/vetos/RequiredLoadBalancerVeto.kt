package com.netflix.spinnaker.keel.ec2.vetos

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.OverrideableClusterDependencyContainer
import com.netflix.spinnaker.keel.api.ec2.loadBalancersByRegion
import com.netflix.spinnaker.keel.api.ec2.targetGroupsByRegion
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.AmazonLoadBalancer
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.veto.Veto
import com.netflix.spinnaker.keel.veto.VetoResponse
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RequiredLoadBalancerVeto(
  private val cloudDriver: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache
) : Veto {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override suspend fun check(resource: Resource<*>): VetoResponse {
    val spec = resource.spec as? OverrideableClusterDependencyContainer<*>
      ?: return allowedResponse()

    // avoid making call to CloudDriver to get all the load balancers if we don't need to
    if (spec.loadBalancersByRegion.isEmpty() && spec.targetGroupsByRegion.isEmpty()) {
      return allowedResponse()
    }

    val loadBalancers = loadBalancers(spec)
    val missingLoadBalancers = loadBalancers.findMissingLoadBalancers(spec)

    return if (missingLoadBalancers.isEmpty()) {
      allowedResponse()
    } else {
      deniedResponse(
        message = missingLoadBalancers.joinToString(separator = "\n", transform = MissingDependency::message),
        vetoArtifact = false
      )
    }
  }

  private suspend fun loadBalancers(spec: OverrideableClusterDependencyContainer<*>) =
    withContext(IO) {
      runCatching {
        cloudDriver.loadBalancersForApplication(DEFAULT_SERVICE_ACCOUNT, spec.application)
      }
        .onFailure { ex ->
          log.error("error finding load balancers for ${spec.application}", ex)
        }
        .getOrDefault(emptyList())
    }

  private suspend fun List<AmazonLoadBalancer>.findMissingLoadBalancers(spec: OverrideableClusterDependencyContainer<*>): Collection<MissingDependency> {
    val missing = mutableListOf<MissingDependency>()
    spec.loadBalancersByRegion.forEach { (name, account, regions) ->
      val missingRegions = regions.filter { region ->
        none { lb ->
          lb is ClassicLoadBalancerModel && lb.account == account && lb.region == region && lb.loadBalancerName == name
        }
      }
      if (missingRegions.isNotEmpty()) {
        missing.add(MissingLoadBalancer(name, account, missingRegions.toSet()))
      }
    }
    spec.targetGroupsByRegion.forEach { (name, account, regions) ->
      val missingRegions = regions.filter { region ->
        none { lb -> lb is ApplicationLoadBalancerModel && lb.account == account && lb.region == region && lb.targetGroups.any { it.targetGroupName == name } }
      }
      if (missingRegions.isNotEmpty()) {
        missing.add(MissingTargetGroup(name, account, missingRegions.toSet()))
      }
    }
    return missing
  }

  private val AmazonLoadBalancer.account: String
    get() = cloudDriverCache.networkBy(vpcId).account

  /**
   * I'd like to add a `region` property to [AmazonLoadBalancer] but CloudDriver doesn't
   * consistently return it from all the endpoints we use.
   */
  private val AmazonLoadBalancer.region: String
    get() = availabilityZones.first().dropLast(1)
}

sealed class MissingDependency(
  val name: String,
  val account: String,
  val regions: Set<String>
) {
  abstract val message: String
}

class MissingLoadBalancer(name: String, account: String, regions: Set<String>) : MissingDependency(name, account, regions) {
  override val message: String
    get() = "Load balancer $name is not found in $account / ${regions.joinToString()}"
}

class MissingTargetGroup(name: String, account: String, regions: Set<String>) : MissingDependency(name, account, regions) {
  override val message: String
    get() = "Target group $name is not found in $account / ${regions.joinToString()}"
}
