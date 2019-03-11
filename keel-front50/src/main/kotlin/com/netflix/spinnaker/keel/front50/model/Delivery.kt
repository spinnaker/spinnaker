package com.netflix.spinnaker.keel.front50.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter

data class Delivery(
  val id: String,
  val application: String,
  val updateTs: Long? = null,
  val createTs: Long? = null,
  val lastModifiedBy: String? = null,
  val deliveryArtifacts: List<Map<String, Any>> = emptyList(),
  val deliveryEnvironments: List<Map<String, Any>> = emptyList(),
  @get:JsonAnyGetter val details: MutableMap<String, Any> = mutableMapOf()
) {

  @JsonAnySetter
  fun setAttribute(key: String, value: Any) {
    details[key] = value
  }
}
