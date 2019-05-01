package com.netflix.spinnaker.keel.mahe.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class DynamicProperty(
  val propertyId: String,
  val key: String,
  val value: Any
)
