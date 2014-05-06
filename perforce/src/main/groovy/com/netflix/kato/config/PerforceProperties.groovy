package com.netflix.kato.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("perforce")
class PerforceProperties {
  String host = "perforce"
  Integer port = 1666
  String programName = "nac"
  String userName = "rolem"
  String udfRoot = '//depot/pd/xf/oq/cloud/apps/aws/udf'
}