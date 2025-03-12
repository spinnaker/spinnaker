package com.netflix.spinnaker.halyard.config.model.v1.node;

/** Matches a single node, vs. a filter which matches a path of nodes. */
public abstract class NodeMatcher {
  public abstract boolean matches(Node n);

  public abstract String getName();
}
