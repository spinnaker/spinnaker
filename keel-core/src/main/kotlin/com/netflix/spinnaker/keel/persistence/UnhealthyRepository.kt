package com.netflix.spinnaker.keel.persistence

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
  abstract fun markUnhealthy(resourceId: String)

  /**
   * Clears unhealthy marking
   */
  abstract fun markHealthy(resourceId: String)

  /**
   * Returns the duration a resource has been unhealthy for
   */
  abstract fun durationUnhealthy(resourceId: String): Duration
}

