package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.ConfigurationException

class InvalidConstraintException(
  constraintName: String,
  message: String
) : ConfigurationException("$constraintName: $message")
