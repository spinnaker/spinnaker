package com.netflix.spinnaker.keel.api

data class Exportable(
  val cloudProvider: String,
  val account: String,
  val user: String,
  val moniker: Moniker,
  val regions: Set<String>,
  val kind: String
) {
  fun toResourceId() =
    "$cloudProvider:$kind:$account:$moniker"
}
