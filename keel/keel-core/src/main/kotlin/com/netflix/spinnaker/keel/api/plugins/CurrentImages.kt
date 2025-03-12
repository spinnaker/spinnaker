package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.ResourceKind

data class CurrentImages(
  val kind: ResourceKind,
  val images: List<ImageInRegion>,
  val resourceId: String
)

data class ImageInRegion(
  val region: String,
  val imageName: String,
  val account: String
)
