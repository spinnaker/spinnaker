package com.netflix.spinnaker.keel.plugin

import de.danielbechler.diff.node.DiffNode

data class ResourceDiff<T : Any>(
  val current: T?,
  val desired: T,
  val diff: DiffNode
) {
  constructor(current: T?, desired: T) : this(current, desired, DiffNode.newRootNode())
}
