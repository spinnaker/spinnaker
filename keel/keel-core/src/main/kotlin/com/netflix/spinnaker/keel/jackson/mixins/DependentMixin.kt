package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.Dependency

interface DependentMixin {
  @get:JsonIgnore
  val dependsOn: Set<Dependency>
}
