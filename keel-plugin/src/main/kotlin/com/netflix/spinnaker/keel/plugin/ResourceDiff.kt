package com.netflix.spinnaker.keel.plugin

import de.danielbechler.diff.node.DiffNode

data class ResourceDiff<T : Any>(
  val source: T,
  val diff: DiffNode
) {
  constructor(source: T) : this(source, DiffNode.newRootNode())
}
