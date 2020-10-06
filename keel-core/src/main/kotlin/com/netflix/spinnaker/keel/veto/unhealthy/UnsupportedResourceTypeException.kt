package com.netflix.spinnaker.keel.veto.unhealthy

import com.netflix.spinnaker.kork.exceptions.SystemException

class UnsupportedResourceTypeException(
  override val message: String
) : SystemException(message)
