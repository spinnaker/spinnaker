package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.SystemException

/**
 * Thrown when the system finds itself in invalid state (generally due to a bug).
 */
class InvalidSystemStateException(
  override val message: String? = null,
  override val cause: Throwable? = null
) : SystemException(message, cause)
