package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.Resource

interface DeliveryConfigMixin {
  @get:JsonIgnore
  val resources: List<Resource<*>>
}
