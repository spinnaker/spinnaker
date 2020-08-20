package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore

interface ResourceMixin {
  @get:JsonIgnore
  val id: String

  @get:JsonIgnore
  val serviceAccount: String

  @get:JsonIgnore
  val application: String
}