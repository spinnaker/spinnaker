package com.netflix.spinnaker.keel.bakery.diff

/**
 * A diff between two sets of packages.
 */
data class PackageDiff(
  val added: Map<String, String>,
  val removed: Map<String, String>,
  val changed: Map<String, OldNewPair<String>>
)
