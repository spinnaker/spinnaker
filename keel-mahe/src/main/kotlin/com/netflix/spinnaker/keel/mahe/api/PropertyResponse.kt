package com.netflix.spinnaker.keel.mahe.api

data class PropertyResponse(
  val propertiesList: Set<DynamicProperty>
)
