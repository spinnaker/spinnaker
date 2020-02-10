package com.netflix.spinnaker.keel.json.mixins

import com.fasterxml.jackson.annotation.JsonIgnore

interface MonikeredMixin {
  @get:JsonIgnore
  val application: String
}
