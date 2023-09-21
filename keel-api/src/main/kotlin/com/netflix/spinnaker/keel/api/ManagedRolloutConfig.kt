package com.netflix.spinnaker.keel.api


/**
 * When managed rollout is enabled, we will deploy with a ManagedRollout stage instead of
 *   the normal deploy stage.
 */
data class ManagedRolloutConfig(
  val enabled: Boolean = false,
  val selectionStrategy: SelectionStrategy? = null
)

// duplication of com.netflix.buoy.sdk.model.SelectionStrategy
// so that we don't add another dependency into this module
enum class SelectionStrategy {
  ALPHABETICAL, OFF_PEAK
}
