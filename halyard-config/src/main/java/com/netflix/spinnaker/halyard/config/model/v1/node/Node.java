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
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

  @JsonIgnore
  public String getNameToRoot() {
    String name = getNodeName();
    if (parent == null) {
      return name;
    } else {
      return parent.getNameToRoot() + "." + name;
    }
  }

  /**
   * Checks if the filter matches this node alone.
   *
   * @param filter the filter being checked.
   * @return true iff the filter accepts this node.
   */
  @JsonIgnore
  private boolean matchesLocally(NodeFilter filter) {
    return filter.matches(this);
  }

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
  public List<String> fieldOptions(ConfigProblemSetBuilder problemSetBuilder, String fieldName) {
    if (fieldName == null || fieldName.isEmpty()) {
      throw new IllegalArgumentException("Input fieldName may not be empty");
    }

    log.info("Looking for options for field " + fieldName + " in node " + getNodeName() + " for type " + getClass().getSimpleName());
    String fieldOptions = fieldName + "Options";
    Method optionsMethod = null;

    try {
      optionsMethod = this.getClass().getDeclaredMethod(fieldOptions, ConfigProblemSetBuilder.class);
      optionsMethod.setAccessible(true);

      return (List<String>) optionsMethod.invoke(this, problemSetBuilder);
    } catch (IllegalAccessException | InvocationTargetException e) {
      log.warn("Failed to call " + fieldOptions + "() on " + this.getClass().getSimpleName());

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

  public List<Field> localFiles() {
    Class<?> clazz = getClass();
    List<Field> res = new ArrayList<>();

    while (clazz != null) {
      res.addAll(Arrays.stream(clazz.getDeclaredFields())
          .filter(f -> f.getDeclaredAnnotation(LocalFile.class) != null)
          .collect(Collectors.toList()));
      clazz = clazz.getSuperclass();
    }

    return res;
  }

  public void recursiveConsume(Consumer<Node> consumer) {
    consumer.accept(this);

    NodeIterator children = getChildren();
    Node child = children.getNext();
    while (child != null) {
      child.recursiveConsume(consumer);
      child = children.getNext();
    }
  }

  /**
   * @param clazz the class to check against.
   * @return a NodeMatcher that matches all nodes of given clazz.
   */
  static public NodeMatcher thisNodeAcceptor(Class clazz) {
    return new NodeMatcher() {
      @Override
      public boolean matches(Node n) {
        return clazz.isAssignableFrom(n.getClass());
      }

      @Override
      public String getName() {
        return "Match against [" + clazz.getSimpleName() + ":*]";
      }
    };
  }

  /**
   * @param clazz the class to check against.
   * @param name is the name to match on.
   * @return a NodeMatcher that matches all nodes of given clazz that also have the right name.
   */
  static public NodeMatcher namedNodeAcceptor(Class clazz, String name) {
    return new NodeMatcher() {
      @Override
      public boolean matches(Node n) {
        return clazz.isAssignableFrom(n.getClass()) && n.getNodeName().equals(name);
      }

      @Override
      public String getName() {
        return "Match against [" + clazz.getSimpleName() + ":" + name + "]";
      }
    };
  }

  public String debugName() {
    return "[" + getClass().getSimpleName() + ":" + getNodeName() + "]";
  }
}
