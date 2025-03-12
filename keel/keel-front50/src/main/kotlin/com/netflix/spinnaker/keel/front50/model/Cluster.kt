package com.netflix.spinnaker.keel.front50.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.netflix.spinnaker.keel.api.Moniker

/**
 * A cluster, as represented in a [DeployStage].
 */
data class Cluster(
  val account: String,
  val application: String,
  val provider: String,
  val strategy: String,
  val stack: String? = null,
  val freeFormDetails: String? = null,
  val detail: String? = freeFormDetails,
  val availabilityZones: Map<String, List<String>> = emptyMap(),
  @JsonAlias("region")
  private val _region: String? = null
) {
  val moniker: Moniker
    get() = Moniker(
      app = application,
      stack = if (stack.isNullOrEmpty()) null else stack,
      detail = if (detail.isNullOrEmpty()) null else detail
    )

  val name: String
    get() = moniker.toName()

  val region: String?
    // region info may be available via the availabilityZones or region fields
    get() = availabilityZones.keys.firstOrNull() ?: _region

  @get:JsonAnyGetter
  val details: MutableMap<String, Any> = mutableMapOf()

  @JsonAnySetter
  fun setAttribute(key: String, value: Any) {
    details[key] = value
  }
}
