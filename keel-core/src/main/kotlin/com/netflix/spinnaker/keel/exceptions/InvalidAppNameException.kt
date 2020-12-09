package com.netflix.spinnaker.keel.exceptions

class InvalidAppNameException(
  appName: String
) : ValidationException(
  "'application' field in delivery config is not a valid Spinnaker app name: $appName"
)

