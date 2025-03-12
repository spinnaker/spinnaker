package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.Moniker

/**
 * Common properties used to identify server groups regardless of provider.
 */
interface ServerGroupIdentity {
  val name: String
  val moniker: Moniker
}
