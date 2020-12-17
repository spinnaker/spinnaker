package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore

interface VerificationMixin {
  @get:JsonIgnore
  val id: String
}
