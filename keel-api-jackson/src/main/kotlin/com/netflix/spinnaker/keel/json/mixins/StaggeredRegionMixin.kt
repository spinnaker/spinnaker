package com.netflix.spinnaker.keel.json.mixins

import com.fasterxml.jackson.annotation.JsonIgnore

interface StaggeredRegionMixin {
  @get:JsonIgnore
  val allowedHours: Set<Int>
}
