package com.netflix.spinnaker.keel.exceptions

import java.lang.RuntimeException

class InvalidConstraintException(
  constraintName: String,
  message: String
) : RuntimeException("$constraintName: $message")
