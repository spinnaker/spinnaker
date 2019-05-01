package com.netflix.spinnaker.keel.bakery.api

data class ImageSpec(
  val artifactName: String,
  val baseLabel: BaseLabel,
  val baseOs: String,
  val regions: Set<String>,
  val storeType: StoreType
)
