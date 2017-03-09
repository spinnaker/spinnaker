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
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.halyard.config.model.v1.node.NodeDiff.ChangeType.ADDED;
import static com.netflix.spinnaker.halyard.config.model.v1.node.NodeDiff.ChangeType.EDITED;
import static com.netflix.spinnaker.halyard.config.model.v1.node.NodeDiff.ChangeType.REMOVED;

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
    return getNameToClass(Halconfig.class);
  }

  @JsonIgnore
  public String getNameToClass(Class<?> clazz) {
    String name = getNodeName();
    if (parent == null || clazz.isAssignableFrom(parent.getClass())) {
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


  private Map<String, Object> serializedNonNodeFields() {
    List<Field> fields = Arrays.stream(this.getClass().getDeclaredFields()).filter(f -> {
      return (!(Node.class.isAssignableFrom(f.getType()) ||
          List.class.isAssignableFrom(f.getType()) ||
          Map.class.isAssignableFrom(f.getType()) ||
          f.getAnnotation(JsonIgnore.class) != null));
      }).collect(Collectors.toList());

    Map<String, Object> res = new HashMap<>();
    for (Field field : fields) {
      field.setAccessible(true);
      try {
        res.put(field.getName(), field.get(this));
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Failed to read field " + field.getName() + " in node " + getNodeName());
      }
      field.setAccessible(false);
    }

    return res;
  }

  private Map<String, Node> serializedNodeFields() {
    Map<String, Node> res = new HashMap<>();

    NodeIterator iterator = this.getChildren();
    Node next = iterator.getNext();

    while (next != null) {
      res.put(next.getNodeName(), next);
      next = iterator.getNext();
    }

    return res;
  }

  public NodeDiff diff(Node other) {
    String tt = this.getClass().getSimpleName();
    String to = other.getClass().getSimpleName();
    if (!tt.equals(to)) {
      throw new RuntimeException("Invalid comparision between unequal node types (" + tt + " != " + to + ")");
    }

    String nnt = this.getNodeName();
    String nno = other.getNodeName();
    if (!nnt.equals(nno)) {
      throw new RuntimeException("Invalid comparision between different nodes (" + nnt + " != " + nno + ")");
    }

    NodeDiff result = new NodeDiff().setChangeType(EDITED).setNode(this);

    Map<String, Object> fts = this.serializedNonNodeFields();
    Map<String, Object> fos = other.serializedNonNodeFields();

    for (Map.Entry<String, Object> entry : fts.entrySet()) {
      String fnt = entry.getKey();
      Object ot = entry.getValue();
      Object oo = fos.get(fnt);

      if (ot == null && oo == null) {
        continue;
      }

      if (ot != null && oo != null && ot.equals(oo)) {
        continue;
      }

      NodeDiff.FieldDiff fc = new NodeDiff.FieldDiff()
          .setFieldName(fnt)
          .setNewValue(ot)
          .setOldValue(oo);

      result.addFieldDiff(fc);
    }


    Map<String, Node> nts = this.serializedNodeFields();
    Map<String, Node> nos = other.serializedNodeFields();

    for (Map.Entry<String, Node> entry : nts.entrySet()) {
      nnt = entry.getKey();
      Node nt = entry.getValue();
      Node no = nos.get(nnt);

      if (no == null) {
        result.addNodeDiff(new NodeDiff().setChangeType(ADDED).setNode(nt));
        continue;
      }

      NodeDiff diff = nt.diff(no);

      if (diff != null) {
        result.addNodeDiff(diff);
      }
    }

    for (Map.Entry<String, Node> entry : nos.entrySet()) {
      nno = entry.getKey();
      Node no = entry.getValue();
      Node nt = nts.get(nno);

      if (nt == null) {
        result.addNodeDiff(new NodeDiff().setChangeType(REMOVED).setNode(no));
      }
    }

    if (result.getFieldDiffs().isEmpty() && result.getNodeDiffs().isEmpty()) {
      return null;
    }
    return result;
  }
}
