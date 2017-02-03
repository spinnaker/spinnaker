package com.netflix.spinnaker.halyard.config.model.v1.node;

/**
 * Matches a single node, vs. a filter which matches a path of nodes.
 */
abstract public class NodeMatcher {
  abstract public boolean matches(Node n);

  abstract public String getName();
}
