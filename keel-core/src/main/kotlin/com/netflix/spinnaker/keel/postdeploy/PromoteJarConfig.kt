package com.netflix.spinnaker.keel.postdeploy

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Defines the docker image that the promote jar runner will run
 */
@ConfigurationProperties("post-deploy.promote-jar")
class PromoteJarConfig {
  var imageId: String? = null
  var account: String? = null
  var region: String? = null
  var application: String? = "keel"
}
