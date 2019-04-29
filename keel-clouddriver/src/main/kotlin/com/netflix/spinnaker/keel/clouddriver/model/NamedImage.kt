package com.netflix.spinnaker.keel.clouddriver.model

data class NamedImage(
  val imageName: String,
  val attributes: Map<String, Any?>,
  val tagsByImageId: Map<String, Map<String, String?>?>,
  val accounts: Set<String>,
  val amis: Map<String, List<String>?>
)
