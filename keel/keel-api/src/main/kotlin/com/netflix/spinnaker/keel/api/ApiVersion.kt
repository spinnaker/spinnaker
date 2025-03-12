package com.netflix.spinnaker.keel.api

data class ApiVersion(val group: String, val version: String = "1") {
  fun qualify(kind: String) = ResourceKind(group, kind, version)
}
