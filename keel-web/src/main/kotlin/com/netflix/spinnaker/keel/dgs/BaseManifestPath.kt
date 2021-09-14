package com.netflix.spinnaker.keel.dgs
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "scm")
@Component
class BaseManifestPath {
  var baseManifestPath: String? = null
}
