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

fun DiffNode.toDebug(working: Any?, base: Any?): String =
  DebugVisitor(working, base)
    .also { visit(it) }
    .toString()

private class DebugVisitor(
  private val working: Any?,
  private val base: Any?
) : NodeHierarchyVisitor() {
  private val builder = StringBuilder()

  override fun print(node: DiffNode, level: Int) {
    print("${node.path} ===> ${indent(level, translateState(node.state, base, working))}")
  }

  override fun print(text: String) {
    builder.append(text).append("\n")
  }

  private fun translateState(state: DiffNode.State, base: Any?, modified: Any?): String =
    when (state) {
      IGNORED -> "has been ignored"
      CHANGED -> "has changed from [ ${toSingleLineString(base)} ] to [ ${toSingleLineString(modified)} ]"
      ADDED -> "has been added => [ ${toSingleLineString(modified)} ]"
      REMOVED -> "with value [ ${toSingleLineString(base)} ] has been removed"
      UNTOUCHED -> "has not changed"
      CIRCULAR -> "has already been processed at another position. (Circular reference!)"
      else -> '('.toString() + state.name + ')'.toString()
    }

  override fun toString(): String = builder.toString()
}
