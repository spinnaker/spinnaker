package com.netflix.spinnaker.keel.api

import java.time.Duration

// TODO: it really doesn't make sense to surface this via keel-api
interface UnhappyControl {
  /**
   * [maxDiffCount] allows customization of how many times keel will attempt to
   * actuate a given resource diff before `UnhappyVeto` is engaged.
   */
  val maxDiffCount: Int?
  /**
   * [unhappyWaitTime] allows customization of how often actuation of an unhappy
   * resource can be attempted. An [unhappyWaitTime] of Duration.ZERO is treated as:
   * "Wait forever or until the diff changes, whichever comes first."
   */
  val unhappyWaitTime: Duration?
}
