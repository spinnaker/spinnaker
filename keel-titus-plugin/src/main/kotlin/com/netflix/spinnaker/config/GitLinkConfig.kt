package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("keel.repo-links")
class GitLinkConfig {
  var gitUrlPrefix: String? = null
}
