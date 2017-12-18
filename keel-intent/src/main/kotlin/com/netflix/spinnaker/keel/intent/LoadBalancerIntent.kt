package com.netflix.spinnaker.keel.intent

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.ApplicationAwareIntentSpec
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.intent.AvailabilityZoneConfig.Automatic
import com.netflix.spinnaker.keel.intent.HealthEndpoint.Http
import com.netflix.spinnaker.keel.intent.HealthEndpoint.Https
import com.netflix.spinnaker.keel.intent.HealthEndpoint.Ssl
import com.netflix.spinnaker.keel.intent.HealthEndpoint.Tcp
import com.netflix.spinnaker.keel.intent.jackson.AvailabilityZoneConfigDeserializer
import com.netflix.spinnaker.keel.intent.jackson.AvailabilityZoneConfigSerializer
import com.netflix.spinnaker.keel.model.Listener
import com.netflix.spinnaker.keel.model.Protocol
import com.netflix.spinnaker.keel.model.Protocol.HTTP
import com.netflix.spinnaker.keel.model.Protocol.HTTPS
import com.netflix.spinnaker.keel.model.Protocol.SSL
import com.netflix.spinnaker.keel.model.Protocol.TCP
import com.netflix.spinnaker.keel.model.Scheme

private const val KIND = "LoadBalancer"
private const val CURRENT_SCHEMA = "0"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class LoadBalancerIntent(spec: LoadBalancerSpec) : Intent<LoadBalancerSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  @JsonIgnore override val defaultId = "$KIND:${spec.cloudProvider}:${spec.accountName}:${spec.name}"
}

@JsonTypeInfo(use = NAME, include = PROPERTY, property = KIND_PROPERTY)
abstract class LoadBalancerSpec : ApplicationAwareIntentSpec() {
  abstract val name: String
  abstract val cloudProvider: String
  abstract val accountName: String
  abstract val region: String
  abstract val securityGroupNames: Set<String>
}

@JsonTypeName("aws")
data class AmazonElasticLoadBalancerSpec(
  override val application: String,
  override val name: String,
  override val cloudProvider: String,
  override val accountName: String,
  override val region: String,
  override val securityGroupNames: Set<String>,
  val vpcName: String?,
  val availabilityZones: AvailabilityZoneConfig = Automatic,
  val scheme: Scheme?,
  val listeners: Set<Listener>,
  val healthCheck: HealthCheckSpec
) : LoadBalancerSpec()

data class HealthCheckSpec(
  val target: HealthEndpoint,
  val interval: Int = 10,
  val timeout: Int = 5,
  val unhealthyThreshold: Int = 2,
  val healthyThreshold: Int = 10
)

@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "protocol")
@JsonSubTypes(
  Type(Tcp::class),
  Type(Ssl::class),
  Type(Http::class),
  Type(Https::class)
)
sealed class HealthEndpoint(
  val protocol: Protocol
) {
  abstract val port: Int

  override fun toString() = "$protocol:$port"

  @JsonTypeName("TCP")
  data class Tcp(override val port: Int) : HealthEndpoint(TCP)

  @JsonTypeName("SSL")
  data class Ssl(override val port: Int) : HealthEndpoint(SSL)

  @JsonTypeName("HTTP")
  data class Http(override val port: Int, val path: String) : HealthEndpoint(HTTP) {
    override fun toString() = "$protocol:$port$path"
  }

  @JsonTypeName("HTTPS")
  data class Https(override val port: Int, val path: String) : HealthEndpoint(HTTPS) {
    override fun toString() = "$protocol:$port$path"
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
