package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec

data class SupportedKind<SPEC : ResourceSpec>(
  val kind: ResourceKind,
  val specClass: Class<SPEC>
)

inline fun <reified SPEC : ResourceSpec> kind(kind: ResourceKind) =
  SupportedKind(kind, SPEC::class.java)

inline fun <reified SPEC : ResourceSpec> kind(kind: String) =
  SupportedKind(ResourceKind.parseKind(kind), SPEC::class.java)
