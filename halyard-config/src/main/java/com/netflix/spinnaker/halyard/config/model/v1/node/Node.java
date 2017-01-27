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
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The "Node" class represents a YAML node in our config hierarchy that can be validated.
 *
 * The motivation for this is to allow us to navigate YAML paths in our halconfig, and validate each node (if necessary)
 * along the way.
 */
@Slf4j
abstract public class Node implements Validatable {
  @JsonIgnore
  public abstract String getNodeName();

  @JsonIgnore
  public abstract NodeIterator getChildren();

  /**
   * Checks if the filter matches this node alone.
   *
   * @param filter the filter being checked.
   * @return true iff the filter accepts this node.
   */
  @JsonIgnore
  protected abstract boolean matchesLocally(NodeFilter filter);

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
  public abstract NodeFilter getFilter();

  @JsonIgnore
  public List<String> fieldOptions(ProblemSetBuilder problemSetBuilder, String fieldName) {
    if (fieldName == null || fieldName.isEmpty()) {
      throw new IllegalArgumentException("Input fieldName may not be empty");
    }

    log.info("Looking for options for field " + fieldName + " in node " + getNodeName() + " for type " + getClass());
    String fieldOptions = fieldName + "Options";
    Method optionsMethod = null;

    try {
      optionsMethod = this.getClass().getDeclaredMethod(fieldOptions, ProblemSetBuilder.class);
      optionsMethod.setAccessible(true);

      return (List<String>) optionsMethod.invoke(this, problemSetBuilder);
    } catch (IllegalAccessException | InvocationTargetException e) {
      log.warn("Failed to call " + fieldOptions + "() on " + this.getClass());

      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      // It's expected that many fields won't supply options endpoints.
      return new ArrayList<>();
    } finally {
      if (optionsMethod != null) {
        optionsMethod.setAccessible(false);
      }
    }
  }

  @JsonIgnore
  public <T> T parentOfType(Class<? extends T> type) {
    Node parent = this.getParent();
    while (parent != null && !type.isAssignableFrom(parent.getClass())) {
      parent = parent.getParent();
    }

    if (parent == null) {
      return null;
    } else {
      return (T) parent;
    }
  }

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
