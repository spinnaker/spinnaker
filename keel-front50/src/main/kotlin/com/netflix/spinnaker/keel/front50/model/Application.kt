package com.netflix.spinnaker.keel.front50.model

data class Application(
  val name: String,
  val email: String,
  val dataSources: DataSources?,
  val repoProjectKey: String? = null,
  val repoSlug: String? = null,
  val repoType: String? = null
)

data class DataSources(
  val enabled: List<String>,
  val disabled: List<String>
)
