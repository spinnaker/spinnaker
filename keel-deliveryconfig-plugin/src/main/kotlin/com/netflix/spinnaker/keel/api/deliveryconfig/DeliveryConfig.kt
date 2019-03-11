package com.netflix.spinnaker.keel.api.deliveryconfig

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL

@JsonInclude(NON_NULL)
data class DeliveryConfig(
  val name: String,
  val application: String
)
