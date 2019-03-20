package com.netflix.spinnaker.keel.sync

import java.time.Duration

/**
 * Appropriate for use locally or when running single instances. Always grants the lock.
 */
object NoOpLock : Lock {
  override fun tryAcquire(name: String, duration: Duration) = true
}
