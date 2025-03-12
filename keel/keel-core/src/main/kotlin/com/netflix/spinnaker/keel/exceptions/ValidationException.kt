package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.UserException

open class ValidationException(
  override val message: String
) : UserException(message)
