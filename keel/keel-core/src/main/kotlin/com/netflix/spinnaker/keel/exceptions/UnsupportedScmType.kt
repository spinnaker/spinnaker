package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.SystemException

class UnsupportedScmType (
  override val message: String? = null,
  override val cause: Throwable? = null
) : SystemException(message, cause)


