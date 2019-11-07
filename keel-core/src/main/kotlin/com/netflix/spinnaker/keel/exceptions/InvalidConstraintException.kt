package com.netflix.spinnaker.keel.exceptions

import java.lang.RuntimeException

class InvalidConstraintException(
  message: String
) : RuntimeException(message)
