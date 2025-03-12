package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore

interface ResourceSpecMixin {
  @get:JsonIgnore
  val displayName: String
}
