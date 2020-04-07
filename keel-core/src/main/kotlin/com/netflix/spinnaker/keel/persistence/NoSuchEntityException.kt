package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.kork.exceptions.SystemException

abstract class NoSuchEntityException(override val message: String?) :
  SystemException(message)
