package com.netflix.spinnaker.keel.plugin

import de.danielbechler.diff.node.DiffNode

data class ResourceDiff<T : Any>(
  val desired: T,
  val current: T?,
  val diff: DiffNode
) {
  constructor(current: T?, desired: T) : this(desired, current, DiffNode.newRootNode())
}
