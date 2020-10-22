package com.netflix.spinnaker.keel.titus.exceptions

import com.netflix.spinnaker.kork.exceptions.IntegrationException

class AwsAccountConfigurationException(
  val awsAccount: String,
  val missingProperty: String
) : IntegrationException("AWS account $awsAccount misconfigured: missing value for $missingProperty")
