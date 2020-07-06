package com.netflix.spinnaker.keel.clouddriver.model

data class Capacity(
  val min: Int,
  val max: Int,
  val desired: Int?
)
