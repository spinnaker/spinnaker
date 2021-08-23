package com.netflix.spinnaker.keel.test

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.core.env.Environment


/**
 * Generate a mock Spring environment that returns the default value for all boolean properties
 */
fun mockEnvironment() : Environment {
  val defaultValue = slot<Boolean>()
  val environment: Environment = mockk()

  every {
    hint(Boolean::class)
    environment.getProperty(any(), Boolean::class.java, capture(defaultValue))
  } answers {
    defaultValue.captured
  }

  return environment
}
