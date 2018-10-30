package com.netflix.spinnaker.keel.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer

data class Resource(
  val apiVersion: ResourceApiVersion,
  val kind: String,
  val metadata: ResourceMetadata,
  val spec: Map<String, Any>
)

@JsonSerialize(using = ToStringSerializer::class)
data class ResourceApiVersion(
  val group: String,
  val version: String
) {
  @JsonCreator
  constructor(value: String) :
    this(value.substringBefore("/"), value.substringAfter("/"))

  override fun toString() = "$group/$version"
}

data class ResourceMetadata(
  val name: String
)
