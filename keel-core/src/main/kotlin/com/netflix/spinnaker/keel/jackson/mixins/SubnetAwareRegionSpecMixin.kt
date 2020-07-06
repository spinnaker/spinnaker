package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY

interface SubnetAwareRegionSpecMixin {
  @get:JsonInclude(NON_EMPTY)
  val availabilityZones: Set<String>
}
