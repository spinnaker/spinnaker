package com.netflix.spinnaker.keel.front50.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter

/**
 * A Spinnaker application, as represented in Front50.
 */
data class Application(
  val name: String,
  val email: String,
  val dataSources: DataSources?,
  val repoProjectKey: String? = null,
  val repoSlug: String? = null,
  val repoType: String? = null,
  val createTs: String? = null,
  @get:JsonAnyGetter val details: MutableMap<String, Any?> = mutableMapOf()
) {
  @JsonAnySetter
  fun setDetail(key: String, value: Any?) {
    details[key] = value
  }
}

data class DataSources(
  val enabled: List<String>,
  val disabled: List<String>
)
