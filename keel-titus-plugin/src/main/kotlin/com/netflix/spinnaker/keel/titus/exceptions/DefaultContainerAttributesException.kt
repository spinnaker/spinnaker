package com.netflix.spinnaker.keel.titus.exceptions

import com.netflix.spinnaker.kork.exceptions.IntegrationException

class DefaultContainerAttributesException(
  val account: String,
  val region: String
) : IntegrationException("Missing default subnets for titus account $account in region $region")
