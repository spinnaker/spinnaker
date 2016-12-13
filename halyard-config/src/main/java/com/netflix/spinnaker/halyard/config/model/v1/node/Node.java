/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.config.spinnaker.v1.ComponentName;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * The "Node" class represents a YAML node in our config hierarchy that can be validated.
 *
 * The motivation for this is to allow us to navigate YAML paths in our halconfig, and validate each node (if necessary)
 * along the way.
 */
abstract public class Node implements Validatable {
  @JsonIgnore
  public abstract String getNodeName();

  @JsonIgnore
  public abstract NodeIterator getChildren();

  @JsonIgnore
  private Set<ComponentName> spinnakerComponents = new HashSet<>();

  protected void registerWithSpinnakerComponent(ComponentName component) {
    if (spinnakerComponents.contains(component)) {
      throw new RuntimeException("Node " + getNodeName() + " cannot be registered with spinnaker component name \"" + component + "\" more than once");
    }

    spinnakerComponents.add(component);
  }

  public Set<ComponentName> registeredSpinnakerComponents() {
    return spinnakerComponents;
  }

  /**
   * Checks if the filter matches this node alone.
   *
   * @param filter the filter being checked.
   * @return true iff the filter accepts this node.
   */
  @JsonIgnore
  abstract boolean matchesLocally(NodeFilter filter);

  /**
   * Checks if the filter matches this node all the way to the root.
   *
   * @param filter the filter being checked.
   * @return true iff the filter accepts this node, as a part of its full context (yaml tree ending at this node).
   */
  @JsonIgnore
  public boolean matchesToRoot(NodeFilter filter) {
    boolean result = matchesLocally(filter);

    if (parent == null || !result) {
      return result;
    }

    return parent.matchesToRoot(filter);
  }

  @JsonIgnore
  public abstract NodeReference getReference();

  @Getter
  @JsonIgnore
  protected Node parent = null;

  @JsonIgnore
  public void parentify() {
    NodeIterator children = getChildren();

    Node child = children.getNext();
    while (child != null) {
      child.parent = this;
      child.parentify();
      child = children.getNext();
    }
  }
}
