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

package com.netflix.spinnaker.keel.asset.aws.loadbalancer

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.netflix.spinnaker.keel.AssetIdProvider
import com.netflix.spinnaker.keel.asset.LoadBalancerSpec
import com.netflix.spinnaker.keel.asset.aws.jackson.AvailabilityZoneConfigDeserializer
import com.netflix.spinnaker.keel.asset.aws.jackson.AvailabilityZoneConfigSerializer
import com.netflix.spinnaker.keel.asset.aws.loadbalancer.AvailabilityZoneConfig.Automatic
import com.netflix.spinnaker.keel.asset.aws.loadbalancer.HealthEndpoint.*

@JsonTypeName("ec2.ClassicLoadBalancer")
data class ClassicLoadBalancerSpec(
  override val application: String,
  override val name: String,
  override val accountName: String,
  val region: String,
  val securityGroupNames: java.util.SortedSet<String>,
  val vpcName: String?,
  val subnets: String?,
  val availabilityZones: AvailabilityZoneConfig = Automatic,
  val scheme: Scheme?,
  val listeners: Set<ClassicListener>,
  val healthCheck: HealthCheckSpec
) : LoadBalancerSpec(), AssetIdProvider {

  override fun cloudProvider() = "ec2"

  override fun assetId() = "LoadBalancer:ec2:$accountName:$region:$name"
}

data class HealthCheckSpec(
  val target: HealthEndpoint,
  val interval: Int = 10,
  val timeout: Int = 5,
  val unhealthyThreshold: Int = 2,
  val healthyThreshold: Int = 10
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "protocol")
@JsonSubTypes(
  JsonSubTypes.Type(Tcp::class),
  JsonSubTypes.Type(Ssl::class),
  JsonSubTypes.Type(Http::class),
  JsonSubTypes.Type(Https::class)
)
sealed class HealthEndpoint(
  val protocol: Protocol
) {
  abstract val port: Int

  open fun render(): String = "$protocol:$port"

  @JsonTypeName("TCP")
  data class Tcp(override val port: Int) : HealthEndpoint(Protocol.TCP)

  @JsonTypeName("SSL")
  data class Ssl(override val port: Int) : HealthEndpoint(Protocol.SSL)

  @JsonTypeName("HTTP")
  data class Http(override val port: Int, val path: String) : HealthEndpoint(Protocol.HTTP) {
    override fun render() = "$protocol:$port$path"
  }

  @JsonTypeName("HTTPS")
  data class Https(override val port: Int, val path: String) : HealthEndpoint(Protocol.HTTPS) {
    override fun render() = "$protocol:$port$path"
  }
}

@JsonSerialize(using = AvailabilityZoneConfigSerializer::class)
@JsonDeserialize(using = AvailabilityZoneConfigDeserializer::class)
sealed class AvailabilityZoneConfig {
  object Automatic : AvailabilityZoneConfig()

  data class Manual(
    val availabilityZones: Set<String>
  ) : AvailabilityZoneConfig()
}

enum class Scheme {
  internal, external
}

data class ClassicListener(
  val protocol: Protocol,
  val loadBalancerPort: Int,
  val instanceProtocol: Protocol,
  val instancePort: Int,
  val sslCertificateId: String? = null
)

enum class Protocol {
  HTTP, HTTPS, TCP, SSL
}
