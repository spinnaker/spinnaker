package com.netflix.spinnaker.keel.diff

import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.node.Visit
import de.danielbechler.util.Strings

internal class JsonVisitor(
  private val working: Any?,
  private val base: Any?,
  private val workingLabel: String = "working",
  private val baseLabel: String = "base"
) : DiffNode.Visitor {
  val messages: Map<String, Any?>
    get() = _messages

  private val _messages = mutableMapOf<String, Map<String, Any?>>()

  override fun node(node: DiffNode, visit: Visit) {
    if (!node.isRootNode) {
      val message = mutableMapOf<String, Any?>("state" to node.state.name)
      if (!node.hasChildren()) {
        message[workingLabel] = node.canonicalGet(working).let(Strings::toSingleLineString)
        message[baseLabel] = node.canonicalGet(base).let(Strings::toSingleLineString)
      }
      _messages[node.path.toString()] = message
    }
  }
}
