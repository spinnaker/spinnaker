package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.Resource
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Tracks resource health so that we can see how long a resource has been unhealthy for
 */
abstract class UnhealthyRepository() {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  init {
    log.info("Using ${javaClass.simpleName}")
  }

  /**
   * Marks resource unhealthy
   */
  abstract fun markUnhealthy(resource: Resource<*>)

  /**
   * Clears unhealthy marking
   */
  abstract fun markHealthy(resource: Resource<*>)

  /**
   * Returns the duration a resource has been unhealthy for
   */
  abstract fun durationUnhealthy(resource: Resource<*>): Duration
}
