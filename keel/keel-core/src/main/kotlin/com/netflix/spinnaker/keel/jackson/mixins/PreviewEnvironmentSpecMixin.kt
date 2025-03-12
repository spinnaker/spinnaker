package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore

interface PreviewEnvironmentSpecMixin {
  @get:JsonIgnore
  val name: String
}
