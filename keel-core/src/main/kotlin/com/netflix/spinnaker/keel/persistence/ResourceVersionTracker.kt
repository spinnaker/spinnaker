package com.netflix.spinnaker.keel.persistence

interface ResourceVersionTracker {

  fun get(): Long

  fun set(value: Long): Unit
}
