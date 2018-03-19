package com.netflix.spinnaker.keel.intent

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.ApplicationAwareIntentSpec
import com.netflix.spinnaker.keel.Intent

private const val KIND = "LoadBalancer"
private const val CURRENT_SCHEMA = "0"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class LoadBalancerIntent(spec: LoadBalancerSpec) : Intent<LoadBalancerSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  @JsonIgnore override val id = "$KIND:${spec.cloudProvider()}:${spec.accountName}:${spec.name}"
}

@JsonTypeInfo(use = NAME, include = PROPERTY, property = KIND_PROPERTY)
abstract class LoadBalancerSpec : ApplicationAwareIntentSpec {
  abstract val name: String
  abstract val accountName: String

  abstract fun cloudProvider(): String
}
