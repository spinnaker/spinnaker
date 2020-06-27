package com.netflix.spinnaker.keel.artifact

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal fun <T> runWithIoContext(block: suspend () -> T): T {
  return runBlocking {
    withContext(Dispatchers.IO) {
      block()
    }
  }
}
