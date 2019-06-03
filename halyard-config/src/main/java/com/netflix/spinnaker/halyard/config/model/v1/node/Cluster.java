package com.netflix.spinnaker.halyard.config.model.v1.node;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class Cluster extends Node implements Cloneable {
  String name;

  @Override
  public String getNodeName() {
    return name;
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }
}
