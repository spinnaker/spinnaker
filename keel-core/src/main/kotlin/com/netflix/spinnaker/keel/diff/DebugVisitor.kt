package com.netflix.spinnaker.keel.diff

import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.DiffNode.State.ADDED
import de.danielbechler.diff.node.DiffNode.State.CHANGED
import de.danielbechler.diff.node.DiffNode.State.CIRCULAR
import de.danielbechler.diff.node.DiffNode.State.IGNORED
import de.danielbechler.diff.node.DiffNode.State.REMOVED
import de.danielbechler.diff.node.DiffNode.State.UNTOUCHED
import de.danielbechler.diff.node.NodeHierarchyVisitor
import de.danielbechler.util.Strings.indent
import de.danielbechler.util.Strings.toSingleLineString

internal class DebugVisitor(
  private val working: Any?,
  private val base: Any?
) : NodeHierarchyVisitor() {
  private val builder = StringBuilder()

  override fun print(node: DiffNode, level: Int) {
    if (!node.isRootNode) {
      if (node.hasChildren()) {
        print(indent(level - 2, "${node.path} : ${node.state.name.toLowerCase()}"))
      } else {
        print(indent(level - 2, "${node.path} : ${translateState(node.state, node.canonicalGet(base), node.canonicalGet(working))}"))
      }
    }
  }

  override fun print(text: String) {
    builder.append(text).append("\n")
  }

  private fun translateState(state: DiffNode.State, base: Any?, modified: Any?): String =
    when (state) {
      IGNORED -> "ignored"
      CHANGED -> "changed from [ ${toSingleLineString(base)} ] to [ ${toSingleLineString(modified)} ]"
      ADDED -> "added [ ${toSingleLineString(modified)} ]"
      REMOVED -> "removed [ ${toSingleLineString(base)} ]"
      UNTOUCHED -> "unchanged"
      CIRCULAR -> "circular reference"
      else -> '('.toString() + state.name + ')'.toString()
    }

  override fun toString(): String = builder.toString()
}
