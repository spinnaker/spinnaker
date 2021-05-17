package com.netflix.spinnaker.keel.front50.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter

/**
 * A stage in a Spinnaker [Pipeline].
 */
data class Stage(
  val type: String,
  val name: String,
  val refId: String,
  val requisiteStageRefIds: List<String> = emptyList(),
  @get:JsonAnyGetter val details: MutableMap<String, Any> = mutableMapOf()
) {
  @JsonAnySetter
  fun setAttribute(key: String, value: Any) {
    details[key] = value
  }
}
