package com.netflix.spinnaker.keel.bakery.api

import com.netflix.spinnaker.keel.api.HasApplication

data class ImageSpec(
  val artifactName: String,
  val baseLabel: BaseLabel,
  val baseOs: String,
  val regions: Set<String>,
  val storeType: StoreType,
  override val application: String // the application an image is baked in
) : HasApplication
