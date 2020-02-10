package com.netflix.spinnaker.keel.api

interface Locations<T : RegionSpec> {
  val account: String
  /**
   * If not specified here, this should be derived from the [SubnetAwareLocations.subnet] (if
   * present) or use a default VPC name.
   */
  val vpc: String?
  val regions: Set<T>
}
