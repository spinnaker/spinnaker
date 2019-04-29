package com.netflix.spinnaker.keel.api.deliveryconfig

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ResourceMetadata

data class DeliveryEnvironment(
  val name: String,
  val packageRef: ChildResource,
  val targets: List<ChildResource>
)

@JsonInclude(NON_NULL)
data class ChildResource(
  val apiVersion: ApiVersion,
  val kind: String,
  val spec: Map<String, Any?>,
  val metadata: Map<String, Any?>?
) {
  @get:JsonIgnore
  val resourceMetadata: ResourceMetadata?
    get() = metadata?.let { ResourceMetadata(it) }
}
