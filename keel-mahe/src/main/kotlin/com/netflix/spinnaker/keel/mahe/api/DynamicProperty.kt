package com.netflix.spinnaker.keel.mahe.api

data class DynamicProperty(
  val propertyId: String,
  val key: String,
  val value: Any
)
