package com.netflix.spinnaker.keel.api

data class ResourceKind(
  val group: String,
  val singular: String,
  val plural: String
)
