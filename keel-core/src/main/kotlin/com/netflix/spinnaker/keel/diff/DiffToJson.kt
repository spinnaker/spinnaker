package com.netflix.spinnaker.keel.diff

import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.Visit
import de.danielbechler.util.Strings

fun DiffNode.toJson(working: Any?, base: Any?): Map<String, Any?> =
  JsonVisitor(working, base)
    .also { visit(it) }
    .messages

private class JsonVisitor(
  private val working: Any?,
  private val base: Any?
) : DiffNode.Visitor {
  val messages: Map<String, Any?>
    get() = _messages

  private val _messages = mutableMapOf<String, Map<String, Any?>>()

  override fun node(node: DiffNode, visit: Visit) {
    if (!node.isRootNode) {
      val message = mutableMapOf<String, Any?>("state" to node.state.name)
      if (!node.hasChildren()) {
        message["working"] = node.canonicalGet(working).let(Strings::toSingleLineString)
        message["base"] = node.canonicalGet(base).let(Strings::toSingleLineString)
      }
      _messages[node.path.toString()] = message
    }
  }
}
