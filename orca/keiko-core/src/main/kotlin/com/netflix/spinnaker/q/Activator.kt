package com.netflix.spinnaker.q

/**
 * Strategy that enables the [Queue] to be enabled and disabled.
 */
interface Activator {

  val enabled: Boolean

  /**
   * Execute [block] if enabled otherwise no-op.
   */
  fun ifEnabled(block: () -> Unit) {
    if (enabled) {
      block.invoke()
    }
  }
}
