package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.ResourceVersionTracker
import java.util.concurrent.atomic.AtomicLong

class InMemoryResourceVersionTracker : ResourceVersionTracker {

  private val resourceVersion: AtomicLong = AtomicLong(0L)

  override fun get() = resourceVersion.get()

  override fun set(value: Long) {
    resourceVersion.set(value)
  }
}
