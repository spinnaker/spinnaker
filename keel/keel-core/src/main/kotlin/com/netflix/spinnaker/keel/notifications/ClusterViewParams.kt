package com.netflix.spinnaker.keel.notifications

/**
 * Params for constructing a cluster view link
 * If you want to search for stuff without a stack or detail,
 * you must pass "(none)" for those
 */
data class ClusterViewParams(
  val acct: String,
  val q: String,
  val stack: String = "(none)",
  val detail: String = "(none)"
) {
  fun toURL(): String {
    return "acct=$acct&q=$q&stack=$stack&detail=$detail"
  }
}
