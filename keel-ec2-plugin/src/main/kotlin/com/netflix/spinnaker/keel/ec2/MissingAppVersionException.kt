package com.netflix.spinnaker.keel.ec2

import com.netflix.spinnaker.kork.exceptions.IntegrationException

class MissingAppVersionException(
  val resourceId: String
) : IntegrationException("Resource $resourceId is missing version information. Is the appVersion tag missing from the AMI?")
