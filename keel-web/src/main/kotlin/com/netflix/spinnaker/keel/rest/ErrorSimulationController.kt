package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import com.netflix.spinnaker.kork.exceptions.SystemException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * This controller provides an interface for injecting simulated errors, in order to make it easier to test keel
 * behavior when failures occur.
 */
@RestController
@ConditionalOnProperty("tests.error-simulation", matchIfMissing = false)
@RequestMapping(path = ["/test"])
class ErrorSimulationController {

  /**
   * Throw
   */
  @GetMapping(
    path = ["/error"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun throwAnException(): Map<String, Any> {
    throw SystemException("GET request was made against test endpoint that is configured to always throw an exception")
  }
}
