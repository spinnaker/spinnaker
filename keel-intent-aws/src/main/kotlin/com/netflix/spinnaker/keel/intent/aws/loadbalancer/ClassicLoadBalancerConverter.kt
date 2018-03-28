/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.keel.intent.aws.loadbalancer

import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.exceptions.DeclarativeException
import com.netflix.spinnaker.keel.intent.SpecConverter
import com.netflix.spinnaker.keel.intent.aws.loadbalancer.AvailabilityZoneConfig.Manual
import com.netflix.spinnaker.keel.model.Job
import org.springframework.stereotype.Component

@Component
class ClassicLoadBalancerConverter(
  private val cloudDriver: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache
) : SpecConverter<ClassicLoadBalancerSpec, LoadBalancerDescription> {

  override fun convertToState(spec: ClassicLoadBalancerSpec): LoadBalancerDescription {
    val vpc = cloudDriverCache.networkBy(spec.vpcName!!, spec.accountName, spec.region)
    val zones = cloudDriverCache.availabilityZonesBy(spec.accountName, vpc.id, spec.region)
    val subnets = getSubnetsForAvailabilityZones(vpc, spec.subnets, spec.availabilityZones, zones)

    return LoadBalancerDescription()
      .withLoadBalancerName(spec.name)
      .withScheme(spec.scheme?.toString())
      .withVPCId(vpc.id)
      .withAvailabilityZones(
        spec.availabilityZones.let { zoneConfig ->
          when (zoneConfig) {
            is Manual -> zoneConfig.availabilityZones
            else -> zones
          }
        }
      )
      .withSubnets(subnets.map { it.id })
      .withSecurityGroups(spec.securityGroupNames)
      .withHealthCheck(
        spec.healthCheck.run {
          HealthCheck()
            .withHealthyThreshold(healthyThreshold)
            .withUnhealthyThreshold(unhealthyThreshold)
            .withInterval(interval)
            .withTarget(target.toString())
            .withTimeout(timeout)
        }
      )
      .withListenerDescriptions(
        spec.listeners.map {
          ListenerDescription()
            .withListener(
              Listener()
                .withLoadBalancerPort(it.loadBalancerPort)
                .withProtocol(it.protocol.toString())
                .withInstancePort(it.instancePort)
                .withInstanceProtocol(it.instanceProtocol.toString())
                .withSSLCertificateId(it.sslCertificateId)
            )
        }
      )
  }

  override fun convertFromState(state: LoadBalancerDescription): ClassicLoadBalancerSpec =
    state.run {
      if (vpcId == null) {
        throw DeclarativeException("EC2-Classic load balancers are not supported: ${state.loadBalancerName}")
      }

      val vpc = cloudDriverCache.networkBy(vpcId!!)
      val zones = cloudDriverCache.availabilityZonesBy(vpc.account, vpc.id, vpc.region)
      val subnets = cloudDriver.listSubnets("aws").first { state.subnets.contains(it.id) }.purpose

      ClassicLoadBalancerSpec(
        accountName = vpc.account,
        region = vpc.region,
        vpcName = vpc.name,
        subnets = subnets,
        application = loadBalancerName.substringBefore("-"),
        name = loadBalancerName,
        healthCheck = healthCheck.convertFromState(),
        availabilityZones = if (availabilityZones == zones) Manual(zones.toSet()) else Manual(availabilityZones.toSet()),
        scheme = Scheme.valueOf(scheme),
        listeners = listenerDescriptions.map { it.listener.toClassicListener() }.toSet(),
        securityGroupNames = securityGroups.map {
          cloudDriverCache.securityGroupSummaryBy(vpc.account, vpc.region, it).name
        }.toSortedSet()
      )
    }

  override fun convertToJob(spec: ClassicLoadBalancerSpec, changeSummary: ChangeSummary): List<Job> {
    changeSummary.addMessage("Converging load balancer ${spec.name}")

    // TODO-AJ this is unnecessary overhead! consider adding expanded availabilityZones to spec
    val vpc = cloudDriverCache.networkBy(spec.vpcName, spec.accountName, spec.region)
    val zones = cloudDriverCache.availabilityZonesBy(vpc.account, vpc.id, vpc.region)

    return listOf(
      Job(
        "upsertLoadBalancer",
        mutableMapOf(
          "name" to spec.name,
          "region" to spec.region,
          "credentials" to spec.accountName,
          "cloudProvider" to spec.cloudProvider(),

          "availabilityZones" to mapOf(spec.region to zones),
          "loadBalancerType" to "classic",
          "securityGroups" to spec.securityGroupNames,
          "vpcId" to vpc.id,
          "subnetType" to spec.subnets,
          "isInternal" to if (spec.scheme == null) true else spec.scheme == Scheme.internal,

          "listeners" to spec.listeners.map {
            mutableMapOf(
              "externalProtocol" to it.protocol.toString(),
              "externalPort" to it.loadBalancerPort,
              "internalProtocol" to it.instanceProtocol.toString(),
              "internalPort" to it.instancePort
            )
          },

          "healthCheck" to spec.healthCheck.target.render(),
          "healthTimeout" to spec.healthCheck.timeout,
          "healthInterval" to spec.healthCheck.interval,
          "healthyThreshold" to spec.healthCheck.healthyThreshold,
          "unhealthyThreshold" to spec.healthCheck.unhealthyThreshold
        )
      )
    )
  }

  private fun HealthCheck.convertFromState(): HealthCheckSpec =
    HealthCheckSpec(
      target = Regex("([A-Z]+):(\\d+)(/\\w+)?").find(target)
        ?.let { match ->
          match.groupValues
            .let { Triple(Protocol.valueOf(it[1]), it[2].toInt(), it[3]) }
            .let { (protocol, port, path) ->
              when (protocol) {
                Protocol.HTTP -> HealthEndpoint.Http(port, path)
                Protocol.HTTPS -> HealthEndpoint.Https(port, path)
                Protocol.SSL -> HealthEndpoint.Ssl(port)
                Protocol.TCP -> HealthEndpoint.Tcp(port)
              }
            }
        } ?: throw IllegalStateException("Unable to parse health check target \"$target\""),
      timeout = timeout,
      interval = interval,
      healthyThreshold = healthyThreshold,
      unhealthyThreshold = unhealthyThreshold
    )

  private fun getSubnetsForAvailabilityZones(vpc: Network,
                                             subnetPurpose: String?,
                                             specAzConfig: AvailabilityZoneConfig,
                                             availabilityZones: Collection<String>): List<Subnet> {
    val zones = when (specAzConfig) {
      is Manual -> specAzConfig.availabilityZones
      else -> availabilityZones
    }
    return cloudDriver.listSubnets("aws")
      .filter { it.vpcId == vpc.id && it.purpose == subnetPurpose && zones.contains(it.availabilityZone) }
  }

}

fun Listener.toClassicListener(): ClassicListener =
  ClassicListener(
    Protocol.valueOf(protocol),
    loadBalancerPort,
    Protocol.valueOf(instanceProtocol),
    instancePort,
    sslCertificateId
  )
