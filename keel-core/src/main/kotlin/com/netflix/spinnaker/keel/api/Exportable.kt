package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.model.Moniker

data class Exportable(
  val account: String,
  val serviceAccount: String,
  val moniker: Moniker,
  val regions: Set<String>,
  val kind: ResourceKind
)
