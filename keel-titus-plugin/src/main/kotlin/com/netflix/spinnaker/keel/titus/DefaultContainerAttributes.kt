package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.titus.exceptions.DefaultContainerAttributesException
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keel.titus.default-container-attributes")
class DefaultContainerAttributes {
  var subnets: Map<String, String> = mutableMapOf()

  fun getAccountKey() = "titusParameter.agent.accountId"

  fun getSubnetKey() = "titusParameter.agent.subnets"

  fun getSubnetValue(account: String, region: String): String {
    return subnets["$account-$region"] ?: throw DefaultContainerAttributesException(account, region)
  }
}
