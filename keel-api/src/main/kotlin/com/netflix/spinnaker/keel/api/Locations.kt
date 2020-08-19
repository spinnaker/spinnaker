package com.netflix.spinnaker.keel.api

interface Locations<T : RegionSpec> {
  val regions: Set<T>
}
