package com.netflix.spinnaker.keel.persistence

import java.time.Duration

interface PeriodicallyCheckedRepository<T : Any> {

  /**
   * Returns between zero and [limit] items that have not been checked (i.e. returned by this
   * method) in at least [minTimeSinceLastCheck].
   *
   * This method is _not_ intended to be idempotent, subsequent calls are expected to return
   * different values.
   */
  fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<T>

  /**
   * Optional operation to mark a check as complete. If this is not implemented it's assumed that
   * [itemsDueForCheck] takes care of that.
   */
  fun markCheckComplete(deliveryConfig: T) {}
}
