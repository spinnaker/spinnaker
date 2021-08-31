package com.netflix.spinnaker.config

/**
 * Retry config for persistent data stores
 */
class BasePersistenceRetryConfig {
  /**
   * The maximum number of retries in the event of an error
   */
  var maxRetries: Int = 5

  /**
   * The amount of time to wait between retries
   */
  var backoffMs: Long = 100
}
