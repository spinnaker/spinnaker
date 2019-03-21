package com.netflix.spinnaker.keel.api.ec2


data class NamedImage(
  val account: String,
  val name: String,
  val currentImage: ImageResult?)

data class ImageResult(
  val imageName: String,
  val attributes: Map<String, Any?>?,
  val tagsByImageId: Map<String, Map<String, String?>?>?,
  val accounts: Set<String>?,
  val amis: Map<String, List<String>?>)


