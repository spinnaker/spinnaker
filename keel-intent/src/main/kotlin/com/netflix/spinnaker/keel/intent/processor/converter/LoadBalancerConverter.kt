package com.netflix.spinnaker.keel.intent.processor.converter

import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.ElasticLoadBalancer
import com.netflix.spinnaker.keel.clouddriver.model.ElasticLoadBalancer.HealthCheck
import com.netflix.spinnaker.keel.clouddriver.model.ElasticLoadBalancer.ListenerDescription
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.intent.AmazonElasticLoadBalancerSpec
import com.netflix.spinnaker.keel.intent.AvailabilityZoneConfig.Automatic
import com.netflix.spinnaker.keel.intent.AvailabilityZoneConfig.Manual
import com.netflix.spinnaker.keel.intent.HealthCheckSpec
import com.netflix.spinnaker.keel.intent.HealthEndpoint
import com.netflix.spinnaker.keel.intent.LoadBalancerSpec
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.Protocol.HTTP
import com.netflix.spinnaker.keel.model.Protocol.HTTPS
import com.netflix.spinnaker.keel.model.Protocol.SSL
import com.netflix.spinnaker.keel.model.Protocol.TCP
import com.netflix.spinnaker.keel.model.Protocol.valueOf
import org.springframework.stereotype.Component

@Component
class LoadBalancerConverter(
  private val cloudDriver: CloudDriverCache
) : SpecConverter<LoadBalancerSpec, ElasticLoadBalancer> {
  override fun convertToState(spec: LoadBalancerSpec): ElasticLoadBalancer {
    if (spec is AmazonElasticLoadBalancerSpec) {
      val vpcId = cloudDriver.networkBy(spec.vpcName!!, spec.accountName, spec.region).id
      val zones = cloudDriver.availabilityZonesBy(spec.accountName, vpcId, spec.region)
      return ElasticLoadBalancer(
        loadBalancerName = spec.name,
        scheme = spec.scheme,
        vpcid = vpcId,
        availabilityZones = spec.availabilityZones.let { zoneConfig ->
          when (zoneConfig) {
            is Manual -> zoneConfig.availabilityZones
            else -> zones
          }
        },
        healthCheck = spec.healthCheck.run {
          HealthCheck(target.toString(), interval, timeout, unhealthyThreshold, healthyThreshold)
        },
        listenerDescriptions = spec.listeners.map { listener ->
          ListenerDescription(listener)
        }.toSet(),
        securityGroups = spec.securityGroupNames
      )
    } else {
      TODO("${spec.javaClass.simpleName} is not supported")
    }
  }

  override fun convertFromState(state: ElasticLoadBalancer): LoadBalancerSpec =
    state.run {
      val vpc = cloudDriver.networkBy(vpcid!!)
      val zones = cloudDriver.availabilityZonesBy(vpc.account, vpc.id, vpc.region)
      AmazonElasticLoadBalancerSpec(
        cloudProvider = vpc.cloudProvider,
        accountName = vpc.account,
        region = vpc.region,
        vpcName = vpc.name,
        application = loadBalancerName.substringBefore("-"),
        name = loadBalancerName,
        healthCheck = healthCheck.convertFromState(),
        availabilityZones = if (availabilityZones == zones) Automatic else Manual(availabilityZones),
        scheme = scheme,
        listeners = listenerDescriptions.map { it.listener }.toSet(),
        securityGroupNames = securityGroups.map {
          cloudDriver.securityGroupBy(vpc.account, it).name
        }.toSet()
      )
    }

  override fun convertToJob(spec: LoadBalancerSpec, changeSummary: ChangeSummary): List<Job> {
    TODO("not implemented")
  }

  private fun HealthCheck.convertFromState(): HealthCheckSpec =
    HealthCheckSpec(
      target = Regex("([A-Z]+):(\\d+)(/\\w+)?").find(target)
        ?.let { match ->
          match.groupValues
            .let { Triple(valueOf(it[1]), it[2].toInt(), it[3]) }
            .let { (protocol, port, path) ->
              when (protocol) {
                HTTP -> HealthEndpoint.Http(port, path)
                HTTPS -> HealthEndpoint.Https(port, path)
                SSL -> HealthEndpoint.Ssl(port)
                TCP -> HealthEndpoint.Tcp(port)
              }
            }
        } ?: throw IllegalStateException("Unable to parse health check target \"$target\""),
      timeout = timeout,
      interval = interval,
      healthyThreshold = healthyThreshold,
      unhealthyThreshold = unhealthyThreshold
    )
}
