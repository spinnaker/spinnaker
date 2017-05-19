package com.netflix.spinnaker.halyard.config.model.v1.node;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class Cluster extends Node implements Cloneable {
  String name;

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  @Override
  public String getNodeName() {
    return name;
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }
}

