package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.ConfigurationException

class InvalidConstraintException(
  constraintName: String,
  message: String,
  cause: Throwable?
) : ConfigurationException("$constraintName: $message", cause) {
  constructor(constraintName: String, message: String) : this(constraintName, message, null)
}
