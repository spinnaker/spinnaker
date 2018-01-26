package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore

class LoadBalancer {
  @JsonIgnore
  private val properties = mutableMapOf<String, Any>()

  @JsonAnySetter
  fun set(name: String, value: Any) {
    properties.put(name, value)
  }

  @JsonAnyGetter
  fun properties(): Map<String, Any> {
    return properties
  }
}
