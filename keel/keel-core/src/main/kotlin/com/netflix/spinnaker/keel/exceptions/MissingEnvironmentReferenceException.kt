package com.netflix.spinnaker.keel.exceptions

class MissingEnvironmentReferenceException(
  private val environmentName: String
) : ValidationException(
  "The depend-on environment named: $environmentName " +
    "is missing in the config. Please ensure you reference the right environment."
)
