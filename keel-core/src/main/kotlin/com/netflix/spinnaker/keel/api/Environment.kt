package com.netflix.spinnaker.keel.api

data class Environment(
  val name: String,
  val resources: Set<Resource<*>>
)
