package com.netflix.spinnaker.keel.exceptions

open class ValidationException(
  val msg: String
) : RuntimeException(msg)
