package com.netflix.spinnaker.keel.api

/**
 * An object for ResourceSpec's representing single-region cloud
 * resources managed in multiple regions via a single spec.
 */
interface MultiRegion : Monikered, ResourceSpec {
  val regionalIds: List<String>
}
