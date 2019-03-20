package com.netflix.spinnaker.keel.sync

import java.time.Duration

interface Lock {
  fun tryAcquire(name: String, duration: Duration): Boolean
}
